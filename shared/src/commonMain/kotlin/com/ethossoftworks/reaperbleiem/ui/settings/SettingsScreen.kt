package com.ethossoftworks.reaperbleiem.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ethossoftworks.reaperbleiem.ui.app.AppToolbar
import com.ethossoftworks.reaperbleiem.ui.app.Screen
import com.ethossoftworks.reaperbleiem.ui.form.AppButton
import com.ethossoftworks.reaperbleiem.ui.form.AppTextField
import org.jetbrains.compose.resources.stringResource
import reaper_ble_iem.shared.generated.resources.Res
import reaper_ble_iem.shared.generated.resources.host_id
import reaper_ble_iem.shared.generated.resources.host_passcode
import reaper_ble_iem.shared.generated.resources.reaper_osc_device_port
import reaper_ble_iem.shared.generated.resources.reaper_osc_listen_port
import reaper_ble_iem.shared.generated.resources.reaper_web_port
import reaper_ble_iem.shared.generated.resources.save
import reaper_ble_iem.shared.generated.resources.settings

@Composable
fun SettingsScreen() {
    Screen(toolbar = { AppToolbar(title = stringResource(Res.string.settings)) }) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            AppTextField(label = stringResource(Res.string.host_id), value = "", onChange = {})
            AppTextField(label = stringResource(Res.string.host_passcode), value = "", onChange = {})
            AppTextField(label = stringResource(Res.string.reaper_web_port), value = "", onChange = {})
            AppTextField(label = stringResource(Res.string.reaper_osc_device_port), value = "", onChange = {})
            AppTextField(label = stringResource(Res.string.reaper_osc_listen_port), value = "", onChange = {})
            AppButton(modifier = Modifier.align(Alignment.End), label = stringResource(Res.string.save), onClick = {})
        }
    }
}
