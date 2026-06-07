package com.ethossoftworks.reaperbleiem.ui.app

import com.ethossoftworks.reaperbleiem.Route
import com.ethossoftworks.reaperbleiem.coordinator.AppCoordinator
import com.outsidesource.oskitkmp.coordinator.Coordinator
import com.outsidesource.oskitkmp.interactor.Interactor

data class AppToolbarViewState(
    val hasBackStack: Boolean = false,
    val isSettingsOptionVisible: Boolean = true,
    val isAboutVisible: Boolean = false,
)

class AppToolbarViewInteractor(val coordinator: AppCoordinator) :
    Interactor<AppToolbarViewState>(
        initialState =
            run {
                val coordinatorObserver = Coordinator.createObserver(coordinator)

                AppToolbarViewState(
                    hasBackStack = coordinatorObserver.hasBackStack(),
                    isSettingsOptionVisible = coordinatorObserver.routeFlow.value.route != Route.Settings,
                )
            },
        dependencies = emptyList(),
    ) {

    fun onBackClick() {
        coordinator.onBackClick()
    }

    fun onSettingsClick() {
        coordinator.onSettingsClick()
    }

    fun onAboutClick() {
        update { state -> state.copy(isAboutVisible = true) }
    }

    fun onAboutDismiss() {
        update { state -> state.copy(isAboutVisible = false) }
    }
}
