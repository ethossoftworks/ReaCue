package com.ethossoftworks.reaperbleiem.service.iem

import com.ethossoftworks.reaperbleiem.lib.bluetooth.IKmpBlePeripheralManager
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleAdvertisementCharacteristic
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleAdvertisementData
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleAdvertisementService
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleCentralId
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleGattPermission
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleGattProperty
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBlePeripheralEvent
import kotlin.math.ceil
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.getAndUpdate
import kotlinx.atomicfu.update
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.writeUShort
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray

val REAPER_BLE_IEM_SERVICE_UUID = "fa6e666c-2c23-43f1-84e4-4653ebf930f4"
val REAPER_BLE_IEM_EVENT_CHARACTERISTIC_UUID = "319893ca-5fa2-4c21-9f51-bc2b1116a352"
val REAPER_BLE_IEM_COMMAND_CHARACTERISTIC_UUID = "aa57C9ce-ada3-4779-88bb-efce418a297e"

@OptIn(ExperimentalSerializationApi::class)
class BlePeripheralIemService(
    private val networkIemService: NetworkIemService,
    private val peripheralManager: IKmpBlePeripheralManager,
) : IIemService {

    private val lastRefreshedEvent = atomic<IemEvent.Refreshed?>(null)
    private val notificationId = atomic<UShort>(0u)
    private val cbor = Cbor {
        ignoreUnknownKeys = true
        preferCborLabelsOverNames = true
    }

    private val advertisementData =
        KmpBleAdvertisementData(
            name = "",
            services =
                listOf(
                    KmpBleAdvertisementService(
                        uuid = REAPER_BLE_IEM_SERVICE_UUID,
                        isPrimaryService = true,
                        characteristics =
                            listOf(
                                KmpBleAdvertisementCharacteristic(
                                    uuid = REAPER_BLE_IEM_EVENT_CHARACTERISTIC_UUID,
                                    properties = setOf(KmpBleGattProperty.Notify),
                                    permissions = setOf(KmpBleGattPermission.Readable),
                                ),
                                KmpBleAdvertisementCharacteristic(
                                    uuid = REAPER_BLE_IEM_COMMAND_CHARACTERISTIC_UUID,
                                    properties = setOf(KmpBleGattProperty.WriteWithoutResponse),
                                    permissions = setOf(KmpBleGattPermission.Writable),
                                ),
                            ),
                    )
                ),
        )

    override fun subscribe(): Flow<IemEvent> = flow {
        coroutineScope {
            val isAdvertising = CompletableDeferred<Unit>()

            peripheralManager
                .advertise(advertisementData)
                .onEach { event ->
                    when (event) {
                        is KmpBlePeripheralEvent.Error -> cancel()
                        KmpBlePeripheralEvent.Advertising -> isAdvertising.complete(Unit)
                        is KmpBlePeripheralEvent.CentralSubscribed -> onCentralSubscribe(event)
                        is KmpBlePeripheralEvent.CentralUnsubscribed -> {}
                        is KmpBlePeripheralEvent.ReadRequest -> {}
                        is KmpBlePeripheralEvent.WriteRequest -> onWriteRequest(event)
                    }
                }
                .launchIn(this)

            isAdvertising.await()
            networkIemService.subscribe().collect { event ->
                emit(event)
                if (event is IemEvent.Refreshed) lastRefreshedEvent.update { event }
                sendBleNotification(event)
            }
        }
    }

    // TODO: Ideally we would only refresh the device that connected
    private suspend fun onCentralSubscribe(event: KmpBlePeripheralEvent.CentralSubscribed) {
        networkIemService.refresh()
    }

    private fun onWriteRequest(request: KmpBlePeripheralEvent.WriteRequest) {}

    override suspend fun refresh() {
        return networkIemService.refresh()
    }

    override suspend fun setOutputVolume(trackId: Int, value: Float) {
        networkIemService.setOutputVolume(trackId, value)
        sendBleNotification(IemEvent.OutputVolumeUpdated(trackId, value))
    }

    override suspend fun setReceiveVolume(trackId: Int, receiveId: Int, value: Float) {
        networkIemService.setReceiveVolume(trackId, receiveId, value)
        sendBleNotification(IemEvent.ReceiveVolumeUpdated(trackId, receiveId, value))
    }

    override suspend fun setReceivePan(trackId: Int, receiveId: Int, value: Float) {
        networkIemService.setReceivePan(trackId, receiveId, value)
        sendBleNotification(IemEvent.ReceivePanUpdated(trackId, receiveId, value))
    }

    override suspend fun setReceiveMute(trackId: Int, receiveId: Int, isMuted: Boolean) {
        networkIemService.setReceiveMute(trackId, receiveId, isMuted)
        // TODO: Notify receive mute
    }

    private suspend fun sendBleNotification(event: IemEvent, centralList: Set<KmpBleCentralId>? = null) {
        val payload = cbor.encodeToByteArray(IemEvent.serializer(), event)
        val centrals = centralList ?: peripheralManager.subscribedCentrals(REAPER_BLE_IEM_EVENT_CHARACTERISTIC_UUID)
        val requestId = notificationId.getAndUpdate { it.inc() }

        for (central in centrals) {
            val packetSize = peripheralManager.maximumUpdateValueLengthForCentral(central) - headerSize
            val packetCount = ceil(payload.size.toDouble() / packetSize.toDouble()).toUInt()

            for (packetId in 0u until packetCount) {
                val buffer =
                    Buffer().apply {
                        writeUShort(requestId)
                        writeUShort(packetId.toUShort())
                        writeUShort(packetCount.toUShort())
                        val startIndex = (packetId * packetSize).toInt()
                        write(payload, startIndex, minOf(startIndex + packetSize.toInt(), payload.size))
                    }

                peripheralManager.notify(REAPER_BLE_IEM_EVENT_CHARACTERISTIC_UUID, buffer.readByteArray())
            }
        }
    }
}

/** Header Format: Request Id (UInt16), Packet Id (Uint16), Packet Count (UInt16) */
val headerSize = 6u
