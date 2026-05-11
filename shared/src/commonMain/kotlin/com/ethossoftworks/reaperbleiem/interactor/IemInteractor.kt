package com.ethossoftworks.reaperbleiem.interactor

import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBlePeripheralId
import com.ethossoftworks.reaperbleiem.service.iem.BleCentralIemService
import com.ethossoftworks.reaperbleiem.service.iem.IIemService
import com.ethossoftworks.reaperbleiem.service.iem.IemEvent
import com.ethossoftworks.reaperbleiem.service.iem.Track
import com.outsidesource.oskitkmp.interactor.Interactor
import com.outsidesource.oskitkmp.lib.update
import com.outsidesource.oskitkmp.outcome.Outcome
import com.outsidesource.oskitkmp.outcome.unwrapOrReturn
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

data class IemState(val tracks: Map<Int, Track> = emptyMap(), val iems: Map<Int, IemMix> = emptyMap())

data class IemMix(val trackId: Int, val volume: Float, val receives: Map<Int, ReceiveData>)

data class ReceiveData(
    val trackId: Int, // The IEM track ID
    val receiveId: Int, // The receive ID for the IEM track
    val srcTrackId: Int, // The track ID the receive is receiving from
    val volume: Float = 0f,
    val pan: Float = .5f,
    val isMuted: Boolean = false,
)

class IemInteractor(private val iemService: IIemService) :
    Interactor<IemState>(dependencies = emptyList(), initialState = IemState()) {

    fun scanPeripherals() = (iemService as? BleCentralIemService)?.scan() ?: emptyFlow()

    suspend fun connectPeripheral(peripheralId: KmpBlePeripheralId): Outcome<Unit, Any> {
        val service = (iemService as? BleCentralIemService) ?: return Outcome.Error("Not supported")
        service.connect(peripheralId).unwrapOrReturn {
            return it
        }
        return Outcome.Ok(Unit)
    }

    suspend fun disconnectPeripheral() {
        val service = (iemService as? BleCentralIemService) ?: return
        service.disconnect()
    }

    fun subscribe() =
        iemService
            .subscribe()
            .onEach { event ->
                when (event) {
                    IemEvent.Refresh -> {
                        // Do Nothing. This is a command sent from the central.
                    }
                    IemEvent.Refreshing -> update { state -> state.copy(tracks = emptyMap(), iems = emptyMap()) }
                    is IemEvent.Refreshed ->
                        update { state ->
                            state.copy(
                                tracks = event.tracks.associateBy { it.id },
                                iems =
                                    buildMap {
                                        for (track in event.tracks) {
                                            if (!track.isIem) continue
                                            this[track.id] =
                                                IemMix(trackId = track.id, receives = emptyMap(), volume = 0f)
                                        }
                                    },
                            )
                        }
                    is IemEvent.OutputVolumeUpdated ->
                        update { state ->
                            state.copy(
                                iems =
                                    state.iems.update {
                                        val mix = this[event.trackId] ?: return@update
                                        this[event.trackId] = mix.copy(volume = event.value)
                                    }
                            )
                        }
                    is IemEvent.ReceivePanUpdated ->
                        updateReceive(event.trackId, event.receiveId) { it?.copy(pan = event.value) }
                    is IemEvent.ReceiveRegistered ->
                        updateReceive(event.trackId, event.receiveId) {
                            ReceiveData(
                                trackId = event.trackId,
                                receiveId = event.receiveId,
                                srcTrackId = event.srcTrackId,
                            )
                        }
                    is IemEvent.ReceiveVolumeUpdated ->
                        updateReceive(event.trackId, event.receiveId) { it?.copy(volume = event.value) }
                    is IemEvent.TrackNameUpdated ->
                        update { state ->
                            state.copy(
                                tracks =
                                    state.tracks.update {
                                        val track = this[event.trackId] ?: return@update
                                        this[event.trackId] = track.copy(name = event.name)
                                    }
                            )
                        }
                    is IemEvent.Error -> update { state -> state.copy(tracks = emptyMap(), iems = emptyMap()) }
                }
            }
            .onCompletion { update { state -> state.copy(tracks = emptyMap(), iems = emptyMap()) } }

    suspend fun setOutputVolume(trackId: Int, value: Float) {
        iemService.setOutputVolume(trackId, value)
        update { state ->
            state.copy(
                iems =
                    state.iems.update {
                        val mix = this[trackId] ?: return@update
                        this[trackId] = mix.copy(volume = value)
                    }
            )
        }
    }

    suspend fun setReceiveVolume(trackId: Int, receiveId: Int, value: Float) {
        iemService.setReceiveVolume(trackId, receiveId, value)
        updateReceive(trackId, receiveId) { it?.copy(volume = value) }
    }

    suspend fun refresh() {
        iemService.refresh()
    }

    private inline fun updateReceive(trackId: Int, receiveId: Int, crossinline block: (ReceiveData?) -> ReceiveData?) {
        update { state ->
            state.copy(
                iems =
                    state.iems.update {
                        val track = this[trackId] ?: return@update
                        this[trackId] =
                            track.copy(
                                receives =
                                    track.receives.update {
                                        val updated = block(this[receiveId]) ?: return@update
                                        this[receiveId] = updated
                                    }
                            )
                    }
            )
        }
    }
}
