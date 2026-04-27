package com.ethossoftworks.reaperbleiem.service.iem

import kotlinx.coroutines.flow.Flow

interface IIemService {
    fun subscribe(): Flow<IemEvent>

    suspend fun refresh()

    suspend fun setOutputVolume(trackId: Int, value: Float) {}

    suspend fun setReceiveVolume(trackId: Int, receiveId: Int, value: Float) {}

    suspend fun setReceivePan(trackId: Int, receiveId: Int, value: Float) {}

    suspend fun setReceiveMute(trackId: Int, receiveId: Int, isMuted: Boolean) {}
}

sealed class IemEvent {
    data class Error(val error: Any) : IemEvent()

    data object Refreshing : IemEvent()

    data class Refreshed(val tracks: List<Track>) : IemEvent()

    data class TrackNameUpdated(val trackId: Int, val name: String) : IemEvent()

    data class ReceiveRegistered(val trackId: Int, val receiveId: Int, val srcTrackId: Int) : IemEvent()

    data class ReceivePanUpdated(val trackId: Int, val receiveId: Int, val value: Float) : IemEvent()

    data class ReceiveVolumeUpdated(val trackId: Int, val receiveId: Int, val value: Float) : IemEvent()

    data class OutputVolumeUpdated(val trackId: Int, val value: Float) : IemEvent()
}

data class Track(val id: Int, val name: String, val sendCount: Int, val receiveCount: Int, val hardwareOutCount: Int) {
    val isIem = hardwareOutCount > 0 && receiveCount > 0
}
