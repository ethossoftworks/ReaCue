@file:OptIn(ExperimentalUuidApi::class)

package com.ethossoftworks.reaperbleiem.service.iem

import co.touchlab.kermit.Logger
import com.ethossoftworks.reaperbleiem.lib.bluetooth.IKmpBleCentralManager
import com.ethossoftworks.reaperbleiem.lib.bluetooth.IKmpBlePeripheral
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleConnectionPriority
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleConnectionStatus
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleWriteMode
import com.ethossoftworks.reaperbleiem.service.preferences.CentralPreferencesService
import com.outsidesource.oskitkmp.lib.toUShort
import com.outsidesource.oskitkmp.lib.update
import com.outsidesource.oskitkmp.outcome.unwrapOrReturn
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import io.ktor.utils.io.core.toByteArray
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor

@OptIn(ExperimentalSerializationApi::class)
class BleCentralIemService(
    private val bleCentralManager: IKmpBleCentralManager,
    private val centralPreferencesService: CentralPreferencesService,
) : IIemService {

    private val peripheral = atomic<IKmpBlePeripheral?>(null)
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

        peripheral.connectionStatus.onEach { if (it == KmpBleConnectionStatus.Disconnected) cancel() }.launchIn(this)

        peripheral.requestConnectionPriority(KmpBleConnectionPriority.High)

        // Auth Handshake
        val nonce = peripheral.read(REACUE_HANDSHAKE_CHARACTERISTIC_UUID).unwrapOrReturn {
            cancel()
            return@callbackFlow
        }

        val hmacKey = hmac.keyDecoder(SHA256).decodeFromByteArray(HMAC.Key.Format.RAW, "QQeUVpXz".toByteArray())
        val signature = hmacKey.signatureGenerator().generateSignature(nonce)

        peripheral.write(REACUE_HANDSHAKE_CHARACTERISTIC_UUID, signature).unwrapOrReturn {
            cancel()
            return@callbackFlow
        }

        // Subscribe to notifications
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

        // Wait for collector to be ready to send refresh command
        delay(16.milliseconds)
        refresh()

        awaitClose {
            job.cancel()
            launch {
                peripheral.disconnect().unwrapOrReturn {
                    return@launch
                }
            }
        }
    }

    fun scan() = bleCentralManager.scan().filter { it.services.contains(REACUE_SERVICE_UUID) }

    override suspend fun refresh() {
        val payload = cbor.encodeToByteArray(IemEvent.serializer(), IemEvent.Refresh)
        peripheral.value?.write(REACUE_COMMAND_CHARACTERISTIC_UUID, payload, KmpBleWriteMode.WithoutResponse)
    }

    override suspend fun setOutputVolume(trackId: Int, value: Float) {
        val payload = cbor.encodeToByteArray(IemEvent.serializer(), IemEvent.OutputVolumeUpdated(trackId, value))
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
}
