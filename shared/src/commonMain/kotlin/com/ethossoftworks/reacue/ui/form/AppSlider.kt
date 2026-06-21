package com.ethossoftworks.reacue.ui.form

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Label
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethossoftworks.reacue.ui.theme.AppTheme
import com.ethossoftworks.reacue.ui.theme.AppThemeProvider
import com.ethossoftworks.reacue.ui.theme.appModalSurface
import com.outsidesource.oskitcompose.form.DpAxisSize
import com.outsidesource.oskitcompose.form.KmpSlider
import com.outsidesource.oskitcompose.form.KmpSliderAlignment
import com.outsidesource.oskitcompose.form.KmpSliderDirection
import com.outsidesource.oskitcompose.form.KmpSliderScope
import com.outsidesource.oskitcompose.form.KmpSliderStyle
import com.outsidesource.oskitcompose.form.KmpSliderTick
import com.outsidesource.oskitcompose.form.KmpSliderTickPosition
import com.outsidesource.oskitcompose.form.KmpSliderTickStyle
import com.outsidesource.oskitcompose.form.Label
import com.outsidesource.oskitcompose.form.MultiThumbMode
import com.outsidesource.oskitcompose.popup.Modal
import com.outsidesource.oskitcompose.popup.ModalStyles
import com.outsidesource.oskitkmp.text.KmpNumberFormatter
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource
import reacue.shared.generated.resources.Res
import reacue.shared.generated.resources.add
import reacue.shared.generated.resources.remove

@Composable
fun AppSlider(
    value: Float,
    range: ClosedRange<Float>,
    onDoubleTap: () -> Unit = {},
    onChange: (Float) -> Unit,
    label: String,
    labelSlot: @Composable KmpSliderScope.() -> Unit = { Label() },
    step: Float,
    valueFormatter: (Float) -> String,
    stringToValue: (String, Float) -> Float = { new, current -> new.toFloatOrNull() ?: current },
    modifier: Modifier = Modifier,
    ticks: List<KmpSliderTick> = emptyList(),
) {
    val theme = AppTheme.colors
    val textStyle = MaterialTheme.typography.bodyLarge
    val styles =
        remember(theme) {
            KmpSliderStyle(
                trackFill = SolidColor(theme.accent),
                trackBackground = theme.sliderTrackBg,
                valueLabelBackground = SolidColor(theme.bgSurface),
                valueLabelTextStyle = textStyle,
                trackThickness = 6.dp,
                thumbBackground = SolidColor(theme.textPrimary),
                labelTextStyle = textStyle,
            )
        }

    KmpSlider(
        modifier = modifier,
        value = value,
        onChange = onChange,
        range = range,
        step = step,
        styles = styles,
        label = label,
        valueFormatter = valueFormatter,
        stringToValue = stringToValue,
        ticks = ticks,
        onDoubleTap = onDoubleTap,
        labelSlot = labelSlot,
        manualEntrySlot = { isVisible, valueString, onTextChange, onCancel, onCommit ->
            AppManualEntryModal(
                isVisible = isVisible,
                valueString = valueString,
                onTextChange = onTextChange,
                onCancel = onCancel,
                onCommit = onCommit,
            )
        },
    )
}

@Composable
private fun KmpSliderScope.AppManualEntryModal(
    isVisible: Boolean,
    valueString: String,
    maxWidth: Dp = 300.dp,
    onTextChange: (String) -> Unit,
    onCancel: () -> Unit,
    onCommit: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val colors = AppTheme.colors

    Modal(
        modifier = Modifier.widthIn(max = maxWidth).appModalSurface().padding(16.dp),
        isVisible = isVisible,
        styles = ModalStyles.UserDefinedContent,
        onDismissRequest = onCancel,
    ) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        var value by remember { mutableStateOf("") }
        var isNegative by remember { mutableStateOf(false) }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            label?.let { label ->
                Text(text = label, fontSize = 16.sp)
                Text(
                    fontSize = 12.sp,
                    text =
                        "${valueFormatter(range.start)}${units ?: ""} \u2014 " +
                            "${valueFormatter(range.endInclusive)}${units ?: ""}",
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            AppTextField(
                modifier =
                    Modifier.fillMaxWidth().focusRequester(focusRequester).onKeyEvent {
                        if (!isEnabled) return@onKeyEvent false
                        if ((it.key != Key.Enter && it.key != Key.NumPadEnter) || it.type != KeyEventType.KeyUp)
                            return@onKeyEvent false
                        onCommit()
                        return@onKeyEvent true
                    },
                value = value,
                iconStart = {
                    Box(
                        modifier =
                            Modifier.size(28.dp)
                                .clip(CircleShape)
                                .clickable(
                                    onClick = {
                                        isNegative = !isNegative
                                        value = "${if (isNegative) "-" else "+"}${value.trimStart('+', '-')}"
                                        onTextChange(value)
                                    }
                                )
                                .background(colors.accentTint, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            modifier = Modifier.size(10.dp),
                            painter =
                                painterResource(
                                    if (isNegative) {
                                        Res.drawable.remove
                                    } else {
                                        Res.drawable.add
                                    }
                                ),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colors.textPrimary),
                        )
                    }
                },
                placeholder = valueString,
                onChange = {
                    value = it
                    onTextChange(it)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                keyboardActions = KeyboardActions(onDone = { onCommit() }),
                singleLine = true,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
            ) {
                AppButton(label = "Cancel", onClick = onCancel)
                AppButton(label = "Ok", onClick = onCommit)
            }
        }
    }
}

@Composable
@Preview
fun AppSliderPreview() {
    AppThemeProvider {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            AppSlider(
                value = .5f,
                range = 0f..1f,
                onChange = {},
                label = "Test",
                step = .01f,
                valueFormatter = { "${(it * 100f).roundToInt()}%" },
            )
        }
    }
}

@Preview
@Composable
private fun KmpSliderPreview() {
    Column(
        modifier =
            Modifier.verticalScroll(rememberScrollState()).background(Color.White).systemBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        val testValues = remember { mutableStateOf(mapOf("0" to -20f, "1" to 0f, "2" to 20f)) }
        var isDisabled by remember { mutableStateOf(false) }

        Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Disabled")
        }

        KmpSlider(
            label = "Group",
            isEnabled = !isDisabled,
            multiThumbMode = MultiThumbMode.Group,
            values = testValues.value,
            range = -100f..100f,
            styles = KmpSliderStyle().copy(),
            ticks =
                remember {
                    (-100..100 step 10).map {
                        KmpSliderTick(
                            value = it.toFloat(),
                            label = if (it % 20 == 0) it.toString() else null,
                            style = if (it % 20 == 0) KmpSliderTickStyle.Line.Medium else KmpSliderTickStyle.Line.Short,
                        )
                    }
                },
            onChange = {
                testValues.value =
                    testValues.value.toMutableMap().apply { it.forEach { (key, value) -> this[key] = value } }
            },
        )

        KmpSlider(
            values = remember { derivedStateOf { mapOf("0" to (testValues.value["0"] ?: 0f)) } }.value,
            label = "Start Alignment",
            isEnabled = !isDisabled,
            units = "%",
            range = -100f..100f,
            styles = KmpSliderStyle().copy(),
            ticks = remember { (-100..100 step 20).map { KmpSliderTick(value = it.toFloat(), label = it.toString()) } },
            onChange = {
                testValues.value =
                    testValues.value.toMutableMap().apply { it.forEach { (key, value) -> this[key] = value } }
            },
        )

        KmpSlider(
            values = remember { derivedStateOf { mapOf("1" to (testValues.value["1"] ?: 0f)) } }.value,
            label = "Center Alignment",
            isEnabled = !isDisabled,
            units = "%",
            range = -100f..100f,
            styles =
                KmpSliderStyle()
                    .copy(
                        trackFill = Brush.linearGradient(0f to Color.Blue, 1f to Color.Red),
                        trackFillAlignment = KmpSliderAlignment.Center,
                    ),
            ticks =
                remember {
                    (-100..100 step 20).map {
                        KmpSliderTick(
                            value = it.toFloat(),
                            label = it.toString(),
                            style = KmpSliderTickStyle.Line.Medium,
                        )
                    }
                },
            onChange = {
                testValues.value =
                    testValues.value.toMutableMap().apply { it.forEach { (key, value) -> this[key] = value } }
            },
        )

        KmpSlider(
            values = remember { derivedStateOf { mapOf("2" to (testValues.value["2"] ?: 0f)) } }.value,
            label = "End Alignment",
            isEnabled = !isDisabled,
            units = "%",
            range = -100f..100f,
            styles =
                KmpSliderStyle()
                    .copy(
                        trackFill = Brush.linearGradient(0f to Color.Blue, 1f to Color.LightGray),
                        trackFillAlignment = KmpSliderAlignment.End,
                    ),
            ticks =
                remember {
                    (-100..100 step 20).map {
                        KmpSliderTick(
                            value = it.toFloat(),
                            label = it.toString(),
                            style = KmpSliderTickStyle.Line.Medium,
                        )
                    }
                },
            onChange = {
                testValues.value =
                    testValues.value.toMutableMap().apply { it.forEach { (key, value) -> this[key] = value } }
            },
        )

        KmpSlider(
            values =
                remember {
                        derivedStateOf {
                            mapOf("0" to (testValues.value["0"] ?: 0f), "2" to (testValues.value["2"] ?: 0f))
                        }
                    }
                    .value,
            label = "Range",
            isEnabled = !isDisabled,
            range = -100f..100f,
            styles = KmpSliderStyle().copy(),
            deadband = 1f,
            ticks =
                remember {
                    buildList {
                        KmpSliderTick(
                                value = -100f,
                                label = "0",
                                style =
                                    KmpSliderTickStyle(
                                        labelPosition =
                                            KmpSliderTickPosition(alignment = KmpSliderAlignment.End, offset = 4.dp),
                                        shapeSize = DpAxisSize(0.dp, 0.dp),
                                    ),
                            )
                            .let { add(it) }
                        KmpSliderTick(
                                value = 100f,
                                label = "100",
                                style =
                                    KmpSliderTickStyle(
                                        labelPosition =
                                            KmpSliderTickPosition(alignment = KmpSliderAlignment.End, offset = 4.dp),
                                        shapeSize = DpAxisSize(0.dp, 0.dp),
                                    ),
                            )
                            .let { add(it) }
                        for (i in -90..90 step 10) {
                            KmpSliderTick(
                                    value = i.toFloat(),
                                    style =
                                        KmpSliderTickStyle.Circle.copy(
                                            shapeBrush = SolidColor(Color.White.copy(alpha = .75f)),
                                            shapePosition = KmpSliderTickPosition(alignment = KmpSliderAlignment.Center),
                                        ),
                                )
                                .let { add(it) }
                        }
                    }
                },
            onChange = {
                testValues.value =
                    testValues.value.toMutableMap().apply { it.forEach { (key, value) -> this[key] = value } }
            },
        )

        val logValues = remember { mutableStateOf(mapOf("0" to 50f)) }
        KmpSlider(
            values = logValues.value,
            isEnabled = !isDisabled,
            label = "Logarithmic",
            range = 20f..20_000f,
            valueFormatter =
                remember {
                    val numberFormatter = KmpNumberFormatter(maximumFractionDigits = 0)
                    return@remember { numberFormatter.format(it) }
                },
            units = "hz",
            logarithmic = true,
            styles = KmpSliderStyle().copy(),
            ticks =
                remember {
                    buildList {
                        add(KmpSliderTick(value = 20f, label = "20hz"))
                        add(KmpSliderTick(value = 50f, label = "50"))
                        add(KmpSliderTick(value = 100f, label = "100"))
                        add(KmpSliderTick(value = 200f, label = "200"))
                        add(KmpSliderTick(value = 500f, label = "500"))
                        add(KmpSliderTick(value = 1_000f, label = "1k"))
                        add(KmpSliderTick(value = 2_000f, label = "2k"))
                        add(KmpSliderTick(value = 5_000f, label = "5k"))
                        add(KmpSliderTick(value = 10_000f, label = "10k"))
                        add(KmpSliderTick(value = 20_000f, label = "20k"))
                    }
                },
            onChange =
                remember {
                    {
                        logValues.value =
                            logValues.value.toMutableMap().apply { it.forEach { (key, value) -> this[key] = value } }
                    }
                },
        )

        KmpSlider(
            modifier = Modifier.height(300.dp),
            values = testValues.value,
            isEnabled = !isDisabled,
            direction = KmpSliderDirection.Vertical,
            multiThumbMode = MultiThumbMode.Group,
            label = "Basic".uppercase(),
            units = "%",
            range = -100f..100f,
            styles = KmpSliderStyle().copy(),
            ticks = remember { (-100..100 step 20).map { KmpSliderTick(value = it.toFloat(), label = it.toString()) } },
            onChange = {
                testValues.value =
                    testValues.value.toMutableMap().apply { it.forEach { (key, value) -> this[key] = value } }
            },
        )
    }
}
