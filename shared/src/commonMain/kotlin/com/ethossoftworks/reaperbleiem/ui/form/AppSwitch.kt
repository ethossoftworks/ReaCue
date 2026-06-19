package com.ethossoftworks.reaperbleiem.ui.form

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethossoftworks.reaperbleiem.ui.theme.AppTheme

@Composable
fun AppSwitch(
    label: String? = null,
    checked: Boolean,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onChange: (Boolean) -> Unit = {},
) {
    val colors = AppTheme.colors
    val isPressed by interactionSource.collectIsPressedAsState()

    val animatedAspectRatio by
        animateFloatAsState(
            targetValue = if (isPressed) 1.25f else 1f,
            animationSpec = appCustomTween(durationMillis = 300),
        )
    val animatedBackground by
        animateColorAsState(
            targetValue = if (checked) colors.accent else colors.bgPrimary20,
            animationSpec = appCustomTween(durationMillis = 300),
        )
    val animatedAlignment by
        animateFloatAsState(
            targetValue = if (checked) 1f else -1f,
            animationSpec = appCustomTween(durationMillis = 300),
        )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) {
            Text(text = label, fontSize = 14.sp, color = colors.textPrimary)
        }
        Column(
            Modifier
                .wrapContentSize(Alignment.Center)
                .requiredSize(51.dp, 31.dp)
                .clip(CircleShape)
                .toggleable(
                    value = checked,
                    onValueChange = { onChange(it) },
                    role = Role.Switch,
                    interactionSource = interactionSource,
                    indication = null,
                )
                .background(animatedBackground)
                .padding(2.dp)
        ) {
            Box(
                Modifier.fillMaxHeight()
                    .aspectRatio(animatedAspectRatio)
                    .align(BiasAlignment.Horizontal(animatedAlignment))
                    .shadow(elevation = 4.dp, shape = CircleShape)
                    .background(Color.White)
            )
        }
    }
}

private fun <T> appCustomTween(
    durationMillis: Int = 400,
    delayMillis: Int = 0,
    easing: Easing = CubicBezierEasing(0.2f, 0.9f, 0.42f, 1f),
): TweenSpec<T> = tween(durationMillis = durationMillis, easing = easing, delayMillis = delayMillis)
