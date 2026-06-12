package com.ethossoftworks.reaperbleiem.ui.iem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import com.ethossoftworks.reaperbleiem.ui.form.AppTextField
import com.ethossoftworks.reaperbleiem.ui.form.Knob
import com.ethossoftworks.reaperbleiem.ui.form.NumberEntryModal
import com.ethossoftworks.reaperbleiem.ui.theme.AppTheme
import com.ethossoftworks.reaperbleiem.ui.theme.appModalSurface
import com.outsidesource.oskitcompose.form.DpAxisSize
import com.outsidesource.oskitcompose.form.KmpSliderAlignment
import com.outsidesource.oskitcompose.form.KmpSliderTick
import com.outsidesource.oskitcompose.form.KmpSliderTickPosition
import com.outsidesource.oskitcompose.form.KmpSliderTickStyle
import com.outsidesource.oskitcompose.interactor.collectAsState
import com.outsidesource.oskitcompose.lib.rememberInjectForRoute
import com.outsidesource.oskitcompose.popup.Modal
import com.outsidesource.oskitcompose.popup.ModalStyles
import com.outsidesource.oskitcompose.systemui.KmpWindowInsets
import com.outsidesource.oskitcompose.systemui.top
import com.outsidesource.oskitkmp.text.KmpNumberFormatter
import com.outsidesource.oskitkmp.text.parseFloatOrNull
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import reacue.shared.generated.resources.Res
import reacue.shared.generated.resources.adjust_all_n
import reacue.shared.generated.resources.connect
import reacue.shared.generated.resources.connecting
import reacue.shared.generated.resources.disconnected_from_peripheral
import reacue.shared.generated.resources.disconnected_from_reaper
import reacue.shared.generated.resources.mix
import reacue.shared.generated.resources.mix_project
import reacue.shared.generated.resources.no_monitors
import reacue.shared.generated.resources.output
import reacue.shared.generated.resources.passcode
import reacue.shared.generated.resources.refresh
import reacue.shared.generated.resources.refreshing
import reacue.shared.generated.resources.scan_for_hosts
import reacue.shared.generated.resources.select_monitor
import reacue.shared.generated.resources.set_all_0db
import reacue.shared.generated.resources.set_all_n
import reacue.shared.generated.resources.untitled
import reacue.shared.generated.resources.view_passcode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IemScreen(
    context: IemContext,
    interactor: IemScreenViewInteractor = rememberInjectForRoute { parametersOf(context) },
) {
    val state = interactor.collectAsState()
    val dimensions = AppTheme.dimensions
    val colors = AppTheme.colors

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
                    if (state.serviceStatus == ServiceStatus.Connected && state.selectedIemId != null) {
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
                    }
                    if (context is IemContext.Peripheral) {
                        AppDropdownItem(
                            text = stringResource(Res.string.view_passcode),
                            onClick = {
                                close()
                                interactor.onViewPasscodeClick()
                            },
                        )
                    }
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
                    step = state.faderInfo.sliderStep,
                    label = stringResource(Res.string.output),
                    onChange = {
                        val hardwareOutId = track.hardwareOuts.keys.firstOrNull() ?: return@AppSlider
                        interactor.onOutputVolumeChange(track.id, hardwareOutId, it)
                    },
                    valueFormatter = { formatDb(it, state.faderInfo) },
                    stringToValue = { new, current ->
                        state.faderInfo.dbToNormalized(new.parseFloatOrNull() ?: return@AppSlider current)
                    },
                    onDoubleTap = {
                        val hardwareOutId = track.hardwareOuts.keys.firstOrNull() ?: return@AppSlider
                        interactor.onOutputVolumeChange(track.id, hardwareOutId, state.faderInfo.dbToNormalized(0f))
                    },
                    ticks = ticks,
                )

                for (receive in track.receives) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        AppSlider(
                            modifier = Modifier.weight(1f),
                            value = receive.value.volume,
                            range = 0f..1f,
                            step = state.faderInfo.sliderStep,
                            label = state.tracks[receive.value.trackId]?.name ?: continue,
                            onChange = { interactor.onReceiveVolumeChange(track.id, receive.key, it) },
                            valueFormatter = { formatDb(it, state.faderInfo) },
                            stringToValue = { new, current ->
                                state.faderInfo.dbToNormalized(new.parseFloatOrNull() ?: return@AppSlider current)
                            },
                            onDoubleTap = {
                                interactor.onReceiveVolumeChange(
                                    track.id,
                                    receive.key,
                                    state.faderInfo.dbToNormalized(0f),
                                )
                            },
                            ticks = ticks,
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text =
                                    when {
                                        receive.value.pan < .5f ->
                                            "${(((.5f - receive.value.pan) * 2f) * 100).roundToInt()}%L"
                                        receive.value.pan == .5f -> "C"
                                        receive.value.pan > .5f ->
                                            "${(((receive.value.pan - .5f) * 2f) * 100).roundToInt()}%R"
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
        valueFormatter = { formatDb(it, state.faderInfo) },
        units = "",
        label =
            when (state.numberInputModalType) {
                NumberInputModalType.Adjust -> stringResource(Res.string.adjust_all_n)
                NumberInputModalType.Set -> stringResource(Res.string.set_all_n)
                null -> ""
            },
        range = state.faderInfo.minDb..state.faderInfo.maxDb,
        onCancel = interactor::onNumberModalDismiss,
        onCommit = interactor::onNumberModalCommit,
    )

    PasscodeEntryModal(
        isVisible = state.passcodeEntry != null,
        onCommit = {
            state.passcodeEntry?.complete(it)
            interactor.onPasscodeEntryDismiss()
        },
        onCancel = {
            state.passcodeEntry?.cancel()
            interactor.onPasscodeEntryDismiss()
        },
    )

    ViewPasscodeModal(
        isVisible = state.isViewPasscodeModalVisible,
        onDismiss = interactor::onViewPasscodeModalDismiss,
        passcode = state.passcode,
    )
}

val dbFormatter = KmpNumberFormatter(minimumFractionDigits = 2, maximumFractionDigits = 2)

private fun formatDb(normalized: Float, faderInfo: FaderInfo): String {
    val roundedDb = (faderInfo.normalizedToDb(normalized) * 100f).roundToInt() / 100f
    // Adjust dB because the default curve doesn't actually land exactly on 0.00 dB
    val adjustedDb = if (roundedDb.absoluteValue <= .01f) 0.0f else roundedDb
    return if (adjustedDb <= -150f) {
        "-\u221e dB"
    } else {
        "${if (adjustedDb >= 0f) "+" else ""}${dbFormatter.format(adjustedDb)} dB"
    }
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

@Composable
fun PasscodeEntryModal(isVisible: Boolean, maxWidth: Dp = 300.dp, onCancel: () -> Unit, onCommit: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }

    Modal(
        modifier = Modifier.widthIn(max = maxWidth).appModalSurface().padding(16.dp),
        isVisible = isVisible,
        styles = ModalStyles.UserDefinedContent,
        onDismissRequest = onCancel,
    ) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        var value by remember { mutableStateOf("") }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = stringResource(Res.string.passcode), fontSize = 16.sp)

            Spacer(modifier = Modifier.height(8.dp))
            AppTextField(
                modifier =
                    Modifier.fillMaxWidth().focusRequester(focusRequester).onKeyEvent {
                        if ((it.key != Key.Enter && it.key != Key.NumPadEnter) || it.type != KeyEventType.KeyUp)
                            return@onKeyEvent false
                        onCommit(value)
                        return@onKeyEvent true
                    },
                value = value,
                onChange = { value = it },
                keyboardActions = KeyboardActions(onDone = { onCommit(value) }),
                fontFamily = FontFamily.Monospace,
                singleLine = true,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
            ) {
                AppButton(label = "Cancel", onClick = onCancel)
                AppButton(label = "Ok", onClick = { onCommit(value) })
            }
        }
    }
}

@Composable
fun ViewPasscodeModal(isVisible: Boolean, passcode: String, maxWidth: Dp = 300.dp, onDismiss: () -> Unit) {
    Modal(
        modifier = Modifier.widthIn(max = maxWidth).appModalSurface().padding(16.dp),
        isVisible = isVisible,
        styles = ModalStyles.UserDefinedContent,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = stringResource(Res.string.passcode), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                modifier = Modifier.padding(vertical = 24.dp),
                text = passcode,
                fontSize = 20.sp,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Light,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
            ) {
                AppButton(label = "Ok", onClick = onDismiss)
            }
        }
    }
}
