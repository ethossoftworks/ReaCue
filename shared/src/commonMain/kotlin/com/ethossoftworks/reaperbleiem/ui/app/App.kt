package com.ethossoftworks.reaperbleiem.ui.app

import androidx.compose.runtime.Composable
import com.ethossoftworks.reaperbleiem.Route
import com.ethossoftworks.reaperbleiem.coordinator.AppCoordinator
import com.ethossoftworks.reaperbleiem.ui.theme.AppThemeProvider
import com.outsidesource.oskitcompose.lib.rememberInject
import com.outsidesource.oskitcompose.router.RouteSwitch
import com.outsidesource.oskitcompose.systemui.SystemBarColorEffect
import com.outsidesource.oskitcompose.systemui.SystemBarIconColor

@Composable
fun App(
    coordinator: AppCoordinator = rememberInject(),
) {
    SystemBarColorEffect(statusBarIconColor = SystemBarIconColor.Light)

    AppThemeProvider {
        RouteSwitch(coordinator) {
            when (it) {
                is Route.Splash -> {}
                is Route.Home -> {}
            }
            InfoMessageContainer()
        }
    }
}