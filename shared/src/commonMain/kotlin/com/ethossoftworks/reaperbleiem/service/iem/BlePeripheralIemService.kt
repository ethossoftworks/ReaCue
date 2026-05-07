package com.ethossoftworks.reaperbleiem.service.iem

import com.ethossoftworks.reaperbleiem.service.bluetooth.IKmpBlePeripheralManager
import com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleAdvertisementCharacteristic
import com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleAdvertisementData
import com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleAdvertisementService
import com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleGattPermission
import com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleGattProperty
import com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBlePeripheralEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class BlePeripheralIemService(
    private val networkIemService: NetworkIemService,
    private val peripheralManager: IKmpBlePeripheralManager,
) : IIemService {
    val REAPER_BLE_IEM_SERVICE_UUID = "FA6E666C-2C23-43F1-84E4-4653EBF930F4"
    val REAPER_BLE_IEM_EVENT_CHARACTERISTIC_UUID = "319893CA-5FA2-4C21-9F51-BC2B1116A352"
    val REAPER_BLE_IEM_COMMAND_CHARACTERISTIC_UUID = "AA57C9CE-ADA3-4779-88BB-EFCE418A297E"

    val advertisementData =
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
                        is KmpBlePeripheralEvent.CentralUnsubscribed -> onCentralUnsubscribe(event)
                        is KmpBlePeripheralEvent.ReadRequest -> onReadRequest(event)
                        is KmpBlePeripheralEvent.WriteRequest -> onWriteRequest(event)
                    }
                }
                .launchIn(this)

            isAdvertising.await()
            networkIemService.subscribe().collect { event ->
                emit(event)
                for (packet in event.toBlePackets()) {
                    peripheralManager.notify(REAPER_BLE_IEM_EVENT_CHARACTERISTIC_UUID, packet)
                }
            }
        }
    }

    private fun IemEvent.toBlePackets(): List<ByteArray> = buildList {
        when (this) {
            is IemEvent.Error -> byteArrayOf()
            is IemEvent.OutputVolumeUpdated -> byteArrayOf()
            is IemEvent.ReceivePanUpdated -> byteArrayOf()
            is IemEvent.ReceiveRegistered -> byteArrayOf()
            is IemEvent.ReceiveVolumeUpdated -> byteArrayOf()
            is IemEvent.Refreshed -> byteArrayOf()
            IemEvent.Refreshing -> byteArrayOf()
            is IemEvent.TrackNameUpdated -> byteArrayOf()
        }
    }

    private fun onCentralSubscribe(event: KmpBlePeripheralEvent.CentralSubscribed) {}

    private fun onCentralUnsubscribe(event: KmpBlePeripheralEvent.CentralUnsubscribed) {}

    private fun onReadRequest(request: KmpBlePeripheralEvent.ReadRequest) {}

    private fun onWriteRequest(request: KmpBlePeripheralEvent.WriteRequest) {}

    override suspend fun refresh() {
        return networkIemService.refresh()
    }

    override suspend fun setOutputVolume(trackId: Int, value: Float) {
        return networkIemService.setOutputVolume(trackId, value)
    }

    override suspend fun setReceiveVolume(trackId: Int, receiveId: Int, value: Float) {
        return networkIemService.setReceiveVolume(trackId, receiveId, value)
    }

    override suspend fun setReceivePan(trackId: Int, receiveId: Int, value: Float) {
        return networkIemService.setReceivePan(trackId, receiveId, value)
    }

    override suspend fun setReceiveMute(trackId: Int, receiveId: Int, isMuted: Boolean) {
        return networkIemService.setReceiveMute(trackId, receiveId, isMuted)
    }
}
