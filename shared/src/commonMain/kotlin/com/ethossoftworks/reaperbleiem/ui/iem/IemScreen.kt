package com.ethossoftworks.reaperbleiem.ui.iem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethossoftworks.reaperbleiem.interactor.ServiceStatus
import com.ethossoftworks.reaperbleiem.service.iem.FaderInfo
import com.ethossoftworks.reaperbleiem.service.iem.IemContext
import com.ethossoftworks.reaperbleiem.ui.app.AppToolbar
import com.ethossoftworks.reaperbleiem.ui.app.Screen
import com.ethossoftworks.reaperbleiem.ui.form.AppButton
import com.ethossoftworks.reaperbleiem.ui.form.AppDropdown
import com.ethossoftworks.reaperbleiem.ui.form.AppDropdownItem
import com.ethossoftworks.reaperbleiem.ui.form.AppLoadingIndicator
import com.ethossoftworks.reaperbleiem.ui.form.AppSlider
import com.ethossoftworks.reaperbleiem.ui.form.Knob
import com.ethossoftworks.reaperbleiem.ui.form.NumberEntryModal
import com.ethossoftworks.reaperbleiem.ui.theme.AppTheme
import com.outsidesource.oskitcompose.form.DpAxisSize
import com.outsidesource.oskitcompose.form.KmpSliderAlignment
import com.outsidesource.oskitcompose.form.KmpSliderTick
import com.outsidesource.oskitcompose.form.KmpSliderTickPosition
import com.outsidesource.oskitcompose.form.KmpSliderTickStyle
import com.outsidesource.oskitcompose.interactor.collectAsState
import com.outsidesource.oskitcompose.lib.rememberInjectForRoute
import com.outsidesource.oskitcompose.systemui.KmpWindowInsets
import com.outsidesource.oskitcompose.systemui.top
import com.outsidesource.oskitkmp.text.KmpNumberFormatter
import kotlin.collections.forEach
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
import reaper_ble_iem.shared.generated.resources.no_monitors
import reaper_ble_iem.shared.generated.resources.output
import reaper_ble_iem.shared.generated.resources.refresh
import reaper_ble_iem.shared.generated.resources.refreshing
import reaper_ble_iem.shared.generated.resources.scan_for_hosts
import reaper_ble_iem.shared.generated.resources.select_monitor
import reaper_ble_iem.shared.generated.resources.set_all_0db
import reaper_ble_iem.shared.generated.resources.set_all_n
import reaper_ble_iem.shared.generated.resources.untitled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IemScreen(
    context: IemContext,
    interactor: IemScreenViewInteractor = rememberInjectForRoute { parametersOf(context) },
) {
    val state = interactor.collectAsState()
    val dimensions = AppTheme.dimensions
    val colors = AppTheme.colors
    val dbFormatter = remember { KmpNumberFormatter(minimumFractionDigits = 1, maximumFractionDigits = 1) }

    DisposableEffect(Unit) {
        interactor.onMount()
        onDispose { interactor.onUnmount() }
    }

    Screen(
        toolbar = {
            AppToolbar(
                title =
                    if (state.serviceStatus == ServiceStatus.Connected && !state.isRefreshing) {
                        stringResource(
                            Res.string.mix_project,
                            state.projectName.ifEmpty { stringResource(Res.string.untitled) },
                        )
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
                        color = colors.strokePrimary,
                    )
                },
            )
        },
        windowInsetsPadding = KmpWindowInsets.top,
        contentPadding =
            PaddingValues(
                top = dimensions.screenPadding,
                start = dimensions.screenPadding,
                end = dimensions.screenPadding,
            ),
    ) {
        if (state.serviceStatus == ServiceStatus.Connecting) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = stringResource(Res.string.connecting), textAlign = TextAlign.Center)
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
                    Text(text = stringResource(Res.string.disconnected_from_peripheral), textAlign = TextAlign.Center)
                } else {
                    Text(text = stringResource(Res.string.disconnected_from_reaper), textAlign = TextAlign.Center)
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
                Text(text = stringResource(Res.string.refreshing), textAlign = TextAlign.Center)
                AppLoadingIndicator()
            }
            return@Screen
        }

        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppDropdown(
                modifier = Modifier.fillMaxWidth(),
                valueLabel = state.tracks[state.selectedIemId]?.name ?: stringResource(Res.string.select_monitor),
            ) { dismiss ->
                var monitorTrackCount = 0
                state.tracks.forEach { (trackId, track) ->
                    if (!track.isIem) return@forEach
                    monitorTrackCount++

                    AppDropdownItem(
                        text = state.tracks[trackId]?.name ?: return@forEach,
                        onClick = {
                            dismiss()
                            interactor.onIemSelect(trackId)
                        },
                    )
                }

                if (monitorTrackCount == 0) {
                    AppDropdownItem(text = stringResource(Res.string.no_monitors), isEnabled = false, onClick = {})
                }
            }

            val track = state.tracks[state.selectedIemId] ?: return@Column
            val hardwareOut = track.hardwareOuts.values.firstOrNull()

            Column(
                modifier =
                    Modifier.weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = dimensions.screenPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val ticks = rememberTicks(state.faderInfo)

                AppSlider(
                    value = (hardwareOut?.volume ?: 0f),
                    range = 0f..1f,
                    step = .01f,
                    label = stringResource(Res.string.output),
                    onChange = { interactor.onOutputVolumeChange(track.id, it) },
                    valueFormatter = {
                        val db = state.faderInfo.normalizedToDb(it)
                        "${if (db > 0f) "+" else ""}${dbFormatter.format(db)} dB"
                    },
                    onDoubleTap = {
                        interactor.onOutputVolumeChange(
                            track.id,
                            state.faderInfo.dbToNormalized(0f)
                        )
                    },
                    ticks = ticks,
                )

                for (receive in track.receives) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        AppSlider(
                            modifier = Modifier.weight(1f),
                            value = receive.value.volume,
                            range = 0f..1f,
                            step = .01f,
                            label = state.tracks[receive.value.trackId]?.name ?: continue,
                            onChange = { interactor.onReceiveVolumeChange(track.id, receive.key, it) },
                            valueFormatter = {
                                val db = state.faderInfo.normalizedToDb(it)
                                "${if (db > 0f) "+" else ""}${dbFormatter.format(db)} dB"
                            },
                            onDoubleTap = {
                                interactor.onReceiveVolumeChange(
                                    track.id,
                                    receive.key,
                                    state.faderInfo.dbToNormalized(0f)
                                )
                            },
                            ticks = ticks,
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = when {
                                    receive.value.pan < .5f -> "${(((.5f - receive.value.pan) * 2f) * 100).roundToInt()}%L"
                                    receive.value.pan == .5f -> "C"
                                    receive.value.pan > .5f -> "${(((receive.value.pan - .5f) * 2f) * 100).roundToInt()}%R"
                                    else -> ""
                                },
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                            )
                            Knob(
                                value = receive.value.pan,
                                onValueChange = { interactor.onReceivePanChange(track.id, receive.key, it) },
                                onDoubleTap = { interactor.onReceivePanChange(track.id, receive.key, .5f) },
                                maxAngle = 160f,
                            )
                        }
                    }
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
private fun rememberTicks(faderInfo: FaderInfo): List<KmpSliderTick> {
    val theme = AppTheme.colors
    return remember(faderInfo) {
        listOf(
            KmpSliderTick(
                value = faderInfo.dbToNormalized(0f),
                style =
                    KmpSliderTickStyle(
                        shapeBrush = SolidColor(theme.strokePrimary),
                        shapePosition = KmpSliderTickPosition(alignment = KmpSliderAlignment.Start),
                        shapeSize = DpAxisSize(mainAxis = 1.dp, crossAxis = 10.dp),
                    ),
            ),
            KmpSliderTick(
                value = faderInfo.dbToNormalized(0f),
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
