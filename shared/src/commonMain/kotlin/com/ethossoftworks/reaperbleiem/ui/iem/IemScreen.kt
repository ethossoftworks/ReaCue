package com.ethossoftworks.reaperbleiem.ui.iem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ethossoftworks.reaperbleiem.interactor.ServiceStatus
import com.ethossoftworks.reaperbleiem.service.iem.IemContext
import com.ethossoftworks.reaperbleiem.ui.app.Screen
import com.ethossoftworks.reaperbleiem.ui.form.AppButton
import com.ethossoftworks.reaperbleiem.ui.form.AppDropdown
import com.ethossoftworks.reaperbleiem.ui.form.AppDropdownItem
import com.ethossoftworks.reaperbleiem.ui.form.AppLoadingIndicator
import com.outsidesource.oskitcompose.interactor.collectAsState
import com.outsidesource.oskitcompose.layout.FlexRowLayoutScope.weight
import com.outsidesource.oskitcompose.lib.rememberInjectForRoute
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import reaper_ble_iem.shared.generated.resources.Res
import reaper_ble_iem.shared.generated.resources.connect
import reaper_ble_iem.shared.generated.resources.connecting
import reaper_ble_iem.shared.generated.resources.disconnected_from_peripheral
import reaper_ble_iem.shared.generated.resources.disconnected_from_reaper
import reaper_ble_iem.shared.generated.resources.output
import reaper_ble_iem.shared.generated.resources.refresh
import reaper_ble_iem.shared.generated.resources.scan_for_devices
import reaper_ble_iem.shared.generated.resources.select_iem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IemScreen(
    context: IemContext,
    interactor: IemScreenViewInteractor = rememberInjectForRoute { parametersOf(context) },
) {
    val state = interactor.collectAsState()

    LaunchedEffect(Unit) { interactor.onMount() }

    Screen {
        if (state.serviceStatus == ServiceStatus.Connecting) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = stringResource(Res.string.connecting))
                AppLoadingIndicator()
            }
            return@Screen
        } else if (state.serviceStatus == ServiceStatus.Disconnected) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (context is IemContext.Central) {
                    Text(text = stringResource(Res.string.disconnected_from_peripheral))
                } else {
                    Text(text = stringResource(Res.string.disconnected_from_reaper))
                }
                AppButton(label = stringResource(Res.string.connect), onClick = interactor::onConnectClick)
                if (context is IemContext.Central) {
                    AppButton(
                        label = stringResource(Res.string.scan_for_devices),
                        onClick = interactor::onBackToScanClick,
                    )
                }
            }
            return@Screen
        }

        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = state.projectName)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppDropdown(
                        modifier = Modifier.weight(1f),
                        valueLabel = state.tracks[state.selectedIemId]?.name ?: stringResource(Res.string.select_iem),
                    ) { dismiss ->
                        state.tracks.forEach { (trackId, track) ->
                            if (!track.isIem) return@forEach
                            AppDropdownItem(
                                text = state.tracks[trackId]?.name ?: return@forEach,
                                onClick = {
                                    dismiss()
                                    interactor.onIemSelect(trackId)
                                },
                            )
                        }
                    }

                    AppButton(label = stringResource(Res.string.refresh), onClick = interactor::onRefreshClick)
                }
            }

            val track = state.tracks[state.selectedIemId] ?: return@Column
            val hardwareOut = track.hardwareOuts.values.firstOrNull()

            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column {
                    Text(text = stringResource(Res.string.output))
                    Slider(
                        value = hardwareOut?.volume ?: 0f,
                        onValueChange = { interactor.onOutputVolumeChange(track.id, it) },
                    )
                }

                for (receive in track.receives) {
                    Column {
                        Text(state.tracks[receive.value.trackId]?.name ?: continue)
                        Slider(
                            value = receive.value.volume,
                            onValueChange = { interactor.onReceiveVolumeChange(track.id, receive.key, it) },
                        )
                    }
                }
            }
        }
    }
}
