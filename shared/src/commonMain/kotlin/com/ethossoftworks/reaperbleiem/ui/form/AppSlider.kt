package com.ethossoftworks.reaperbleiem.ui.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import com.ethossoftworks.reaperbleiem.ui.theme.AppTheme
import com.ethossoftworks.reaperbleiem.ui.theme.AppThemeProvider
import com.outsidesource.oskitcompose.form.KmpSlider
import com.outsidesource.oskitcompose.form.KmpSliderScope
import com.outsidesource.oskitcompose.form.KmpSliderStyle
import com.outsidesource.oskitcompose.form.KmpSliderTick
import com.outsidesource.oskitcompose.modifier.kmpOuterShadow
import com.outsidesource.oskitcompose.popup.Modal
import com.outsidesource.oskitcompose.popup.ModalStyles
import kotlin.math.roundToInt

@Composable
fun AppSlider(
    value: Float,
    range: ClosedRange<Float>,
    onChange: (Float) -> Unit,
    label: String,
    step: Float,
    valueFormatter: (Float) -> String,
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
        ticks = ticks,
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
    val theme = AppTheme.colors
    val shape = remember { RoundedCornerShape(12.dp) }

    Modal(
        modifier =
            Modifier.widthIn(max = maxWidth)
                .border(width = 1.dp, color = theme.strokePrimary, shape = shape)
                .kmpOuterShadow(blur = 8.dp, shape = shape, color = Color.Black.copy(alpha = 0.25f))
                .background(theme.bgSurface, shape = shape)
                .padding(16.dp),
        isVisible = isVisible,
        styles = ModalStyles.UserDefinedContent,
        onDismissRequest = onCancel,
    ) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        var value by remember { mutableStateOf("") }

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
                        if ((it.key != Key.Enter && it.key != Key.NumPadEnter) || it.type != KeyEventType.KeyUp) return@onKeyEvent false
                        onCommit()
                        return@onKeyEvent true
                    },
                value = value,
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
