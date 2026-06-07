package com.ethossoftworks.reaperbleiem.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import reacue.shared.generated.resources.Inter_18pt_Bold
import reacue.shared.generated.resources.Inter_18pt_ExtraLight
import reacue.shared.generated.resources.Inter_18pt_Light
import reacue.shared.generated.resources.Inter_18pt_Medium
import reacue.shared.generated.resources.Inter_18pt_Regular
import reacue.shared.generated.resources.Inter_18pt_SemiBold
import reacue.shared.generated.resources.Inter_18pt_Thin
import reacue.shared.generated.resources.Res
import reacue.shared.generated.resources.SourceSans3_Bold
import reacue.shared.generated.resources.SourceSans3_Medium
import reacue.shared.generated.resources.SourceSans3_Regular
import reacue.shared.generated.resources.SourceSans3_SemiBold

@Immutable
interface IAppTypography {
    @get:Composable
    val defaultFontFamily: FontFamily
        get() = Inter

    @get:Composable
    val Inter: FontFamily
        get() =
            FontFamily(
                listOf(
                    Font(Res.font.Inter_18pt_Thin, weight = FontWeight.Thin),
                    Font(Res.font.Inter_18pt_ExtraLight, weight = FontWeight.ExtraLight),
                    Font(Res.font.Inter_18pt_Light, weight = FontWeight.Light),
                    Font(Res.font.Inter_18pt_Regular, weight = FontWeight.Normal),
                    Font(Res.font.Inter_18pt_Medium, weight = FontWeight.Medium),
                    Font(Res.font.Inter_18pt_SemiBold, weight = FontWeight.SemiBold),
                    Font(Res.font.Inter_18pt_Bold, weight = FontWeight.Bold),
                )
            )

    @get:Composable
    val SourceSans3: FontFamily
        get() =
            FontFamily(
                listOf(
                    Font(Res.font.SourceSans3_Regular, weight = FontWeight.Normal),
                    Font(Res.font.SourceSans3_Medium, weight = FontWeight.Medium),
                    Font(Res.font.SourceSans3_SemiBold, weight = FontWeight.SemiBold),
                    Font(Res.font.SourceSans3_Bold, weight = FontWeight.Bold),
                )
            )
}

@Immutable data class AppTypography(val colors: AppColors, val dimensions: AppDimensions) : IAppTypography
