package com.ethossoftworks.reacue.ui.settings

import com.ethossoftworks.reacue.interactor.InfoMessageInteractor
import com.ethossoftworks.reacue.interactor.InfoMessageType
import com.ethossoftworks.reacue.service.preferences.CentralPreferencesService
import com.ethossoftworks.reacue.service.preferences.CentralSettings
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

data class CentralSettingsState(
    val originalSettings: CentralSettings = CentralSettings(),
    val showTalkBack: Boolean = true,
    val talkBackChannel: Int = -1,
    val isSaving: Boolean = false,
    val isResetToDefaultModalVisible: Boolean = false,
)

class CentralSettingsScreenViewInteractor(
    private val preferencesService: CentralPreferencesService,
    private val infoMessageInteractor: InfoMessageInteractor,
) : Interactor<CentralSettingsState>(initialState = CentralSettingsState()) {

    fun onMount() {
        preferencesService.settings
            .onEach { preferences ->
                update { state ->
                    state.copy(
                        originalSettings = preferences,
                        talkBackChannel = preferences.talkbackChannel,
                        showTalkBack = preferences.showTalkBack,
                    )
                }
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

            if (state.showTalkBack != state.originalSettings.showTalkBack) {
                preferencesService.setShowTalkback(state.showTalkBack).runOnError { hasError = true }
            }

            if (state.talkBackChannel != state.originalSettings.talkbackChannel) {
                preferencesService.setTalkbackChannel(state.talkBackChannel).runOnError { hasError = true }
            }

            update { state -> state.copy(isSaving = false) }

            infoMessageInteractor.enqueueMessage(
                message = if (hasError) getString(Res.string.settings_error) else getString(Res.string.settings_saved),
                type = if (hasError) InfoMessageType.Error else InfoMessageType.Info,
            )
        }
    }

    fun onResetToDefaultClick() {
        update { state -> state.copy(isResetToDefaultModalVisible = true) }
    }

    fun onResetToDefaultCancel() {
        update { state -> state.copy(isResetToDefaultModalVisible = false) }
    }

    fun onResetToDefaultConfirmClick() {
        update { state -> state.copy(isResetToDefaultModalVisible = false) }

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
                    showTalkBack = true,
                    talkBackChannel = -1,
                )
            }

            infoMessageInteractor.enqueueMessage(message = getString(Res.string.settings_saved))
        }
    }

    fun onShowTalkbackChange(value: Boolean) {
        update { state -> state.copy(showTalkBack = value) }
    }

    fun onTalkbackChannelChanged(value: Int) {
        update { state -> state.copy(talkBackChannel = value) }
    }
}
