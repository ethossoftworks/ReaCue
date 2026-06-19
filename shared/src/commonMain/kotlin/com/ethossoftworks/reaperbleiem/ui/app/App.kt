package com.ethossoftworks.reaperbleiem.ui.app

import androidx.compose.runtime.Composable
import com.ethossoftworks.reaperbleiem.Route
import com.ethossoftworks.reaperbleiem.coordinator.AppCoordinator
import com.ethossoftworks.reaperbleiem.ui.iem.IemScreen
import com.ethossoftworks.reaperbleiem.ui.scan.ScanScreen
import com.ethossoftworks.reaperbleiem.ui.settings.CentralSettingsScreen
import com.ethossoftworks.reaperbleiem.ui.settings.PeripheralSettingsScreen
import com.ethossoftworks.reaperbleiem.ui.theme.AppThemeProvider
import com.outsidesource.oskitcompose.lib.rememberInject
import com.outsidesource.oskitcompose.router.RouteSwitch
import com.outsidesource.oskitkmp.lib.Platform
import com.outsidesource.oskitkmp.lib.current

@Composable
fun App(coordinator: AppCoordinator = rememberInject()) {
    AppThemeProvider {
        RouteSwitch(coordinator) {
            when (it) {
                is Route.Scan -> ScanScreen()
                is Route.Iem -> IemScreen(context = it.context)
                is Route.Settings -> if (Platform.current.isDesktop) {
                    PeripheralSettingsScreen()
                } else {
                    CentralSettingsScreen()
                }
            }
            InfoMessageContainer()
        }
    }
}
