@file:OptIn(ExperimentalUuidApi::class)

package com.ethossoftworks.reaperbleiem.ui.settings

import com.ethossoftworks.reaperbleiem.interactor.InfoMessageInteractor
import com.ethossoftworks.reaperbleiem.interactor.InfoMessageType
import com.ethossoftworks.reaperbleiem.service.preferences.PeripheralPreferencesService
import com.ethossoftworks.reaperbleiem.service.preferences.PeripheralSettings
import com.outsidesource.oskitkmp.interactor.Interactor
import com.outsidesource.oskitkmp.outcome.runOnError
import com.outsidesource.oskitkmp.outcome.unwrapOrReturn
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import reacue.shared.generated.resources.Res
import reacue.shared.generated.resources.settings_error
import reacue.shared.generated.resources.settings_saved

data class PeripheralSettingsState(
    val originalPeripheralSettings: PeripheralSettings = PeripheralSettings(),
    val hostId: String = "",
    val hostPasscode: String = "",
    val reacueReaScriptPort: String = "",
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
            .onEach { preferences -> update { state -> state.copy(originalPeripheralSettings = preferences) } }
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

            val sanitizedReacuePort = state.reacueReaScriptPort.toIntOrNull()
            if (sanitizedReacuePort != null) {
                peripheralPreferencesService.setReaCueReaScriptPort(sanitizedReacuePort).runOnError { hasError = true }
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
                    reacueReaScriptPort = "",
                )
            }

            infoMessageInteractor.enqueueMessage(message = getString(Res.string.settings_saved))
        }
    }

    fun onHostIdChange(value: String) {
        update { state -> state.copy(hostId = value.take(8)) }
    }

    fun onHostPasscodeChange(value: String) {
        update { state -> state.copy(hostPasscode = value.take(64)) }
    }

    fun onReaCueReaScriptPortChange(value: String) {
        update { state -> state.copy(reacueReaScriptPort = value.replace(intRegexReplace, "").take(8)) }
    }
}
