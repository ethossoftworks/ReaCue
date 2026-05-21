package com.ethossoftworks.reaperbleiem.ui.iem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.ethossoftworks.reaperbleiem.interactor.ServiceStatus
import com.ethossoftworks.reaperbleiem.service.iem.IemContext
import com.ethossoftworks.reaperbleiem.ui.app.AppToolbar
import com.ethossoftworks.reaperbleiem.ui.app.Screen
import com.ethossoftworks.reaperbleiem.ui.form.AppButton
import com.ethossoftworks.reaperbleiem.ui.form.AppDropdown
import com.ethossoftworks.reaperbleiem.ui.form.AppDropdownItem
import com.ethossoftworks.reaperbleiem.ui.form.AppLoadingIndicator
import com.ethossoftworks.reaperbleiem.ui.form.AppSlider
import com.ethossoftworks.reaperbleiem.ui.form.NumberEntryModal
import com.ethossoftworks.reaperbleiem.ui.theme.AppTheme
import com.outsidesource.oskitcompose.form.DpAxisSize
import com.outsidesource.oskitcompose.form.KmpSliderAlignment
import com.outsidesource.oskitcompose.form.KmpSliderTick
import com.outsidesource.oskitcompose.form.KmpSliderTickPosition
import com.outsidesource.oskitcompose.form.KmpSliderTickStyle
import com.outsidesource.oskitcompose.interactor.collectAsState
import com.outsidesource.oskitcompose.lib.rememberInjectForRoute
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import reaper_ble_iem.shared.generated.resources.Res
import reaper_ble_iem.shared.generated.resources.adjust_all_n
import reaper_ble_iem.shared.generated.resources.connect
import reaper_ble_iem.shared.generated.resources.connecting
import reaper_ble_iem.shared.generated.resources.disconnected_from_peripheral
import reaper_ble_iem.shared.generated.resources.disconnected_from_reaper
import reaper_ble_iem.shared.generated.resources.mix
import reaper_ble_iem.shared.generated.resources.mix_project
import reaper_ble_iem.shared.generated.resources.output
import reaper_ble_iem.shared.generated.resources.refresh
import reaper_ble_iem.shared.generated.resources.refreshing
import reaper_ble_iem.shared.generated.resources.scan_for_hosts
import reaper_ble_iem.shared.generated.resources.select_iem
import reaper_ble_iem.shared.generated.resources.set_all_0db
import reaper_ble_iem.shared.generated.resources.set_all_n

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IemScreen(
    context: IemContext,
    interactor: IemScreenViewInteractor = rememberInjectForRoute { parametersOf(context) },
) {
    val state = interactor.collectAsState()
    val theme = AppTheme.colors

    DisposableEffect(Unit) {
        interactor.onMount()
        onDispose { interactor.onUnmount() }
    }

    Screen(
        toolbar = {
            AppToolbar(
                title =
                    if (state.serviceStatus == ServiceStatus.Connected && !state.isRefreshing) {
                        stringResource(Res.string.mix_project, state.projectName)
                    } else {
                        stringResource(Res.string.mix)
                    },
                additionalMenuItems = { close ->
                    if (state.serviceStatus != ServiceStatus.Connected || state.selectedIemId == null) return@AppToolbar
                    AppDropdownItem(
                        text = stringResource(Res.string.refresh),
                        onClick = {
                            close()
                            interactor.onRefreshClick()
                        },
                    )
                    AppDropdownItem(
                        text = stringResource(Res.string.set_all_0db),
                        onClick = {
                            close()
                            interactor.onSetAllTo0Click()
                        },
                    )
                    AppDropdownItem(
                        text = stringResource(Res.string.set_all_n),
                        onClick = {
                            close()
                            interactor.onSetAllToNClick()
                        },
                    )
                    AppDropdownItem(
                        text = stringResource(Res.string.adjust_all_n),
                        onClick = {
                            close()
                            interactor.onAdjustAllByNClick()
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        color = theme.strokePrimary,
                    )
                },
            )
        }
    ) {
        if (state.serviceStatus == ServiceStatus.Connecting) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = stringResource(Res.string.connecting))
                AppLoadingIndicator()
            }
            return@Screen
        } else if (state.serviceStatus == ServiceStatus.Disconnected) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
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
                        label = stringResource(Res.string.scan_for_hosts),
                        onClick = interactor::onBackToScanClick,
                    )
                }
            }
            return@Screen
        }

        if (state.isRefreshing) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = stringResource(Res.string.refreshing))
                AppLoadingIndicator()
            }
            return@Screen
        }

        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppDropdown(
                modifier = Modifier.fillMaxWidth(),
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

            val track = state.tracks[state.selectedIemId] ?: return@Column
            val hardwareOut = track.hardwareOuts.values.firstOrNull()

            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val ticks = rememberTicks()

                AppSlider(
                    value = (hardwareOut?.volume ?: 0f) * 100f,
                    range = 0f..100f,
                    step = 1f,
                    label = stringResource(Res.string.output),
                    onChange = { interactor.onOutputVolumeChange(track.id, it / 100f) },
                    valueFormatter = { "${it.roundToInt()}%" },
                    ticks = ticks,
                )

                for (receive in track.receives) {
                    AppSlider(
                        value = receive.value.volume * 100f,
                        range = 0f..100f,
                        step = 1f,
                        label = state.tracks[receive.value.trackId]?.name ?: continue,
                        onChange = { interactor.onReceiveVolumeChange(track.id, receive.key, it / 100f) },
                        valueFormatter = { "${it.roundToInt()}%" },
                        ticks = ticks,
                    )
                }
            }
        }
    }

    NumberEntryModal(
        isVisible = state.numberInputModalType != null,
        valueFormatter = { it.roundToInt().toString() },
        units = "%",
        label =
            when (state.numberInputModalType) {
                NumberInputModalType.Adjust -> stringResource(Res.string.adjust_all_n)
                NumberInputModalType.Set -> stringResource(Res.string.set_all_n)
                null -> ""
            },
        range =
            when (state.numberInputModalType) {
                NumberInputModalType.Adjust -> -100f..100f
                NumberInputModalType.Set -> 0f..100f
                null -> 0f..0f
            },
        onCancel = interactor::onNumberModalDismiss,
        onCommit = interactor::onNumberModalCommit,
    )
}

@Composable
private fun rememberTicks(): List<KmpSliderTick> {
    val theme = AppTheme.colors
    return remember {
        listOf(
            KmpSliderTick(
                value = .716f * 100f,
                style =
                    KmpSliderTickStyle(
                        shapeBrush = SolidColor(theme.strokePrimary),
                        shapePosition = KmpSliderTickPosition(alignment = KmpSliderAlignment.Start),
                        shapeSize = DpAxisSize(mainAxis = 1.dp, crossAxis = 10.dp),
                    ),
            ),
            KmpSliderTick(
                value = .716f * 100f,
                style =
                    KmpSliderTickStyle(
                        shapeBrush = SolidColor(theme.strokePrimary),
                        shapePosition = KmpSliderTickPosition(alignment = KmpSliderAlignment.End),
                        shapeSize = DpAxisSize(mainAxis = 1.dp, crossAxis = 10.dp),
                    ),
            ),
        )
    }
}
