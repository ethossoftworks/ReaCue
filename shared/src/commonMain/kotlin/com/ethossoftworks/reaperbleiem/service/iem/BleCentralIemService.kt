@file:OptIn(ExperimentalUuidApi::class)

package com.ethossoftworks.reaperbleiem.service.iem

import co.touchlab.kermit.Logger
import com.ethossoftworks.reaperbleiem.lib.bluetooth.IKmpBleCentralManager
import com.ethossoftworks.reaperbleiem.lib.bluetooth.IKmpBleL2CapChannel
import com.ethossoftworks.reaperbleiem.lib.bluetooth.IKmpBlePeripheral
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleConnectionPriority
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleConnectionStatus
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleError
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleWriteMode
import com.ethossoftworks.reaperbleiem.service.preferences.CentralPreferencesService
import com.ethossoftworks.reaperbleiem.service.talkback.AdpcmEncoder
import com.ethossoftworks.reaperbleiem.service.talkback.IMicrophoneCaptureService
import com.outsidesource.oskitkmp.lib.toUShort
import com.outsidesource.oskitkmp.lib.update
import com.outsidesource.oskitkmp.outcome.Outcome
import com.outsidesource.oskitkmp.outcome.unwrapOrReturn
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import io.ktor.utils.io.core.toByteArray
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.readUShort
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor

@OptIn(ExperimentalSerializationApi::class)
class BleCentralIemService(
    private val bleCentralManager: IKmpBleCentralManager,
    private val centralPreferencesService: CentralPreferencesService,
    private val microphoneCaptureService: IMicrophoneCaptureService,
) : IIemService {

    private val peripheral = atomic<IKmpBlePeripheral?>(null)

    // Talkback: PSM discovered in the handshake, the persistent L2CAP channel (opened once per connection), and the
    // mic->channel pump job (started/stopped per push-to-talk press).
    private val talkbackPsm = atomic(0)
    private val talkbackChannel = atomic<IKmpBleL2CapChannel?>(null)
    private val talkbackMicJob = atomic<Job?>(null)
    private val crypto = CryptographyProvider.Default
    private val hmac = crypto.get(HMAC)
    private val requestBuffers = atomic<Map<UShort, Buffer>>(emptyMap())
    private val cbor = Cbor {
        ignoreUnknownKeys = true
        preferCborLabelsOverNames = true
    }

    override fun subscribe(context: IemContext): Flow<IemEvent> = callbackFlow {
        // Connect to and observe peripheral
        val peripheralId = (context as? IemContext.Central ?: return@callbackFlow).peripheral.identifier
        val peripheral =
            withTimeout(10.seconds) {
                bleCentralManager
                    .connect(peripheralId)
                    .unwrapOrReturn { throw RuntimeException("Could not connect") }
                    .apply { this@BleCentralIemService.peripheral.update { this } }
            }

        peripheral.requestMtu(517).unwrapOrReturn {
            return@callbackFlow
        }
        peripheral.discoverServices().unwrapOrReturn {
            return@callbackFlow
        }

        peripheral.requestConnectionPriority(KmpBleConnectionPriority.High)
        peripheral.connectionStatus.onEach { if (it == KmpBleConnectionStatus.Disconnected) cancel() }.launchIn(this)

        // Subscribe to notifications. Subscribe to notifications before auth so that BLE connection is kept open
        // while the user types in the passcode
        val job =
            peripheral
                .notifications(REACUE_EVENT_CHARACTERISTIC_UUID)
                .onEach { notification ->
                    try {
                        val requestId = notification.toUShort(0)
                        val packetsRemaining = notification.toUShort(2)
                        Logger.i { "Received notification - $requestId - packets remaining $packetsRemaining" }

                        val buffer = requestBuffers.value[requestId] ?: Buffer()
                        buffer.write(notification, headerSize.toInt(), notification.size)

                        if (packetsRemaining > 0u.toUShort()) {
                            requestBuffers.update { it.update { this[requestId] = buffer } }
                            return@onEach
                        }

                        requestBuffers.update { it.update { remove(requestId) } }
                        val event = cbor.decodeFromByteArray(IemEvent.serializer(), buffer.readByteArray())

                        send(event)
                        Logger.i { "Received message - $event" }

                        if (event is IemEvent.Error) cancel()
                    } catch (e: Exception) {
                        Logger.e(e) { "Error while assembling packets: ${notification.toHexString()}" }
                    }
                }
                .launchIn(this)

        // Auth Handshake
        handleAuth(peripheral, ::send).unwrapOrReturn {
            cancel()
            return@callbackFlow
        }

        // Wait for collector to be ready to send refresh command
        delay(16.milliseconds)
        refresh()

        awaitClose {
            job.cancel()
            talkbackMicJob.getAndSet(null)?.cancel()
            talkbackChannel.getAndSet(null)?.close()
            launch {
                peripheral.disconnect().unwrapOrReturn {
                    return@launch
                }
            }
        }
    }

    private suspend fun handleAuth(
        peripheral: IKmpBlePeripheral,
        send: suspend (IemEvent) -> Unit,
    ): Outcome<Unit, Any> {
        return try {
            val authChallenge =
                peripheral.read(REACUE_HANDSHAKE_CHARACTERISTIC_UUID).unwrapOrReturn {
                    return it
                }

            val buffer = Buffer().apply { write(authChallenge) }
            val protocol = buffer.readInt()

            if (protocol != BLE_PROTOCOL_VERSION) {
                send(IemEvent.Error.BleProtocolMismatch)
                return Outcome.Error(Unit)
            }

            val hostId = Uuid.fromByteArray(buffer.readByteArray(16)).toHexString()
            val nonce = buffer.readByteArray(16)
            talkbackPsm.update { buffer.readUShort().toInt() }

            val requestPasscode: suspend () -> String = {
                val passcodeDeferred = CompletableDeferred<String>()
                send(IemEvent.PasscodeRequired(passcodeDeferred))
                val passcode = passcodeDeferred.await()
                centralPreferencesService.putPasscode(hostId, passcode)
                passcode
            }

            val verifyPasscode: suspend (passcode: String) -> Outcome<Unit, KmpBleError> = { passcode ->
                val hmacKey = hmac.keyDecoder(SHA256).decodeFromByteArray(HMAC.Key.Format.RAW, passcode.toByteArray())
                val signature = hmacKey.signatureGenerator().generateSignature(nonce)
                peripheral.write(REACUE_HANDSHAKE_CHARACTERISTIC_UUID, signature)
            }

            val passcode = centralPreferencesService.getPasscode(hostId) ?: requestPasscode()
            val result = verifyPasscode(passcode)
            if (result is Outcome.Ok) return result

            while (currentCoroutineContext().isActive) {
                delay(.5.seconds)
                val passcodeRetry = requestPasscode()
                val retryResult = verifyPasscode(passcodeRetry)
                if (retryResult is Outcome.Ok) return retryResult
            }

            Outcome.Ok(Unit)
        } catch (e: Exception) {
            return Outcome.Error(e)
        }
    }

    fun scan() = bleCentralManager.scan().filter { it.services.contains(REACUE_SERVICE_UUID) }

    val isTalkbackChannelOpen: Boolean
        get() = talkbackPsm.value != 0

    /**
     * Starts streaming mic audio to the peripheral. The L2CAP channel is opened lazily on first use (the connection
     * isn't ready for a CoC immediately after connect) and then kept open and reused for subsequent presses.
     * Idempotent while active.
     */
    suspend fun startTalkback() {
        val peripheral = peripheral.value ?: return
        if (talkbackMicJob.value != null) return

        if (talkbackChannel.value == null) {
            val psm = talkbackPsm.value
            if (psm == 0) {
                Logger.w { "Talkback not supported by host (no PSM)" }
                return
            }
            when (val out = peripheral.openL2CapChannel(psm)) {
                is Outcome.Ok -> {
                    talkbackChannel.update { out.value }
                    Logger.i { "Talkback L2CAP channel opened (psm $psm)" }
                }
                is Outcome.Error -> {
                    Logger.e { "Talkback L2CAP channel open failed: ${out.error}" }
                    return
                }
            }
        }
        val channel = talkbackChannel.value ?: return

        // The desired talkback channel travels in-band as the first byte of every L2CAP frame (-1 = let the host
        // auto-allocate a slot; 0..7 = a specific channel). Read once at press time so it's constant for this burst.
        val talkbackChannel = centralPreferencesService.settings.value.talkbackChannel

        val job =
            peripheral.scope.launch {
                Logger.i { "Talkback: starting mic capture on channel $talkbackChannel" }
                // Compress to IMA ADPCM (~4:1) so 16 kHz fits the BLE link. Each frame is [len u16 LE][channel u8]
                // [ADPCM block]; len covers the channel byte + block. A fresh encoder per press resets state.
                val encoder = AdpcmEncoder()
                var frames = 0
                try {
                    microphoneCaptureService.capture().collect { pcm ->
                        frames++
                        val block = encoder.encode(pcm)
                        val payloadLen = 1 + block.size
                        val framed = ByteArray(2 + payloadLen)
                        framed[0] = (payloadLen and 0xFF).toByte()
                        framed[1] = ((payloadLen shr 8) and 0xFF).toByte()
                        framed[2] = talkbackChannel.toByte() // signed; -1 -> 0xFF
                        block.copyInto(framed, destinationOffset = 3)
                        val result = channel.write(framed)
                        if (result is Outcome.Error) {
                            Logger.e { "Talkback: L2CAP write failed at frame $frames: ${result.error}" }
                            return@collect
                        }
                    }
                    Logger.i { "Talkback: mic capture completed after $frames frames" }
                } catch (t: Throwable) {
                    Logger.e(t) { "Talkback: capture error after $frames frames" }
                }
            }
        talkbackMicJob.update { job }
    }

    /** Stops the mic stream. The L2CAP channel stays open for the next press. */
    fun stopTalkback() {
        talkbackMicJob.getAndSet(null)?.cancel()
    }

    override suspend fun refresh() {
        val payload = cbor.encodeToByteArray(IemEvent.serializer(), IemEvent.Refresh)
        peripheral.value?.write(REACUE_COMMAND_CHARACTERISTIC_UUID, payload, KmpBleWriteMode.WithoutResponse)
    }

    override suspend fun setTrackVolume(trackId: Int, value: Float) {
        val payload =
            cbor.encodeToByteArray(IemEvent.serializer(), IemEvent.TrackVolumeUpdated(trackId, value))
        peripheral.value?.write(REACUE_COMMAND_CHARACTERISTIC_UUID, payload, KmpBleWriteMode.WithoutResponse)
    }

    override suspend fun setTrackPan(trackId: Int, value: Float) {
        val payload =
            cbor.encodeToByteArray(IemEvent.serializer(), IemEvent.TrackPanUpdated(trackId, value))
        peripheral.value?.write(REACUE_COMMAND_CHARACTERISTIC_UUID, payload, KmpBleWriteMode.WithoutResponse)
    }

    override suspend fun setTrackMute(trackId: Int, value: Boolean) {
        val payload =
            cbor.encodeToByteArray(IemEvent.serializer(), IemEvent.TrackMuteUpdated(trackId, value))
        peripheral.value?.write(REACUE_COMMAND_CHARACTERISTIC_UUID, payload, KmpBleWriteMode.WithoutResponse)
    }

    override suspend fun setReceiveVolume(trackId: Int, receiveId: Int, value: Float) {
        val payload =
            cbor.encodeToByteArray(IemEvent.serializer(), IemEvent.ReceiveVolumeUpdated(trackId, receiveId, value))
        peripheral.value?.write(REACUE_COMMAND_CHARACTERISTIC_UUID, payload, KmpBleWriteMode.WithoutResponse)
    }

    override suspend fun setReceivePan(trackId: Int, receiveId: Int, value: Float) {
        val payload =
            cbor.encodeToByteArray(IemEvent.serializer(), IemEvent.ReceivePanUpdated(trackId, receiveId, value))
        peripheral.value?.write(REACUE_COMMAND_CHARACTERISTIC_UUID, payload, KmpBleWriteMode.WithoutResponse)
    }

    override suspend fun setReceiveMute(trackId: Int, receiveId: Int, value: Boolean) {
        val payload =
            cbor.encodeToByteArray(IemEvent.serializer(), IemEvent.ReceiveMuteUpdated(trackId, receiveId, value))
        peripheral.value?.write(REACUE_COMMAND_CHARACTERISTIC_UUID, payload, KmpBleWriteMode.WithoutResponse)
    }

    override suspend fun setOutputVolume(trackId: Int, hardwareOutId: Int, value: Float) {
        val payload =
            cbor.encodeToByteArray(
                IemEvent.serializer(),
                IemEvent.HardwareOutputVolumeUpdated(trackId, hardwareOutId, value),
            )
        peripheral.value?.write(REACUE_COMMAND_CHARACTERISTIC_UUID, payload, KmpBleWriteMode.WithoutResponse)
    }

    override suspend fun setOutputPan(trackId: Int, hardwareOutId: Int, value: Float) {
        val payload =
            cbor.encodeToByteArray(
                IemEvent.serializer(),
                IemEvent.HardwareOutputPanUpdated(trackId, hardwareOutId, value),
            )
        peripheral.value?.write(REACUE_COMMAND_CHARACTERISTIC_UUID, payload, KmpBleWriteMode.WithoutResponse)
    }

    override suspend fun setOutputMute(trackId: Int, hardwareOutId: Int, value: Boolean) {
        val payload =
            cbor.encodeToByteArray(
                IemEvent.serializer(),
                IemEvent.HardwareOutputMuteUpdated(trackId, hardwareOutId, value),
            )
        peripheral.value?.write(REACUE_COMMAND_CHARACTERISTIC_UUID, payload, KmpBleWriteMode.WithoutResponse)
    }
}
