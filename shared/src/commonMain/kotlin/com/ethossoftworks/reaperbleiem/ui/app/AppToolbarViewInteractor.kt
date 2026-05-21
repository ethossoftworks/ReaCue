package com.ethossoftworks.reaperbleiem.ui.app

import com.ethossoftworks.reaperbleiem.coordinator.AppCoordinator
import com.outsidesource.oskitkmp.coordinator.Coordinator
import com.outsidesource.oskitkmp.interactor.Interactor

data class AppToolbarViewState(val hasBackStack: Boolean = false)

class AppToolbarViewInteractor(val coordinator: AppCoordinator) :
    Interactor<AppToolbarViewState>(
        initialState = AppToolbarViewState(hasBackStack = Coordinator.createObserver(coordinator).hasBackStack()),
        dependencies = emptyList(),
    ) {

    fun onBackClick() {
        coordinator.onBackClick()
    }

    fun onSettingsClick() {
        coordinator.onSettingsClick()
    }

    fun onAboutClick() {

    }
}
