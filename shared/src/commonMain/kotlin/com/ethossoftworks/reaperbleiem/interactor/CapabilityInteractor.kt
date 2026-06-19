package com.ethossoftworks.reaperbleiem.interactor

import com.outsidesource.oskitkmp.capability.CapabilityStatus
import com.outsidesource.oskitkmp.capability.KmpCapabilities
import com.outsidesource.oskitkmp.interactor.Interactor
import com.outsidesource.oskitkmp.outcome.unwrapOrReturn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class CapabilityState(
    val bluetoothStatus: CapabilityStatus = CapabilityStatus.NoPermission(),
)

class CapabilityInteractor(private val capabilities: KmpCapabilities) :
    Interactor<CapabilityState>(initialState = CapabilityState()) {

    init {
        interactorScope.launch { fetchCapabilityStatus() }
    }

    fun observeBluetoothCapabilityState(): Flow<CapabilityStatus> =
        capabilities.bluetooth.status.onEach { update { state -> state.copy(bluetoothStatus = it) } }

    suspend fun fetchCapabilityStatus() {
        val bluetoothStatus = capabilities.bluetooth.queryStatus()
        update { state -> state.copy(bluetoothStatus = bluetoothStatus) }
    }

    suspend fun requestBlePermissions() {
        capabilities.bluetooth.requestPermissions()
    }

    suspend fun queryMicrophonePermissions() = capabilities.microphone.queryStatus()

    suspend fun requestMicrophonePermission() {
        capabilities.microphone.requestPermissions().unwrapOrReturn { return }
    }
}
