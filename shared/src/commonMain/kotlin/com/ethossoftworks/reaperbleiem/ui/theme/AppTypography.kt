package com.ethossoftworks.reaperbleiem.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
interface IAppTypography {
}

@Immutable
data class AppTypography(val colors: IAppColors, val dimensions: IAppDimensions) : IAppTypography {
}
