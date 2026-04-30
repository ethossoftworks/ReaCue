package com.ethossoftworks.reaperbleiem.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
open class AppColors {
    open val textColor = Color.Black
    open val primaryAppColor = Color.Black
    open val primaryBgColor = Color.Black
}

@Immutable
data object LightAppColors : AppColors() {
    override val textColor = Color.White
    override val primaryAppColor = Color.Black
    override val primaryBgColor = Color(0xFF333333)
}
