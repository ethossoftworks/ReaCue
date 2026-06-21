package com.ethossoftworks.reacue.interactor

import androidx.compose.ui.platform.UriHandler
import com.outsidesource.oskitkmp.interactor.Interactor
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import reacue.shared.generated.resources.Res
import reacue.shared.generated.resources.project_url

data class AboutViewState(val isOssDisclaimerVisible: Boolean = false)

class AboutViewInteractor : Interactor<AboutViewState>(initialState = AboutViewState(), dependencies = listOf()) {

    fun onOssDisclaimerClick() {
        update { state -> state.copy(isOssDisclaimerVisible = true) }
    }

    fun onOssDisclaimerDismiss() {
        update { state -> state.copy(isOssDisclaimerVisible = false) }
    }

    fun onProjectPageClick(uriHandler: UriHandler) {
        interactorScope.launch {
            uriHandler.openUri(getString(Res.string.project_url))
        }
    }
}
