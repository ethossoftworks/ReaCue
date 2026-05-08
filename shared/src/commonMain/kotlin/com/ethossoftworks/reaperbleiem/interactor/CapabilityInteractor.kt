package com.ethossoftworks.reaperbleiem.interactor

import com.outsidesource.oskitkmp.capability.CapabilityStatus
import com.outsidesource.oskitkmp.capability.KmpCapabilities
import com.outsidesource.oskitkmp.interactor.Interactor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class CapabilityInteractor(private val capabilities: KmpCapabilities) :
    Interactor<CapabilityState>(initialState = CapabilityState()) {

    init {
        interactorScope.launch { fetchCapabilityStatus() }
    }

    fun observeCapabilityState(): Flow<CapabilityStatus> =
        capabilities.bluetooth.status.onEach { update { state -> state.copy(bluetoothStatus = it) } }

    suspend fun fetchCapabilityStatus() {
        interactorScope.launch {
            val bluetoothStatus = capabilities.bluetooth.queryStatus()
            update { state -> state.copy(bluetoothStatus = bluetoothStatus) }
        }
    }

    suspend fun requestBlePermissions() {
        capabilities.bluetooth.requestPermissions()
    }
}

data class CapabilityState(val bluetoothStatus: CapabilityStatus = CapabilityStatus.NoPermission())
