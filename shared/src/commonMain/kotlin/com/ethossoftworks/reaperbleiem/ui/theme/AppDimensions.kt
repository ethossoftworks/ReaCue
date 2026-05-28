package com.ethossoftworks.reaperbleiem.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

@Immutable
open class AppDimensions {
    val screenPadding = 24.dp
}

@Immutable data object PhoneAppDimensions : AppDimensions()

@Immutable data object TabletAppDimensions : AppDimensions()

@Immutable data object DesktopAppDimensions : AppDimensions()
