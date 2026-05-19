package com.ethossoftworks.reaperbleiem.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

@Immutable
open class AppColors {
    open val textPrimary = Color.Black
    open val accent = Color.Black
    open val accentTint = Color.Black
    open val error = Color.Black
    open val errorTint = Color.Black
    open val bgPrimary = Color.Black
    open val bgPopup = Color.Black
    open val strokePrimary = Color.Black

    open val buttonBg: Brush = SolidColor(bgPrimary)
}

@Immutable
data object LightAppColors : AppColors() {
    override val textPrimary = Color(0xFFE5E7EB)
    override val accent = Color(0xFF1077f6)
    override val accentTint = accent.copy(alpha = 0.15f)
    override val error = Color(0xFFfc5c55)
    override val errorTint = error.copy(alpha = 0.15f)
    override val bgPrimary = Color(0xFF090d12)
    override val bgPopup = Color(0xFF12161a)
    override val strokePrimary = Color(0xFF383c42)

    override val buttonBg =
        Brush.linearGradient(
            colors = listOf(Color(0xFF171c22), Color(0xFF12161a)),
            start = Offset.Zero,
            end = Offset(x = 0f, y = Float.POSITIVE_INFINITY),
        )
}
