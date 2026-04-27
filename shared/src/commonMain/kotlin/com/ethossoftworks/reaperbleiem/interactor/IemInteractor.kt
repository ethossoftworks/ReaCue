package com.ethossoftworks.reaperbleiem.interactor

import com.ethossoftworks.reaperbleiem.service.iem.Track
import com.outsidesource.oskitkmp.interactor.Interactor

data class IemState(
    val tracks: Map<Int, Track> = emptyMap(),
    val iems: Map<Int, Track> = emptyMap(),
    val receiveData: Map<Int, ReceiveData> = emptyMap(),
)

data class ReceiveData(
    val iemId: Int,
    val receiveId: Int,
    val srcTrackId: Int,
    val volume: Float = 0f,
    val pan: Float = .5f,
    val isMuted: Boolean = false,
)

class IemInteractor : Interactor<IemState>(
    dependencies = emptyList(),
    initialState = IemState(),
) {

    fun start() {

    }
}
