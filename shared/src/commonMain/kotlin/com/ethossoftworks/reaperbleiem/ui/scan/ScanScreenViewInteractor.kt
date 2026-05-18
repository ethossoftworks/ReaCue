package com.ethossoftworks.reaperbleiem.ui.scan

import com.ethossoftworks.reaperbleiem.coordinator.AppCoordinator
import com.ethossoftworks.reaperbleiem.interactor.CapabilityInteractor
import com.ethossoftworks.reaperbleiem.interactor.IemInteractor
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleScanRecord
import com.outsidesource.oskitkmp.capability.CapabilityStatus
import com.outsidesource.oskitkmp.interactor.Interactor
import com.outsidesource.oskitkmp.lib.update
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class ScanScreenViewState(
    val bluetoothStatus: CapabilityStatus = CapabilityStatus.Unknown,
    val devices: Map<String, KmpBleScanRecord> = emptyMap(),
)

class ScanScreenViewInteractor(
    private val iemInteractor: IemInteractor,
    private val capabilityInteractor: CapabilityInteractor,
    private val appCoordinator: AppCoordinator,
) : Interactor<ScanScreenViewState>(initialState = ScanScreenViewState(), dependencies = listOf(capabilityInteractor)) {

    var observeJob: Job? = null

    override fun computed(state: ScanScreenViewState): ScanScreenViewState {
        return state.copy(bluetoothStatus = capabilityInteractor.state.bluetoothStatus)
    }

    fun onMount() {
        if (capabilityInteractor.state.bluetoothStatus == CapabilityStatus.Ready) {
            onScan()
            return
        }

        observeJob =
            capabilityInteractor
                .observeCapabilityState()
                .onEach { if (it == CapabilityStatus.Ready) onScan() }
                .launchIn(interactorScope)
    }

    fun onDispose() {
        observeJob?.cancel()
        observeJob = null
    }

    fun onScan() {
        interactorScope.launch {
            withTimeout(10.seconds) {
                iemInteractor.scanPeripherals().collect {
                    update { state -> state.copy(devices = state.devices.update { this[it.identifier] = it }) }
                }
            }
        }
    }

    fun onRequestBlePermissionClick() {
        interactorScope.launch { capabilityInteractor.requestBlePermissions() }
    }

    fun onDeviceClick(scanRecord: KmpBleScanRecord) {
        appCoordinator.onBleScanRecordClick(scanRecord)
    }
}
