package com.ethossoftworks.reaperbleiem.ui.iem

import com.ethossoftworks.reaperbleiem.coordinator.AppCoordinator
import com.ethossoftworks.reaperbleiem.interactor.CapabilityInteractor
import com.ethossoftworks.reaperbleiem.interactor.IemInteractor
import com.ethossoftworks.reaperbleiem.interactor.InfoMessageInteractor
import com.ethossoftworks.reaperbleiem.interactor.ServiceStatus
import com.ethossoftworks.reaperbleiem.service.iem.IemContext
import com.ethossoftworks.reaperbleiem.service.iem.Track
import com.outsidesource.oskitkmp.capability.CapabilityStatus
import com.outsidesource.oskitkmp.interactor.Interactor
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class IemScreenViewState(
    val bluetoothStatus: CapabilityStatus = CapabilityStatus.Unknown,
    val selectedIemId: Int? = null,
    val tracks: Map<Int, Track> = emptyMap(),
    val projectName: String = "Unknown",
    val serviceStatus: ServiceStatus = ServiceStatus.Disconnected,
    val isRefreshing: Boolean = false,
    val numberInputModalType: NumberInputModalType? = null,
)

enum class NumberInputModalType {
    Adjust,
    Set,
}

class IemScreenViewInteractor(
    private val iemContext: IemContext,
    private val iemInteractor: IemInteractor,
    private val capabilityInteractor: CapabilityInteractor,
    private val coordinator: AppCoordinator,
) :
    Interactor<IemScreenViewState>(
        initialState = IemScreenViewState(),
        dependencies = listOf(iemInteractor, capabilityInteractor),
    ) {

    private val subscriptionJob = atomic<Job?>(null)

    override fun computed(state: IemScreenViewState): IemScreenViewState {
        return state.copy(
            bluetoothStatus = capabilityInteractor.state.bluetoothStatus,
            tracks = iemInteractor.state.tracks,
            projectName = iemInteractor.state.projectName,
            serviceStatus = iemInteractor.state.serviceStatus,
            isRefreshing = iemInteractor.state.isRefreshing,
        )
    }

    fun onMount() {
        interactorScope.launch { start() }
    }

    fun onUnmount() {
        subscriptionJob.value?.cancel()
    }

    fun onIemSelect(id: Int) {
        update { state -> state.copy(selectedIemId = id) }
    }

    fun onConnectClick() {
        start()
    }

    fun onBackToScanClick() {
        coordinator.onBackToScanClick()
    }

    fun onRefreshClick() {
        interactorScope.launch { iemInteractor.refresh() }
    }

    fun onSetAllTo0Click() {
        interactorScope.launch {
            val track = state.tracks[state.selectedIemId] ?: return@launch
            for ((_, receive) in track.receives) {
                iemInteractor.setReceiveVolume(track.id, receive.id, .716f)
            }
        }
    }

    fun onSetAllToNClick() {
        update { state -> state.copy(numberInputModalType = NumberInputModalType.Set) }
    }

    fun onAdjustAllByNClick() {
        update { state -> state.copy(numberInputModalType = NumberInputModalType.Adjust) }
    }

    fun onNumberModalCommit(value: Float) {
        val modalType = state.numberInputModalType ?: return

        update { state -> state.copy(numberInputModalType = null) }

        interactorScope.launch {
            val track = state.tracks[state.selectedIemId] ?: return@launch
            for ((_, receive) in track.receives) {
                val clampedValue = when (modalType) {
                    NumberInputModalType.Adjust -> (receive.volume + (value / 100f)).coerceIn(0f..1f)
                    NumberInputModalType.Set -> (value / 100f).coerceIn(0f, 1f)
                }
                iemInteractor.setReceiveVolume(track.id, receive.id, clampedValue)
            }
        }
    }

    fun onNumberModalDismiss() {
        update { state -> state.copy(numberInputModalType = null) }
    }

    fun onOutputVolumeChange(trackId: Int, value: Float) {
        interactorScope.launch { iemInteractor.setOutputVolume(trackId, value) }
    }

    fun onReceiveVolumeChange(trackId: Int, receiveId: Int, value: Float) {
        interactorScope.launch { iemInteractor.setReceiveVolume(trackId, receiveId, value) }
    }

    private fun start() {
        subscriptionJob.value?.cancel()
        interactorScope.launch { iemInteractor.subscribe(iemContext).collect {} }.let { subscriptionJob.value = it }
    }
}
