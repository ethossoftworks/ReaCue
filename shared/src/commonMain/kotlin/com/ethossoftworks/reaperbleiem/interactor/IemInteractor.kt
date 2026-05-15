package com.ethossoftworks.reaperbleiem.interactor

import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBlePeripheralId
import com.ethossoftworks.reaperbleiem.service.iem.BleCentralIemService
import com.ethossoftworks.reaperbleiem.service.iem.IIemService
import com.ethossoftworks.reaperbleiem.service.iem.IemEvent
import com.ethossoftworks.reaperbleiem.service.iem.Mix
import com.ethossoftworks.reaperbleiem.service.iem.Track
import com.outsidesource.oskitkmp.interactor.Interactor
import com.outsidesource.oskitkmp.lib.update
import com.outsidesource.oskitkmp.outcome.Outcome
import com.outsidesource.oskitkmp.outcome.unwrapOrReturn
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

data class IemState(val tracks: Map<Int, Track> = emptyMap())

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
                    IemEvent.Refresh,
                    IemEvent.Reset -> {
                        // Do Nothing. These are commands sent from the central.
                    }
                    IemEvent.Refreshing -> update { state -> state.copy(tracks = emptyMap()) }
                    is IemEvent.Refreshed -> update { state -> state.copy(tracks = event.tracks) }
                    is IemEvent.OutputVolumeUpdated ->
                        updateHardwareOutput(event.trackId) { it.copy(volume = event.value) }
                    is IemEvent.ReceivePanUpdated ->
                        updateReceive(event.trackId, event.receiveId) { it.copy(pan = event.value) }
                    is IemEvent.ReceiveVolumeUpdated ->
                        updateReceive(event.trackId, event.receiveId) { it.copy(volume = event.value) }
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
                    is IemEvent.Error -> update { state -> state.copy(tracks = emptyMap()) }
                }
            }
            .onCompletion { update { state -> state.copy(tracks = emptyMap()) } }

    suspend fun setOutputVolume(trackId: Int, value: Float) {
        iemService.setOutputVolume(trackId, value)
        updateHardwareOutput(trackId) { it.copy(volume = value) }
    }

    suspend fun setReceiveVolume(trackId: Int, receiveId: Int, value: Float) {
        iemService.setReceiveVolume(trackId, receiveId, value)
        updateReceive(trackId, receiveId) { it.copy(volume = value) }
    }

    suspend fun refresh() {
        iemService.refresh()
    }

    private inline fun updateReceive(trackId: Int, receiveId: Int, crossinline block: (Mix) -> Mix) {
        update { state ->
            state.copy(
                tracks =
                    state.tracks.update {
                        val track = this[trackId] ?: return@update
                        this[trackId] =
                            track.copy(
                                receives =
                                    track.receives.update {
                                        val receive = this[receiveId] ?: return@update
                                        this[receive.id] = block(receive)
                                    }
                            )
                    }
            )
        }
    }

    private inline fun updateHardwareOutput(trackId: Int, crossinline block: (Mix) -> Mix) {
        update { state ->
            state.copy(
                tracks =
                    state.tracks.update {
                        val track = this[trackId] ?: return@update
                        this[trackId] =
                            track.copy(
                                hardwareOuts =
                                    track.hardwareOuts.update {
                                        val hwOut = values.firstOrNull() ?: return@update
                                        this[hwOut.id] = block(hwOut)
                                    }
                            )
                    }
            )
        }
    }
}
