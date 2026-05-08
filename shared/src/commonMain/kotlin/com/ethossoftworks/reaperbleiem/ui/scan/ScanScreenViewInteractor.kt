package com.ethossoftworks.reaperbleiem.ui.scan

import com.ethossoftworks.reaperbleiem.interactor.CapabilityInteractor
import com.ethossoftworks.reaperbleiem.interactor.IemInteractor
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleScanRecord
import com.outsidesource.oskitkmp.capability.CapabilityStatus
import com.outsidesource.oskitkmp.interactor.Interactor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch

data class ScanScreenViewState(
    val bluetoothStatus: CapabilityStatus = CapabilityStatus.Unknown,
    val devices: List<KmpBleScanRecord> = emptyList(),
)

class ScanScreenViewInteractor(
    private val iemInteractor: IemInteractor,
    private val capabilityInteractor: CapabilityInteractor,
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

        observeJob = capabilityInteractor.observeCapabilityState().launchIn(interactorScope)
    }

    fun onDispose() {
        observeJob?.cancel()
        observeJob = null
    }

    fun onScan() {
        interactorScope.launch { iemInteractor.scan().collect { println(it) } }
    }

    fun onRequestBlePermissionClick() {
        interactorScope.launch { capabilityInteractor.requestBlePermissions() }
    }
}
