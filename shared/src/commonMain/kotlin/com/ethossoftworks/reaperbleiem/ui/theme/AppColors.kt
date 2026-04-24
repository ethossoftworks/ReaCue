package com.ethossoftworks.reaperbleiem.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

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
