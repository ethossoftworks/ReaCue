package com.ethossoftworks.reaperbleiem.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

@Immutable
open class AppColors {
    open val textPrimary = Color.Black
    open val textSecondary = Color.Black
    open val accent = Color.Black
    open val accentTint = Color.Black
    open val error = Color.Black
    open val errorTint = Color.Black
    open val bgPrimary = Color.Black
    open val bgPrimary10 = Color.Black
    open val bgPrimary20 = Color.Black
    open val bgPrimary30 = Color.Black
    open val bgSurface = Color.Black
    open val bgControl: Brush = SolidColor(Color.Black)
    open val strokePrimary = Color.Black
    open val strokeControl: Brush = SolidColor(Color.Black)

    open val sliderTrackBg: Brush = SolidColor(Color.Black)
}

@Immutable
data object LightAppColors : AppColors() {
    override val textPrimary = Color(0xFFE5E7EB)
    override val textSecondary = Color(0xFF5f6872)
    override val accent = Color(0xFF1077f6)
    override val accentTint = accent.copy(alpha = 0.15f)
    override val error = Color(0xFFfc5c55)
    override val errorTint = error.copy(alpha = 0.15f)
    override val bgPrimary = Color(0xFF090d12)
    override val bgPrimary10 = Color(0xFF12161a)
    override val bgPrimary20 = Color(0xFF171c22)
    override val bgPrimary30 = Color(0xFF242b38)
    override val bgSurface = Color(0xFF12161a)
    override val bgControl =
        Brush.linearGradient(
            colors = listOf(bgPrimary20, bgPrimary10),
            start = Offset.Zero,
            end = Offset(x = 0f, y = Float.POSITIVE_INFINITY),
        )
    override val strokePrimary = Color(0xFF383c42)
    override val strokeControl =
        Brush.verticalGradient(0.0f to Color(0xFFaaabb1), 0.9f to Color(0xFF51585a), 1.0f to Color(0xFF0a0c0f))

    override val sliderTrackBg = Brush.verticalGradient(0.0f to bgPrimary20, 0.8f to bgPrimary20, .95f to strokePrimary)
}
