package com.ethossoftworks.reaperbleiem.service.iem

import com.ethossoftworks.reaperbleiem.lib.bluetooth.IKmpBleCentralManager
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBlePeripheralId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter

class BleCentralIemService(private val bleService: IKmpBleCentralManager) : IIemService {

    fun scan() = bleService.scan()
        .filter { it.serviceUuids.contains(REAPER_BLE_IEM_SERVICE_UUID.lowercase()) }

    suspend fun connect(id: KmpBlePeripheralId) = bleService.connect(id)

    override fun subscribe(): Flow<IemEvent> {
        TODO("Not yet implemented")
    }

    override suspend fun refresh() {
        TODO("Not yet implemented")
    }

    override suspend fun setOutputVolume(trackId: Int, value: Float) {
        TODO("Not yet implemented")
    }

    override suspend fun setReceiveVolume(trackId: Int, receiveId: Int, value: Float) {
        TODO("Not yet implemented")
    }

    override suspend fun setReceivePan(trackId: Int, receiveId: Int, value: Float) {
        TODO("Not yet implemented")
    }

    override suspend fun setReceiveMute(trackId: Int, receiveId: Int, isMuted: Boolean) {
        TODO("Not yet implemented")
    }
}
