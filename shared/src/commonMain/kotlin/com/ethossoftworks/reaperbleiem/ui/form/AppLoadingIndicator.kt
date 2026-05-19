package com.ethossoftworks.reaperbleiem.ui.form

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ethossoftworks.reaperbleiem.ui.theme.AppTheme
import com.outsidesource.oskitcompose.modifier.kmpOuterShadow

@Composable
fun AppLoadingIndicator(size: Dp = 32.dp) {
    val theme = AppTheme.colors
    val density = LocalDensity.current
    val transition = rememberInfiniteTransition()

    val alpha by
        transition.animateFloat(
            initialValue = .5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1000), repeatMode = RepeatMode.Reverse),
        )
    val scale by
        transition.animateFloat(
            initialValue = .8f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1000), repeatMode = RepeatMode.Reverse),
        )
    val shadow by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 30f,
            animationSpec = infiniteRepeatable(tween(1000), repeatMode = RepeatMode.Reverse),
        )

    Box(
        modifier =
            Modifier.size(48.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .kmpOuterShadow(
                    blur = with(density) { shadow.toDp() },
                    color = theme.accent.copy(alpha = alpha),
                    shape = CircleShape,
                )
                .alpha(alpha)
                .background(color = theme.accent, shape = CircleShape)
                .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {}
}

@Composable
@Preview
private fun AppLoadingIndicatorPreview() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) { AppLoadingIndicator() }
}
