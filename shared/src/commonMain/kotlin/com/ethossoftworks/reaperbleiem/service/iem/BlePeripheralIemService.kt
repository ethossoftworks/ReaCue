package com.ethossoftworks.reaperbleiem.service.iem

import kotlinx.coroutines.flow.Flow

class BlePeripheralIemService(
    private val networkIemService: NetworkIemService,
//    private val peripheralManager: KmpBlePeripheralManager
) : IIemService {
    override fun subscribe(): Flow<IemEvent> {
        return networkIemService.subscribe()
    }

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
