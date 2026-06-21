package com.ethossoftworks.reacue.interactor

import com.ethossoftworks.reacue.service.iem.BleCentralIemService
import com.ethossoftworks.reacue.service.iem.BlePeripheralIemService
import com.ethossoftworks.reacue.service.iem.FaderInfo
import com.ethossoftworks.reacue.service.iem.IIemService
import com.ethossoftworks.reacue.service.iem.IemContext
import com.ethossoftworks.reacue.service.iem.IemEvent
import com.ethossoftworks.reacue.service.iem.Mix
import com.ethossoftworks.reacue.service.iem.Track
import com.outsidesource.oskitkmp.interactor.Interactor
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.io.files.Path

data class IemState(
    val projectName: String = "Unknown",
    val faderInfo: FaderInfo = FaderInfo(),
    val tracks: PersistentMap<Int, Track> = persistentMapOf(),
    val serviceStatus: ServiceStatus = ServiceStatus.Disconnected,
    val isRefreshing: Boolean = false,
)

enum class ServiceStatus {
    Disconnected,
    Connecting,
    Connected,
}

private val THROTTLE_INTERVAL = 20.milliseconds

class IemInteractor(private val iemService: IIemService) :
    Interactor<IemState>(dependencies = emptyList(), initialState = IemState()) {

    private val throttles = atomic(persistentMapOf<ThrottleKey, Channel<Float>>())
    private val throttleScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun scanPeripherals() = (iemService as? BleCentralIemService)?.scan() ?: emptyFlow()

    fun subscribe(context: IemContext): Flow<IemEvent> {
        update { state -> state.copy(serviceStatus = ServiceStatus.Connecting) }

        return iemService
            .subscribe(context)
            .onEach { event ->
                when (event) {
                    is IemEvent.PasscodeRequired,
                    IemEvent.Refresh -> {}

                    is IemEvent.StructureChanged -> {
                        throttleScope.coroutineContext.cancelChildren()
                        throttles.update { it.mutate { it.clear() } }

                        update { state ->
                            state.copy(
                                projectName = Path(path = event.projectName).name.removeSuffix(".RPP"),
                                tracks = event.tracks,
                                faderInfo = event.faderInfo,
                                serviceStatus = ServiceStatus.Connected,
                                isRefreshing = false,
                            )
                        }
                    }

                    is IemEvent.TrackNameUpdated -> updateTrack(event.trackId) { it.copy(name = event.name) }
                    is IemEvent.TrackVolumeUpdated -> updateTrack(event.trackId) { it.copy(volume = event.value) }
                    is IemEvent.TrackPanUpdated -> updateTrack(event.trackId) { it.copy(pan = event.value) }
                    is IemEvent.TrackMuteUpdated -> updateTrack(event.trackId) { it.copy(isMuted = event.value) }

                    is IemEvent.ReceiveVolumeUpdated ->
                        updateReceive(event.trackId, event.receiveId) { it.copy(volume = event.value) }
                    is IemEvent.ReceivePanUpdated ->
                        updateReceive(event.trackId, event.receiveId) { it.copy(pan = event.value) }
                    is IemEvent.ReceiveMuteUpdated ->
                        updateReceive(event.trackId, event.receiveId) { it.copy(isMuted = event.value) }

                    is IemEvent.HardwareOutputVolumeUpdated ->
                        updateHardwareOutput(event.trackId, event.hardwareOutId) { it.copy(volume = event.value) }
                    is IemEvent.HardwareOutputPanUpdated ->
                        updateHardwareOutput(event.trackId, event.hardwareOutId) { it.copy(pan = event.value) }
                    is IemEvent.HardwareOutputMuteUpdated ->
                        updateHardwareOutput(event.trackId, event.hardwareOutId) { it.copy(isMuted = event.value) }

                    is IemEvent.Error -> update { state -> state.copy(tracks = persistentMapOf()) }
                }
            }
            .onCompletion {
                update { state -> state.copy(tracks = persistentMapOf(), serviceStatus = ServiceStatus.Disconnected) }
            }
    }

    fun sendDisconnectEvent() {
        if (iemService is BlePeripheralIemService) iemService.sendDisconnectEvent()
    }

    val isTalkbackChannelOpen: Boolean
        get() = (iemService as? BleCentralIemService)?.isTalkbackChannelOpen ?: false

    fun startTalkback() {
        interactorScope.launch { (iemService as? BleCentralIemService)?.startTalkback() }
    }

    fun stopTalkback() {
        (iemService as? BleCentralIemService)?.stopTalkback()
    }

    fun setOutputVolume(trackId: Int, hardwareOutId: Int, value: Float) {
        updateHardwareOutput(trackId, hardwareOutId) { it.copy(volume = value) }
        getThrottledChannel(ThrottleKey.OutputVolume(trackId, hardwareOutId)).trySend(value)
    }

    fun setReceiveVolume(trackId: Int, receiveId: Int, value: Float) {
        updateReceive(trackId, receiveId) { it.copy(volume = value) }
        getThrottledChannel(ThrottleKey.ReceiveVolume(trackId, receiveId)).trySend(value)
    }

    fun setReceiveMute(trackId: Int, receiveId: Int, value: Boolean) {
        interactorScope.launch {
            updateReceive(trackId, receiveId) { it.copy(isMuted = value) }
            iemService.setReceiveMute(trackId, receiveId, value)
        }
    }

    fun setReceivePan(trackId: Int, receiveId: Int, value: Float) {
        updateReceive(trackId, receiveId) { it.copy(pan = value) }
        getThrottledChannel(ThrottleKey.ReceivePan(trackId, receiveId)).trySend(value)
    }

    suspend fun refresh() {
        iemService.refresh()
    }

    private inline fun updateTrack(trackId: Int, crossinline block: (Track) -> Track) {
        update { state ->
            state.copy(
                tracks =
                    state.tracks.mutate { tracks ->
                        val track = tracks[trackId] ?: return@mutate
                        tracks[trackId] = block(track)
                    }
            )
        }
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

    private inline fun updateHardwareOutput(trackId: Int, hardwareOutId: Int, crossinline block: (Mix) -> Mix) {
        update { state ->
            state.copy(
                tracks =
                    state.tracks.mutate { tracks ->
                        val track = tracks[trackId] ?: return@mutate
                        tracks[trackId] =
                            track.copy(
                                hardwareOuts =
                                    track.hardwareOuts.mutate { hwOuts ->
                                        hwOuts[hardwareOutId] = block(hwOuts[hardwareOutId] ?: return@mutate)
                                    }
                            )
                    }
            )
        }
    }

    private sealed class ThrottleKey {
        data class TrackVolume(val trackId: Int) : ThrottleKey()

        data class TrackPan(val trackId: Int) : ThrottleKey()

        data class OutputVolume(val trackId: Int, val hardwareOutId: Int) : ThrottleKey()

        data class OutputPan(val trackId: Int, val hardwareOutId: Int) : ThrottleKey()

        data class ReceiveVolume(val trackId: Int, val receiveId: Int) : ThrottleKey()

        data class ReceivePan(val trackId: Int, val receiveId: Int) : ThrottleKey()
    }

    private fun getThrottledChannel(key: ThrottleKey): Channel<Float> {
        throttles.value[key]?.let {
            return it
        }

        val throttle = Channel<Float>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        throttles.update { it.mutate { it[key] = throttle } }

        throttleScope.launch {
            for (value in throttle) {
                when (key) {
                    is ThrottleKey.TrackVolume -> iemService.setTrackVolume(key.trackId, value)
                    is ThrottleKey.TrackPan -> iemService.setTrackPan(key.trackId, value)
                    is ThrottleKey.OutputVolume -> iemService.setOutputVolume(key.trackId, key.hardwareOutId, value)
                    is ThrottleKey.OutputPan -> iemService.setOutputVolume(key.trackId, key.hardwareOutId, value)
                    is ThrottleKey.ReceiveVolume -> iemService.setReceiveVolume(key.trackId, key.receiveId, value)
                    is ThrottleKey.ReceivePan -> iemService.setReceivePan(key.trackId, key.receiveId, value)
                }
                delay(THROTTLE_INTERVAL)
            }
        }

        return throttle
    }
}
