package com.ethossoftworks.reaperbleiem.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
open class AppColors {
    open val textColor = Color.Black
    open val primaryAppColor = Color.Black
    open val primaryBgColor = Color.Black
    open val primaryBgColor10 = Color.Black
    open val controlColor = Color.Black
}

@Immutable
data object LightAppColors : AppColors() {
    override val textColor = Color.White
    override val primaryAppColor = Color(0xFF0377BA)
    override val primaryBgColor = Color(0xFF333333)
    override val primaryBgColor10 = Color(0xFF444444)
    override val controlColor = Color(0xFF7E8B91)
}
