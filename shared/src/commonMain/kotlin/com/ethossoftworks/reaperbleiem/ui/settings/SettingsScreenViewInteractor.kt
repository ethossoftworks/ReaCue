package com.ethossoftworks.reaperbleiem.ui.settings

import com.ethossoftworks.reaperbleiem.interactor.InfoMessageInteractor
import com.ethossoftworks.reaperbleiem.interactor.InfoMessageType
import com.ethossoftworks.reaperbleiem.service.preferences.AppSettings
import com.ethossoftworks.reaperbleiem.service.preferences.PreferencesService
import com.outsidesource.oskitkmp.interactor.Interactor
import com.outsidesource.oskitkmp.outcome.runOnError
import com.outsidesource.oskitkmp.outcome.unwrapOrReturn
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import reaper_ble_iem.shared.generated.resources.Res
import reaper_ble_iem.shared.generated.resources.settings_error
import reaper_ble_iem.shared.generated.resources.settings_saved

data class SettingsState(
    val originalAppSettings: AppSettings = AppSettings(),
    val hostId: String = "",
    val hostPasscode: String = "",
    val reaperWebPort: String = "",
    val reaperOscDevicePort: String = "",
    val reaperOscListenPort: String = "",
    val isSaving: Boolean = false,
    val isDefaultModalVisible: Boolean = false,
)

private val intRegexReplace = Regex("""[^0-9]""")

class SettingsScreenViewInteractor(
    private val preferencesService: PreferencesService,
    private val infoMessageInteractor: InfoMessageInteractor,
) : Interactor<SettingsState>(initialState = SettingsState()) {

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
        interactorScope.launch {
            update { state -> state.copy(isSaving = true) }
            var hasError = false

            if (state.hostId != "") {
                preferencesService.setHostId(state.hostId).runOnError { hasError = true }
            }

            if (state.hostPasscode != "") {
                preferencesService.setHostPasscode(state.hostPasscode).runOnError { hasError = true }
            }

            val sanitizedReaperWebPort = state.reaperWebPort.toIntOrNull()
            if (sanitizedReaperWebPort != null) {
                preferencesService.setReaperWebPort(sanitizedReaperWebPort).runOnError { hasError = true }
            }

            val sanitizedReaperOscDevicePort = state.reaperOscDevicePort.toIntOrNull()
            if (sanitizedReaperOscDevicePort != null) {
                preferencesService.setReaperOscDevicePort(sanitizedReaperOscDevicePort).runOnError { hasError = true }
            }

            val sanitizedReaperOscListenPort = state.reaperOscListenPort.toIntOrNull()
            if (sanitizedReaperOscListenPort != null) {
                preferencesService.setReaperOscListenPort(sanitizedReaperOscListenPort).runOnError { hasError = true }
            }

            update { state -> state.copy(isSaving = false) }

            infoMessageInteractor.enqueueMessage(
                message = if (hasError) getString(Res.string.settings_error) else getString(Res.string.settings_saved),
                type = if (hasError) InfoMessageType.Error else InfoMessageType.Info,
            )
        }
    }

    fun onResetToDefaultClick() {
        update { state -> state.copy(isDefaultModalVisible = true) }
    }

    fun onResetToDefaultCancel() {
        update { state -> state.copy(isDefaultModalVisible = false) }
    }

    fun onResetToDefaultConfirmClick() {
        update { state -> state.copy(isDefaultModalVisible = false) }

        interactorScope.launch {
            preferencesService.resetToDefaults().unwrapOrReturn {
                infoMessageInteractor.enqueueMessage(
                    message = getString(Res.string.settings_error),
                    type = InfoMessageType.Error,
                )
                return@launch
            }

            update { state ->
                state.copy(
                    hostId = "",
                    hostPasscode = "",
                    reaperWebPort = "",
                    reaperOscDevicePort = "",
                    reaperOscListenPort = "",
                )
            }

            infoMessageInteractor.enqueueMessage(message = getString(Res.string.settings_saved))
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
