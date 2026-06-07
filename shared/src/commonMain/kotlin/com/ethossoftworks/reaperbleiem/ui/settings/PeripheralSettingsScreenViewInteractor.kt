@file:OptIn(ExperimentalUuidApi::class)

package com.ethossoftworks.reaperbleiem.ui.settings

import com.ethossoftworks.reaperbleiem.interactor.InfoMessageInteractor
import com.ethossoftworks.reaperbleiem.interactor.InfoMessageType
import com.ethossoftworks.reaperbleiem.service.preferences.PeripheralSettings
import com.ethossoftworks.reaperbleiem.service.preferences.PeripheralPreferencesService
import com.outsidesource.oskitkmp.interactor.Interactor
import com.outsidesource.oskitkmp.outcome.runOnError
import com.outsidesource.oskitkmp.outcome.unwrapOrReturn
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import reacue.shared.generated.resources.Res
import reacue.shared.generated.resources.settings_error
import reacue.shared.generated.resources.settings_saved
import kotlin.uuid.ExperimentalUuidApi

data class PeripheralSettingsState(
    val originalPeripheralSettings: PeripheralSettings = PeripheralSettings(),
    val hostId: String = "",
    val hostPasscode: String = "",
    val reaperWebPort: String = "",
    val reaperOscDevicePort: String = "",
    val reaperOscListenPort: String = "",
    val isSaving: Boolean = false,
    val isDefaultModalVisible: Boolean = false,
)

private val intRegexReplace = Regex("""[^0-9]""")

class PeripheralSettingsScreenViewInteractor(
    private val peripheralPreferencesService: PeripheralPreferencesService,
    private val infoMessageInteractor: InfoMessageInteractor,
) : Interactor<PeripheralSettingsState>(initialState = PeripheralSettingsState()) {

    fun onMount() {
        peripheralPreferencesService.settings
            .onEach { preferences ->
                update { state -> state.copy(originalPeripheralSettings = preferences) }
            }
            .launchIn(interactorScope)
    }

    fun onUnmount() {
        interactorScope.coroutineContext.cancelChildren()
    }

    fun onApplyClick() {
        interactorScope.launch {
            update { state -> state.copy(isSaving = true) }
            var hasError = false

            if (state.hostId != "") {
                peripheralPreferencesService.setHostName(state.hostId).runOnError { hasError = true }
            }

            if (state.hostPasscode != "") {
                peripheralPreferencesService.setHostPasscode(state.hostPasscode).runOnError { hasError = true }
            }

            val sanitizedReaperWebPort = state.reaperWebPort.toIntOrNull()
            if (sanitizedReaperWebPort != null) {
                peripheralPreferencesService.setReaperWebPort(sanitizedReaperWebPort).runOnError { hasError = true }
            }

            val sanitizedReaperOscDevicePort = state.reaperOscDevicePort.toIntOrNull()
            if (sanitizedReaperOscDevicePort != null) {
                peripheralPreferencesService.setReaperOscDevicePort(sanitizedReaperOscDevicePort).runOnError { hasError = true }
            }

            val sanitizedReaperOscListenPort = state.reaperOscListenPort.toIntOrNull()
            if (sanitizedReaperOscListenPort != null) {
                peripheralPreferencesService.setReaperOscListenPort(sanitizedReaperOscListenPort).runOnError { hasError = true }
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
            peripheralPreferencesService.resetToDefaults().unwrapOrReturn {
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
