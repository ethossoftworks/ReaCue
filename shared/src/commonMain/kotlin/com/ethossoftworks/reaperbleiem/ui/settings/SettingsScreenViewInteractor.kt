package com.ethossoftworks.reaperbleiem.ui.settings

import com.ethossoftworks.reaperbleiem.service.preferences.AppSettings
import com.ethossoftworks.reaperbleiem.service.preferences.PreferencesService
import com.outsidesource.oskitkmp.interactor.Interactor
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class SettingsState(
    val originalAppSettings: AppSettings = AppSettings(),
    val hostId: String = "",
    val hostPasscode: String = "",
    val reaperWebPort: String = "",
    val reaperOscDevicePort: String = "",
    val reaperOscListenPort: String = "",
)

val intRegexReplace = Regex("""^[0-9]""")

class SettingsScreenViewInteractor(private val preferencesService: PreferencesService) :
    Interactor<SettingsState>(initialState = SettingsState()) {

    fun onMount() {
        preferencesService.settings
            .onEach { preferences ->
                update { state -> state.copy(originalAppSettings = preferences) }
            }
            .launchIn(interactorScope)
    }

    fun onUnmount() {
        interactorScope.coroutineContext.cancelChildren()
    }

    fun onSaveClick() {
        // TODO: handle errors
        interactorScope.launch {
            if (state.hostId != state.originalAppSettings.hostId) {
                preferencesService.setHostId(state.hostId)
            }
            if (state.hostPasscode != "") {
                preferencesService.setHostPasscode(state.hostPasscode)
            }

            val sanitizedReaperWebPort = state.reaperWebPort.toIntOrNull()
            if (sanitizedReaperWebPort != null) {
                preferencesService.setReaperWebPort(sanitizedReaperWebPort)
            }

            val sanitizedReaperOscDevicePort = state.reaperOscDevicePort.toIntOrNull()
            if (sanitizedReaperOscDevicePort != null) {
                preferencesService.setReaperOscDevicePort(sanitizedReaperOscDevicePort)
            }

            val sanitizedReaperOscListenPort = state.reaperOscListenPort.toIntOrNull()
            if (sanitizedReaperOscListenPort != null) {
                preferencesService.setReaperOscDevicePort(sanitizedReaperOscListenPort)
            }
        }
    }

    fun onHostIdChange(value: String) {
        update { state -> state.copy(hostId = value.take(8)) }
    }

    fun onHostPasscodeChange(value: String) {
        update { state ->
            state.copy(hostPasscode = value.take(64))
        }
    }

    fun onReaperWebPortChange(value: String) {
        update { state ->
            state.copy(reaperWebPort = value.replace(intRegexReplace, "").take(8))
        }
    }

    fun onReaperOscDevicePortChange(value: String) {
        update { state ->
            state.copy(reaperOscDevicePort = value.replace(intRegexReplace, "").take(8))
        }
    }

    fun onReaperOscListenerPortChange(value: String) {
        update { state ->
            state.copy(reaperOscListenPort = value.replace(intRegexReplace, "").take(8))
        }
    }
}
