package com.ethossoftworks.reaperbleiem.ui.theme

import androidx.compose.runtime.Immutable

@Immutable interface IAppDimensions {}

@Immutable private data object DefaultDimensions : IAppDimensions {}

@Immutable data object PhoneAppDimensions : IAppDimensions by DefaultDimensions

@Immutable data object TabletAppDimensions : IAppDimensions by DefaultDimensions

@Immutable data object DesktopAppDimensions : IAppDimensions by DefaultDimensions
