package com.ethossoftworks.reaperbleiem.ui.scan

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleScanRecord
import com.ethossoftworks.reaperbleiem.ui.app.AppToolbar
import com.ethossoftworks.reaperbleiem.ui.app.Screen
import com.ethossoftworks.reaperbleiem.ui.form.AppButton
import com.ethossoftworks.reaperbleiem.ui.form.AppLoadingIndicator
import com.ethossoftworks.reaperbleiem.ui.theme.AppTheme
import com.outsidesource.oskitcompose.interactor.collectAsState
import com.outsidesource.oskitcompose.lib.rememberInjectForRoute
import com.outsidesource.oskitcompose.modifier.kmpOuterShadow
import com.outsidesource.oskitkmp.capability.CapabilityStatus
import com.outsidesource.oskitkmp.capability.NoPermissionReason
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import reaper_ble_iem.shared.generated.resources.Res
import reaper_ble_iem.shared.generated.resources.ble_disabled_message
import reaper_ble_iem.shared.generated.resources.ble_permission_denied_message
import reaper_ble_iem.shared.generated.resources.ble_permission_request_message
import reaper_ble_iem.shared.generated.resources.bluetooth
import reaper_ble_iem.shared.generated.resources.discovered_hosts
import reaper_ble_iem.shared.generated.resources.no_hosts_found
import reaper_ble_iem.shared.generated.resources.scanning

@Composable
fun ScanScreen(interactor: ScanScreenViewInteractor = rememberInjectForRoute()) {
    val state = interactor.collectAsState()

    DisposableEffect(Unit) {
        interactor.onMount()
        onDispose { interactor.onDispose() }
    }

    Screen(toolbar = { AppToolbar(title = stringResource(Res.string.discovered_hosts)) }) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp, alignment = Alignment.CenterVertically),
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
                    Device(device, onClick = { interactor.onDeviceClick(device) })
                }

                if (state.devices.isEmpty() && state.isScanning) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = stringResource(Res.string.scanning))
                    AppLoadingIndicator()
                }

                if (state.devices.isEmpty() && !state.isScanning) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = stringResource(Res.string.no_hosts_found), textAlign = TextAlign.Center)
                    AppButton(label = "Scan", onClick = interactor::onScan)
                }

                Spacer(modifier = Modifier.weight(1f))

                if (state.devices.isNotEmpty() && !state.isScanning) {
                    AppButton(label = "Scan", onClick = interactor::onScan)
                }

                if (state.devices.isNotEmpty() && state.isScanning) {
                    Text(text = stringResource(Res.string.scanning))
                    AppLoadingIndicator()
                }
            }
        }
    }
}

@Composable
private fun Device(device: KmpBleScanRecord, onClick: () -> Unit) {
    val theme = AppTheme.colors
    val cardShape = remember { RoundedCornerShape(12.dp) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val ripple = ripple(color = theme.accent)

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(80.dp)
                .kmpOuterShadow(blur = 8.dp, color = Color.Black.copy(alpha = 0.25f), shape = cardShape)
                .background(color = theme.bgSurface, shape = cardShape)
                .background(
                    color =
                        if (isHovered) {
                            theme.accentTint
                        } else {
                            Color.Transparent
                        },
                    shape = cardShape,
                )
                .clickable(onClick = onClick, interactionSource = interactionSource, indication = ripple)
                .border(width = 1.dp, color = theme.accent, shape = cardShape)
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(
            modifier = Modifier.size(32.dp),
            painter = painterResource(Res.drawable.bluetooth),
            colorFilter = ColorFilter.tint(theme.accent),
            contentDescription = null,
        )
        Text(text = device.name)
    }
}
