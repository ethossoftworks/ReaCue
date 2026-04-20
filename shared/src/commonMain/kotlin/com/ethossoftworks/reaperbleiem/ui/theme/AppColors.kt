package com.ethossoftworks.reaperbleiem.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

@Immutable
interface IAppColors {
    val primaryAppColor: Color
    val primaryBgColor: Color
}

@Immutable
data object LightAppColors : IAppColors {
    override val primaryAppColor = Color.Black
    override val primaryBgColor = Color.White
}
