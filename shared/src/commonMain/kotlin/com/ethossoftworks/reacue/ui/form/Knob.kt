package com.ethossoftworks.reacue.ui.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.ethossoftworks.reacue.ui.theme.AppTheme
import com.ethossoftworks.reacue.ui.theme.AppThemeProvider
import com.outsidesource.oskitcompose.modifier.kmpOuterShadow
import kotlin.math.absoluteValue

@Composable
fun Knob(
    value: Float, // Normalized 0f..1f
    onValueChange: (Float) -> Unit,
    onDoubleTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
    sensitivity: Float = .005f,
    step: Float = 0.01f,
    size: Dp = 44.dp,
    maxAngle: Float = 180f, // Must be 0f..180f
) {
    val colors = AppTheme.colors
    val latestValue by rememberUpdatedState(value)
    var isDragging by remember { mutableStateOf(false) }
    var localDragValue by remember { mutableStateOf(value) }
    val displayValue = if (isDragging) localDragValue else value

    Box(
        modifier =
            Modifier.size(size)
                .kmpOuterShadow(
                    blur = 12.dp,
                    color = Color.Black.copy(alpha = 0.25f),
                    shape = CircleShape,
                    offset = DpOffset(x = 0.dp, y = 6.dp),
                )
                .pointerInput(sensitivity, step) {
                    var totalDelta = 0f

                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            localDragValue = latestValue
                            totalDelta = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDelta += ((dragAmount.x - dragAmount.y).toDp().value * sensitivity)
                            if (totalDelta.absoluteValue < step) return@detectDragGestures

                            val stepsTaken = (totalDelta / step).toInt()
                            val snappedChange = stepsTaken * step
                            val newValue = (localDragValue + snappedChange).coerceIn(0f, 1f)
                            if (newValue == localDragValue) return@detectDragGestures

                            totalDelta = 0f
                            localDragValue = newValue
                            onValueChange(newValue)
                        },
                        onDragEnd = {
                            isDragging = false
                            totalDelta = 0f
                        },
                        onDragCancel = {
                            isDragging = false
                            totalDelta = 0f
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { onDoubleTap() }, onLongPress = { onLongPress() })
                }
                .background(colors.bgControl, CircleShape)
                .drawWithCache {
                    val width = 4.dp.toPx()

                    onDrawWithContent {
                        val topLeft = Offset(this@drawWithCache.size.width / 2f - width / 2f, -2f)
                        val size = Size(width, this@drawWithCache.size.height / 2f)

                        this@onDrawWithContent.drawContent()
                        val degrees = ((maxAngle * 2f * displayValue) - maxAngle).coerceIn(-maxAngle, maxAngle)

                        rotate(degrees) {
                            drawRoundRect(
                                color = colors.accent,
                                cornerRadius = CornerRadius(50f, 50f),
                                topLeft = topLeft,
                                size = size,
                            )
                        }
                    }
                }
                .border(width = 1.dp, brush = colors.strokeControl, shape = CircleShape)
    )
}

@Composable
@Preview
private fun KnobPreview() {
    AppThemeProvider { Column(modifier = Modifier.fillMaxSize().padding(16.dp)) { Knob(.5f, onValueChange = {}) } }
}
