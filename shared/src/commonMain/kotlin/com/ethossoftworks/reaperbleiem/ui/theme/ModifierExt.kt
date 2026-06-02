package com.ethossoftworks.reaperbleiem.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.outsidesource.oskitcompose.modifier.kmpOuterShadow

@Composable
fun Modifier.appModalSurface(): Modifier {
    val colors = AppTheme.colors
    val shape = remember { RoundedCornerShape(12.dp) }
    return this.border(width = 1.dp, color = colors.strokePrimary, shape = shape)
        .kmpOuterShadow(blur = 8.dp, shape = shape, color = Color.Black.copy(alpha = 0.25f))
        .background(colors.bgSurface, shape = shape)
}
