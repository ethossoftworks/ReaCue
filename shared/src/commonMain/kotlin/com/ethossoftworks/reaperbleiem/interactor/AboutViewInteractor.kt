package com.ethossoftworks.reaperbleiem.interactor

import com.outsidesource.oskitkmp.interactor.Interactor

data class AboutViewState(val isOssDisclaimerVisible: Boolean = false)

class AboutViewInteractor : Interactor<AboutViewState>(initialState = AboutViewState(), dependencies = listOf()) {

    fun onOssDisclaimerClick() {
        update { state -> state.copy(isOssDisclaimerVisible = true) }
    }

    fun onOssDisclaimerDismiss() {
        update { state -> state.copy(isOssDisclaimerVisible = false) }
    }
}
