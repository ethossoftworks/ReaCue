package com.ethossoftworks.reaperbleiem.ui.scan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ethossoftworks.reaperbleiem.ui.app.Screen
import com.ethossoftworks.reaperbleiem.ui.form.AppButton
import com.ethossoftworks.reaperbleiem.ui.form.AppLoadingIndicator
import com.outsidesource.oskitcompose.interactor.collectAsState
import com.outsidesource.oskitcompose.lib.rememberInjectForRoute
import com.outsidesource.oskitkmp.capability.CapabilityStatus
import com.outsidesource.oskitkmp.capability.NoPermissionReason
import org.jetbrains.compose.resources.stringResource
import reaper_ble_iem.shared.generated.resources.Res
import reaper_ble_iem.shared.generated.resources.ble_disabled_message
import reaper_ble_iem.shared.generated.resources.ble_permission_denied_message
import reaper_ble_iem.shared.generated.resources.ble_permission_request_message
import reaper_ble_iem.shared.generated.resources.no_devices_found
import reaper_ble_iem.shared.generated.resources.scanning

@Composable
fun ScanScreen(interactor: ScanScreenViewInteractor = rememberInjectForRoute()) {
    val state = interactor.collectAsState()

    DisposableEffect(Unit) {
        interactor.onMount()
        onDispose { interactor.onDispose() }
    }

    Screen {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp, alignment = Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.bluetoothStatus is CapabilityStatus.NoPermission) {
                if (state.bluetoothStatus.reason == NoPermissionReason.NotRequested) {
                    Text(text = stringResource(Res.string.ble_permission_request_message))
                    AppButton(label = "Request", onClick = interactor::onRequestBlePermissionClick)
                } else {
                    Text(text = stringResource(Res.string.ble_permission_denied_message))
                }
            }

            if (state.bluetoothStatus == CapabilityStatus.NotEnabled) {
                Text(text = stringResource(Res.string.ble_disabled_message))
            }

            if (state.bluetoothStatus == CapabilityStatus.Ready) {
                for (device in state.devices.values) {
                    Text(
                        modifier = Modifier.clickable(onClick = { interactor.onDeviceClick(device) }),
                        text = "${device.identifier} - ${device.name}",
                    )
                }

                if (state.devices.isEmpty() && !state.isScanning) {
                    Text(text = stringResource(Res.string.no_devices_found), textAlign = TextAlign.Center)
                }

                if (state.isScanning) {
                    Text(text = stringResource(Res.string.scanning))
                    AppLoadingIndicator()
                } else {
                    AppButton(label = "Scan", onClick = interactor::onScan)
                }
            }
        }
    }
}
