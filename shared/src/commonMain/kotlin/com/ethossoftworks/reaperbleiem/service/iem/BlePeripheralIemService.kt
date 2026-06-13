@file:OptIn(ExperimentalUuidApi::class)

package com.ethossoftworks.reaperbleiem.service.iem

import co.touchlab.kermit.Logger
import com.ethossoftworks.reaperbleiem.lib.bluetooth.IKmpBlePeripheralManager
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleAdvertisementCharacteristic
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleAdvertisementData
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleAdvertisementService
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleCentralId
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleConnectionPriority
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleGattPermission
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleGattProperty
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBlePeripheralEvent
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBlePeripheralGattResult
import com.ethossoftworks.reaperbleiem.service.preferences.PeripheralPreferencesService
import com.outsidesource.oskitkmp.lib.update
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import io.ktor.utils.io.core.toByteArray
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.getAndUpdate
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.mutate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.writeUShort
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor

val REACUE_SERVICE_UUID = Uuid.parseHexDash("fa6e666c-2c23-43f1-84e4-4653ebf930f4")
val REACUE_EVENT_CHARACTERISTIC_UUID = Uuid.parseHexDash("319893ca-5fa2-4c21-9f51-bc2b1116a352")
val REACUE_COMMAND_CHARACTERISTIC_UUID = Uuid.parseHexDash("aa57c9ce-ada3-4779-88bb-efce418a297e")
val REACUE_HANDSHAKE_CHARACTERISTIC_UUID = Uuid.parseHexDash("2575a2df-6aa2-4466-8eeb-7c13bade6734")

const val BLE_PROTOCOL_VERSION = 1

@OptIn(ExperimentalSerializationApi::class)
class BlePeripheralIemService(
    private val networkIemService: NetworkIemService,
    private val peripheralManager: IKmpBlePeripheralManager,
    private val peripheralPreferencesService: PeripheralPreferencesService,
) : IIemService {

    private val crypto = CryptographyProvider.Default
    private val hmac = crypto.get(HMAC)
    private val secureRandom = CryptographyRandom.Default
    private val authorizedCentrals = atomic<Set<KmpBleCentralId>>(emptySet())
    private val authNonces = atomic<Map<KmpBleCentralId, ByteArray>>(emptyMap())

    private var hostName: String = "ReaCue"
    private var hostPasscode: String = ""

    private val lastStructureChangedEvent = atomic<IemEvent.StructureChanged?>(null)
    private val bleNotificationChannel = Channel<IemEvent>(capacity = Channel.UNLIMITED)
    private val notificationId = atomic<UShort>(0u)
    private val cbor = Cbor {
        ignoreUnknownKeys = true
        preferCborLabelsOverNames = true
    }

    private val advertisementData =
        KmpBleAdvertisementData(
            name = hostName,
            services =
                listOf(
                    KmpBleAdvertisementService(
                        uuid = REACUE_SERVICE_UUID,
                        isPrimaryService = true,
                        characteristics =
                            listOf(
                                KmpBleAdvertisementCharacteristic(
                                    uuid = REACUE_EVENT_CHARACTERISTIC_UUID,
                                    properties = setOf(KmpBleGattProperty.Notify),
                                    permissions = setOf(KmpBleGattPermission.Readable),
                                ),
                                KmpBleAdvertisementCharacteristic(
                                    uuid = REACUE_COMMAND_CHARACTERISTIC_UUID,
                                    properties = setOf(KmpBleGattProperty.WriteWithoutResponse),
                                    permissions = setOf(KmpBleGattPermission.Writable),
                                ),
                                KmpBleAdvertisementCharacteristic(
                                    uuid = REACUE_HANDSHAKE_CHARACTERISTIC_UUID,
                                    properties = setOf(KmpBleGattProperty.Write, KmpBleGattProperty.Read),
                                    permissions = setOf(KmpBleGattPermission.Writable, KmpBleGattPermission.Readable),
                                ),
                            ),
                    )
                ),
        )

    override fun subscribe(context: IemContext): Flow<IemEvent> = channelFlow {
        loadUserSettings()
        val isAdvertising = CompletableDeferred<Unit>()

        val bleChannelJob = launch {
            for (notification in bleNotificationChannel) {
                sendBleNotification(notification)

                if (notification is IemEvent.Error) {
                    delay(500.milliseconds)
                    this@channelFlow.cancel()
                }
            }
        }

        val advertiseJob =
            peripheralManager
                .advertise(advertisementData.copy(name = hostName))
                .onEach { event ->
                    when (event) {
                        is KmpBlePeripheralEvent.Error -> cancel()
                        KmpBlePeripheralEvent.Advertising -> isAdvertising.complete(Unit)
                        is KmpBlePeripheralEvent.CentralSubscribed ->
                            peripheralManager.requestConnectionPriority(KmpBleConnectionPriority.High, event.centralId)
                        is KmpBlePeripheralEvent.CentralUnsubscribed ->
                            authorizedCentrals.update { it - event.centralId }
                        is KmpBlePeripheralEvent.ReadRequest ->
                            when (event.characteristic) {
                                REACUE_HANDSHAKE_CHARACTERISTIC_UUID -> onHandshakeReadRequest(event)
                                else -> {}
                            }
                        is KmpBlePeripheralEvent.WriteRequest ->
                            when (event.characteristic) {
                                REACUE_HANDSHAKE_CHARACTERISTIC_UUID -> onHandshakeWriteRequest(event)
                                REACUE_COMMAND_CHARACTERISTIC_UUID -> onCommandWriteRequest(event, ::send)
                            }
                    }
                }
                .launchIn(this)

        isAdvertising.await()
        Logger.i { "Advertising started" }

        val networkJob =
            networkIemService
                .subscribe(context)
                .onEach { event ->
                    Logger.i { "Received event from Reaper - $event" }
                    send(event)
                    bleNotificationChannel.trySend(event)
                }
                .launchIn(this)

        awaitClose {
            authorizedCentrals.update { emptySet() }
            authNonces.update { emptyMap() }

            bleChannelJob.cancel()
            advertiseJob.cancel()
            networkJob.cancel()
        }
    }

    private suspend fun loadUserSettings() {
        val settings = peripheralPreferencesService.awaitSettings()
        hostName = settings.hostName
        hostPasscode = settings.hostPasscode
    }

    private suspend fun onHandshakeReadRequest(event: KmpBlePeripheralEvent.ReadRequest) {
        val nonce = secureRandom.nextBytes(16)
        val hostId = peripheralPreferencesService.settings.value.hostId.toByteArray()
        val payload =
            Buffer()
                .apply {
                    writeInt(BLE_PROTOCOL_VERSION)
                    write(hostId)
                    write(nonce)
                }
                .readByteArray()

        authNonces.update { it.update { this[event.centralId] = nonce } }

        peripheralManager.respondToRequest(
            central = event.centralId,
            requestId = event.requestId,
            result = KmpBlePeripheralGattResult.Success,
            value = payload,
        )
    }

    private suspend fun onHandshakeWriteRequest(event: KmpBlePeripheralEvent.WriteRequest) {
        val nonce = authNonces.value[event.centralId] ?: byteArrayOf()
        val keyBytes = peripheralPreferencesService.settings.value.hostPasscode.toByteArray()
        val hmacKey = hmac.keyDecoder(SHA256).decodeFromByteArray(HMAC.Key.Format.RAW, keyBytes)
        val signatureVerifier = hmacKey.signatureVerifier()
        val isValid = signatureVerifier.tryVerifySignature(nonce, event.data)

        if (isValid) {
            authorizedCentrals.update { it + event.centralId }
            authNonces.update { it - event.centralId }
        }

        peripheralManager.respondToRequest(
            central = event.centralId,
            requestId = event.requestId,
            result =
                if (isValid) {
                    KmpBlePeripheralGattResult.Success
                } else {
                    KmpBlePeripheralGattResult.UserDefined(0x9F.toByte())
                },
        )
    }

    private suspend fun onCommandWriteRequest(
        request: KmpBlePeripheralEvent.WriteRequest,
        send: suspend (IemEvent) -> Unit,
    ) {
        try {
            if (!authorizedCentrals.value.contains(request.centralId)) return

            val event = cbor.decodeFromByteArray(IemEvent.serializer(), request.data)
            Logger.i { "Received command - $event" }

            when (event) {
                IemEvent.Refresh -> {
                    sendBleNotification(lastStructureChangedEvent.value ?: return, setOf(request.centralId))
                    return
                }
                is IemEvent.ReceiveVolumeUpdated ->
                    networkIemService.setReceiveVolume(event.trackId, event.receiveId, event.value)
                is IemEvent.ReceivePanUpdated ->
                    networkIemService.setReceivePan(event.trackId, event.receiveId, event.value)
                is IemEvent.ReceiveMuteUpdated ->
                    networkIemService.setReceiveMute(event.trackId, event.receiveId, event.value)

                is IemEvent.HardwareOutputVolumeUpdated ->
                    networkIemService.setOutputVolume(event.trackId, event.hardwareOutId, event.value)
                is IemEvent.HardwareOutputPanUpdated ->
                    networkIemService.setOutputPan(event.trackId, event.hardwareOutId, event.value)
                is IemEvent.HardwareOutputMuteUpdated ->
                    networkIemService.setOutputMute(event.trackId, event.hardwareOutId, event.value)

                is IemEvent.Error,
                is IemEvent.StructureChanged,
                is IemEvent.TrackNameUpdated,
                is IemEvent.TrackVolumeUpdated,
                is IemEvent.TrackPanUpdated,
                is IemEvent.TrackMuteUpdated,
                is IemEvent.PasscodeRequired -> return
            }

            val centrals = peripheralManager.subscribedCentrals(REACUE_EVENT_CHARACTERISTIC_UUID) - request.centralId
            sendBleNotification(event, centrals)
            send(event)
        } catch (t: Throwable) {
            Logger.e { "Could not decode write request ${t.message}" }
        }
    }

    fun sendDisconnectEvent() {
        sendBleNotification(IemEvent.Error.DisconnectedPeripheral)
    }

    override suspend fun refresh() {
        return networkIemService.refresh()
    }

    override suspend fun setTrackVolume(trackId: Int, value: Float) {
        networkIemService.setTrackVolume(trackId, value)
        sendBleNotification(IemEvent.TrackVolumeUpdated(trackId, value))
    }

    override suspend fun setTrackPan(trackId: Int, value: Float) {
        networkIemService.setTrackPan(trackId, value)
        sendBleNotification(IemEvent.TrackPanUpdated(trackId, value))
    }

    override suspend fun setTrackMute(trackId: Int, value: Boolean) {
        networkIemService.setTrackMute(trackId, value)
        sendBleNotification(IemEvent.TrackMuteUpdated(trackId, value))
    }

    override suspend fun setReceiveVolume(trackId: Int, receiveId: Int, value: Float) {
        networkIemService.setReceiveVolume(trackId, receiveId, value)
        sendBleNotification(IemEvent.ReceiveVolumeUpdated(trackId, receiveId, value))
    }

    override suspend fun setReceivePan(trackId: Int, receiveId: Int, value: Float) {
        networkIemService.setReceivePan(trackId, receiveId, value)
        sendBleNotification(IemEvent.ReceivePanUpdated(trackId, receiveId, value))
    }

    override suspend fun setReceiveMute(trackId: Int, receiveId: Int, value: Boolean) {
        networkIemService.setReceiveMute(trackId, receiveId, value)
        sendBleNotification(IemEvent.ReceiveMuteUpdated(trackId, receiveId, value))
    }

    override suspend fun setOutputVolume(trackId: Int, hardwareOutId: Int, value: Float) {
        networkIemService.setOutputVolume(trackId, hardwareOutId, value)
        sendBleNotification(IemEvent.HardwareOutputVolumeUpdated(trackId, hardwareOutId, value))
    }

    override suspend fun setOutputPan(trackId: Int, hardwareOutId: Int, value: Float) {
        networkIemService.setOutputPan(trackId, hardwareOutId, value)
        sendBleNotification(IemEvent.HardwareOutputPanUpdated(trackId, hardwareOutId, value))
    }

    override suspend fun setOutputMute(trackId: Int, hardwareOutId: Int, value: Boolean) {
        networkIemService.setOutputMute(trackId, hardwareOutId, value)
        sendBleNotification(IemEvent.HardwareOutputMuteUpdated(trackId, hardwareOutId, value))
    }

    private fun sendBleNotification(event: IemEvent, centralList: Set<KmpBleCentralId>? = null) {
        updateLastRefreshedEvent(event)
        val payload = cbor.encodeToByteArray(IemEvent.serializer(), event)
        val centrals = centralList ?: peripheralManager.subscribedCentrals(REACUE_EVENT_CHARACTERISTIC_UUID)
        val requestId = notificationId.getAndUpdate { it.inc() }
        val buffer = Buffer()

        Logger.i { "Sending notification to ${centrals.size} centrals - $event" }

        for (central in centrals) {
            if (!authorizedCentrals.value.contains(central)) continue

            val packetSize = peripheralManager.maximumUpdateValueLengthForCentral(central) - headerSize
            val packetCount = ceil(payload.size.toDouble() / packetSize.toDouble()).toUInt()

            for (packetIndex in 0u until packetCount) {
                buffer.apply {
                    clear()
                    writeUShort(requestId)
                    writeUShort((packetCount - 1u - packetIndex).toUShort())
                    val startIndex = (packetIndex * packetSize).toInt()
                    write(payload, startIndex, minOf(startIndex + packetSize.toInt(), payload.size))
                }

                Logger.i {
                    "Sending notification packet: Packet Size: ${buffer.size}, Request Id - $requestId - ${packetIndex.toInt() + 1}/$packetCount"
                }
                peripheralManager.notify(
                    REACUE_EVENT_CHARACTERISTIC_UUID,
                    buffer.readByteArray(),
                    centrals = listOf(central),
                )
            }
        }
    }

    private fun updateLastRefreshedEvent(event: IemEvent) {
        when (event) {
            is IemEvent.StructureChanged -> lastStructureChangedEvent.update { event }

            is IemEvent.ReceiveVolumeUpdated ->
                updateLastStructureChangeReceive(event.trackId, event.receiveId) {
                    it.copy(volume = event.value)
                }

            is IemEvent.ReceivePanUpdated ->
                updateLastStructureChangeReceive(event.trackId, event.receiveId) {
                    it.copy(pan = event.value)
                }

            is IemEvent.ReceiveMuteUpdated ->
                updateLastStructureChangeReceive(event.trackId, event.receiveId) {
                    it.copy(isMuted = event.value)
                }

            is IemEvent.HardwareOutputVolumeUpdated ->
                updateLastStructureChangeHardwareOut(event.trackId, event.hardwareOutId) {
                    it.copy(volume = event.value)
                }

            is IemEvent.HardwareOutputPanUpdated ->
                updateLastStructureChangeHardwareOut(event.trackId, event.hardwareOutId) {
                    it.copy(pan = event.value)
                }

            is IemEvent.HardwareOutputMuteUpdated ->
                updateLastStructureChangeHardwareOut(event.trackId, event.hardwareOutId) {
                    it.copy(isMuted = event.value)
                }
            is IemEvent.TrackNameUpdated -> updateLastStructureChangeTrack(event.trackId) { it.copy(name = event.name) }
            is IemEvent.TrackVolumeUpdated ->
                updateLastStructureChangeTrack(event.trackId) { it.copy(volume = event.value) }
            is IemEvent.TrackPanUpdated -> updateLastStructureChangeTrack(event.trackId) { it.copy(pan = event.value) }
            is IemEvent.TrackMuteUpdated ->
                updateLastStructureChangeTrack(event.trackId) { it.copy(isMuted = event.value) }

            IemEvent.Refresh,
            is IemEvent.PasscodeRequired,
            is IemEvent.Error -> return
        }
    }

    private inline fun updateLastStructureChangeTrack(trackId: Int, block: (Track) -> Track) {
        lastStructureChangedEvent.update {
            val state = it ?: return@update null
            state.copy(
                tracks =
                    state.tracks.mutate { tracks ->
                        val track = tracks[trackId] ?: return@mutate
                        tracks[trackId] = block(track)
                    }
            )
        }
    }

    private inline fun updateLastStructureChangeReceive(trackId: Int, receiveId: Int, block: (Mix) -> Mix) {
        lastStructureChangedEvent.update {
            val state = it ?: return@update null
            state.copy(
                tracks =
                    state.tracks.mutate { tracks ->
                        val track = tracks[trackId] ?: return@mutate
                        track.receives.mutate { receives ->
                            val receive = receives[receiveId] ?: return@mutate
                            receives[receiveId] = block(receive)
                        }
                    }
            )
        }
    }

    private inline fun updateLastStructureChangeHardwareOut(trackId: Int, hardwareOutId: Int, block: (Mix) -> Mix) {
        lastStructureChangedEvent.update {
            val state = it ?: return@update null
            state.copy(
                tracks =
                    state.tracks.mutate { tracks ->
                        val track = tracks[trackId] ?: return@mutate
                        track.hardwareOuts.mutate { hwOuts ->
                            val hwOut = hwOuts[hardwareOutId] ?: return@mutate
                            hwOuts[hwOut.id] = block(hwOut)
                        }
                    }
            )
        }
    }
}

/** Header Format: Request Id (UInt16), Packets remaining (UInt16) */
val headerSize = 4u
