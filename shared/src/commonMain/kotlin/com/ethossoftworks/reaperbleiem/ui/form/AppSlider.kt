package com.ethossoftworks.reaperbleiem.ui.form

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ethossoftworks.reaperbleiem.ui.theme.AppTheme
import com.ethossoftworks.reaperbleiem.ui.theme.AppThemeProvider
import com.outsidesource.oskitcompose.form.KmpSlider
import com.outsidesource.oskitcompose.form.KmpSliderStyle
import com.outsidesource.oskitcompose.form.KmpSliderTick
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
                valueLabelBackground = SolidColor(theme.bgPopup),
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
    )
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
