package com.ethossoftworks.reaperbleiem.service.iem

import com.ethossoftworks.reaperbleiem.lib.bluetooth.IKmpBle
import kotlinx.coroutines.flow.Flow

class BleCentralIemService(
    private val bleService: IKmpBle
) : IIemService {
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
