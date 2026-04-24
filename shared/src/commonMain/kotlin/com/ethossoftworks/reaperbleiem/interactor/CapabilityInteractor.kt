package com.ethossoftworks.reaperbleiem.interactor

import com.outsidesource.oskitkmp.capability.CapabilityStatus
import com.outsidesource.oskitkmp.capability.KmpCapabilities
import com.outsidesource.oskitkmp.interactor.Interactor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class CapabilityInteractor(private val capabilities: KmpCapabilities) :
    Interactor<CapabilityState>(initialState = CapabilityState()) {

    fun observeCapabilityStatus(): Flow<CapabilityState> = callbackFlow {
        launch {
            capabilities.bluetooth.status.collect {
                update { state -> state.copy(bluetoothStatus = it) }
                send(state)
            }
        }
        awaitClose {}
    }

    init {
        interactorScope.launch { fetchCapabilityStatus() }
    }

    suspend fun fetchCapabilityStatus() {
        interactorScope.launch {
            val bluetoothStatus = capabilities.bluetooth.queryStatus()
            update { state -> state.copy(bluetoothStatus = bluetoothStatus) }
        }
    }

    fun tryEnableBluetooth() {
        interactorScope.launch {
            if (capabilities.bluetooth.supportsRequestEnable) {
                capabilities.bluetooth.requestEnable()
            } else {
                capabilities.bluetooth.openAppSettingsScreen()
            }
        }
    }

    suspend fun openAppSettings() {
        capabilities.bluetooth.openAppSettingsScreen()
    }

    suspend fun requestBlePermissions() {
        capabilities.bluetooth.requestPermissions()
    }
}

data class CapabilityState(val bluetoothStatus: CapabilityStatus = CapabilityStatus.NoPermission())
