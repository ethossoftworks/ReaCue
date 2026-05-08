package com.ethossoftworks.reaperbleiem.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.outsidesource.oskitcompose.interactor.collectAsState
import com.outsidesource.oskitcompose.lib.rememberInjectForRoute
import com.outsidesource.oskitkmp.capability.CapabilityStatus
import com.outsidesource.oskitkmp.capability.NoPermissionReason

@Composable
fun ScanScreen(interactor: ScanScreenViewInteractor = rememberInjectForRoute()) {
    val state = interactor.collectAsState()

    DisposableEffect(Unit) {
        interactor.onMount()
        onDispose { interactor.onDispose() }
    }

    Column(
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars).padding(16.dp).fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val bleStatus = state.bluetoothStatus) {
            is CapabilityStatus.NoPermission -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (bleStatus.reason == NoPermissionReason.NotRequested) {
                        Button(onClick = interactor::onRequestBlePermissionClick) { Text("Request") }
                        Text("Request BLE Permission")
                    } else {
                        Text("BLE permission denied. Add permission in Settings")
                    }
                }
            }
            is CapabilityStatus.NotEnabled -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Enable BLE Permissions in Settings")
                }
            }
            CapabilityStatus.Ready -> {}
            CapabilityStatus.Unknown -> {}
            is CapabilityStatus.Unsupported -> {}
        }
    }
}
