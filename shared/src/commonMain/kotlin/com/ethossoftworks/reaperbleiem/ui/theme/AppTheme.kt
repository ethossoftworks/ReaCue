package com.ethossoftworks.reaperbleiem.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.min

val LocalAppColors = staticCompositionLocalOf<IAppColors> { LightAppColors }
val LocalAppTypography = staticCompositionLocalOf<IAppTypography> { AppTypography(LightAppColors, PhoneAppDimensions) }
val LocalAppDimensions = staticCompositionLocalOf<IAppDimensions> { PhoneAppDimensions }

object AppTheme {
    val colors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current

    val typography
        @Composable @ReadOnlyComposable get() = LocalAppTypography.current

    val dimensions
        @Composable @ReadOnlyComposable get() = LocalAppDimensions.current
}

@Composable
fun AppThemeProvider(colorsOverride: IAppColors? = null, content: @Composable BoxScope.() -> Unit) {
    val windowInfo = LocalWindowInfo.current
    val colors =
        when {
            colorsOverride != null -> colorsOverride
            !isSystemInDarkTheme() -> LightAppColors
            else -> LightAppColors
        }

    val containerSize = windowInfo.containerSize
    val density = LocalDensity.current
    val size =
        remember(containerSize) { with(density) { DpSize(containerSize.width.toDp(), containerSize.height.toDp()) } }

    val minDimension = min(size.width.value, size.height.value).dp
    val dimensions =
        when {
            minDimension <= 600.dp -> PhoneAppDimensions
            minDimension <= 1024.dp -> TabletAppDimensions
            else -> DesktopAppDimensions
        }
    val typography = remember(colors, dimensions) { AppTypography(colors, dimensions) }

    val textSelectionColors = remember {
        TextSelectionColors(
            handleColor = colors.primaryAppColor,
            backgroundColor = colors.primaryAppColor.copy(alpha = .15f),
        )
    }

    MaterialTheme {
        CompositionLocalProvider(
            LocalTextSelectionColors provides textSelectionColors,
            LocalAppColors provides colors,
            LocalAppTypography provides typography,
            LocalAppDimensions provides dimensions,
        ) {
            Box(modifier = Modifier.fillMaxSize().background(AppTheme.colors.primaryBgColor), content = content)
        }
    }
}
