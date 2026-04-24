package com.ethossoftworks.reaperbleiem.ui.theme

import androidx.compose.runtime.Immutable

@Immutable interface IAppTypography {}

@Immutable data class AppTypography(val colors: IAppColors, val dimensions: IAppDimensions) : IAppTypography {}
