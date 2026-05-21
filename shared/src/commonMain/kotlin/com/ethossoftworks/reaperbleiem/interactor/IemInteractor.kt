package com.ethossoftworks.reaperbleiem.interactor

import com.ethossoftworks.reaperbleiem.service.iem.BleCentralIemService
import com.ethossoftworks.reaperbleiem.service.iem.IIemService
import com.ethossoftworks.reaperbleiem.service.iem.IemContext
import com.ethossoftworks.reaperbleiem.service.iem.IemEvent
import com.ethossoftworks.reaperbleiem.service.iem.Mix
import com.ethossoftworks.reaperbleiem.service.iem.Track
import com.outsidesource.oskitkmp.interactor.Interactor
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.io.files.Path

data class IemState(
    val projectName: String = "Unknown",
    val tracks: PersistentMap<Int, Track> = persistentMapOf(),
    val serviceStatus: ServiceStatus = ServiceStatus.Disconnected,
    val isRefreshing: Boolean = false,
)

enum class ServiceStatus {
    Disconnected,
    Connecting,
    Connected,
}

class IemInteractor(private val iemService: IIemService) :
    Interactor<IemState>(dependencies = emptyList(), initialState = IemState()) {

    fun scanPeripherals() = (iemService as? BleCentralIemService)?.scan() ?: emptyFlow()

    fun subscribe(context: IemContext): Flow<IemEvent> {
        update { state -> state.copy(serviceStatus = ServiceStatus.Connecting) }

        return iemService
            .subscribe(context)
            .onEach { event ->
                when (event) {
                    IemEvent.Refresh,
                    IemEvent.Reset -> {
                        // Do Nothing. These are commands sent from the central.
                    }
                    IemEvent.Refreshing ->
                        update { state -> state.copy(tracks = persistentMapOf(), isRefreshing = true) }
                    is IemEvent.Refreshed ->
                        update { state ->
                            state.copy(
                                projectName = Path(path = event.projectName).name.removeSuffix(".RPP"),
                                tracks = event.tracks,
                                serviceStatus = ServiceStatus.Connected,
                                isRefreshing = false,
                            )
                        }
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
                                    state.tracks.mutate { tracks ->
                                        val track = tracks[event.trackId] ?: return@mutate
                                        tracks[event.trackId] = track.copy(name = event.name)
                                    }
                            )
                        }
                    is IemEvent.Error -> update { state -> state.copy(tracks = persistentMapOf()) }
                }
            }
            .onCompletion {
                update { state -> state.copy(tracks = persistentMapOf(), serviceStatus = ServiceStatus.Disconnected) }
            }
    }

    suspend fun setOutputVolume(trackId: Int, value: Float) {
        updateHardwareOutput(trackId) { it.copy(volume = value) }
        iemService.setOutputVolume(trackId, value)
    }

    suspend fun setReceiveVolume(trackId: Int, receiveId: Int, value: Float) {
        updateReceive(trackId, receiveId) { it.copy(volume = value) }
        iemService.setReceiveVolume(trackId, receiveId, value)
    }

    suspend fun refresh() {
        iemService.refresh()
    }

    private inline fun updateReceive(trackId: Int, receiveId: Int, crossinline block: (Mix) -> Mix) {
        update { state ->
            state.copy(
                tracks =
                    state.tracks.mutate { tracks ->
                        val track = tracks[trackId] ?: return@mutate
                        tracks[trackId] =
                            track.copy(
                                receives =
                                    track.receives.mutate { receives ->
                                        val receive = receives[receiveId] ?: return@mutate
                                        receives[receive.id] = block(receive)
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
                    state.tracks.mutate { tracks ->
                        val track = tracks[trackId] ?: return@mutate
                        tracks[trackId] =
                            track.copy(
                                hardwareOuts =
                                    track.hardwareOuts.mutate { hwOuts ->
                                        val hwOut = hwOuts.values.firstOrNull() ?: return@mutate
                                        hwOuts[hwOut.id] = block(hwOut)
                                    }
                            )
                    }
            )
        }
    }
}
