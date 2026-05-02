package com.ethossoftworks.reaperbleiem.ui.app

import androidx.compose.runtime.Composable
import com.ethossoftworks.reaperbleiem.Route
import com.ethossoftworks.reaperbleiem.coordinator.AppCoordinator
import com.ethossoftworks.reaperbleiem.ui.home.IemScreen
import com.ethossoftworks.reaperbleiem.ui.theme.AppThemeProvider
import com.outsidesource.oskitcompose.lib.rememberInject
import com.outsidesource.oskitcompose.router.RouteSwitch

@Composable
fun App(coordinator: AppCoordinator = rememberInject()) {
    AppThemeProvider {
        RouteSwitch(coordinator) {
            when (it) {
                is Route.Home -> IemScreen()
            }
            InfoMessageContainer()
        }
    }
}
