package com.ethossoftworks.reaperbleiem.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.outsidesource.oskitcompose.modifier.kmpOuterShadow

fun Modifier.modalSurface(colors: AppColors, shape: Shape): Modifier =
    border(width = 1.dp, color = colors.strokePrimary, shape = shape)
        .kmpOuterShadow(blur = 8.dp, shape = shape, color = Color.Black.copy(alpha = 0.25f))
        .background(colors.bgSurface, shape = shape)
