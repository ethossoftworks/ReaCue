package com.ethossoftworks.reacue.ui.app

import androidx.compose.runtime.Composable
import com.ethossoftworks.reacue.Route
import com.ethossoftworks.reacue.coordinator.AppCoordinator
import com.ethossoftworks.reacue.ui.iem.IemScreen
import com.ethossoftworks.reacue.ui.scan.ScanScreen
import com.ethossoftworks.reacue.ui.settings.CentralSettingsScreen
import com.ethossoftworks.reacue.ui.settings.PeripheralSettingsScreen
import com.ethossoftworks.reacue.ui.theme.AppThemeProvider
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
