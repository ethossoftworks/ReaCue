package com.ethossoftworks.reaperbleiem.ui.iem

import com.ethossoftworks.reaperbleiem.interactor.CapabilityInteractor
import com.ethossoftworks.reaperbleiem.interactor.IemInteractor
import com.ethossoftworks.reaperbleiem.interactor.InfoMessageInteractor
import com.ethossoftworks.reaperbleiem.service.iem.Track
import com.outsidesource.oskitkmp.capability.CapabilityStatus
import com.outsidesource.oskitkmp.interactor.Interactor
import kotlinx.coroutines.launch

data class IemScreenViewState(
    val bluetoothStatus: CapabilityStatus = CapabilityStatus.Unknown,
    val isServiceRunning: Boolean = false,
    val selectedIemId: Int? = null,
    val tracks: Map<Int, Track> = emptyMap(),
    val projectName: String = "Unknown",
)

class IemScreenViewInteractor(
    private val iemInteractor: IemInteractor,
    private val capabilityInteractor: CapabilityInteractor,
    private val infoMessageInteractor: InfoMessageInteractor,
) :
    Interactor<IemScreenViewState>(
        initialState = IemScreenViewState(),
        dependencies = listOf(iemInteractor, capabilityInteractor),
    ) {

    override fun computed(state: IemScreenViewState): IemScreenViewState {
        return state.copy(
            bluetoothStatus = capabilityInteractor.state.bluetoothStatus,
            tracks = iemInteractor.state.tracks,
            projectName = iemInteractor.state.projectName,
        )
    }

    fun onMount() {
        start()
    }

    fun onIemSelect(id: Int) {
        update { state -> state.copy(selectedIemId = id) }
    }

    fun onRestartClick() {
        start()
    }

    fun onRefreshClick() {
        interactorScope.launch {
            iemInteractor.refresh()
        }
    }

    fun onOutputVolumeChange(trackId: Int, value: Float) {
        interactorScope.launch {
            iemInteractor.setOutputVolume(trackId, value)
        }
    }

    fun onReceiveVolumeChange(trackId: Int, receiveId: Int, value: Float) {
        interactorScope.launch {
            iemInteractor.setReceiveVolume(trackId, receiveId, value)
        }
    }

    private fun start() {
        interactorScope.launch {
            update { state -> state.copy(isServiceRunning = true) }
            iemInteractor.subscribe().collect { }
            update { state -> state.copy(isServiceRunning = false) }
        }
    }
}
