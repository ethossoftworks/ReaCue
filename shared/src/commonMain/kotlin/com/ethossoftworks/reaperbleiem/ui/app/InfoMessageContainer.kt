package com.ethossoftworks.reaperbleiem.ui.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ethossoftworks.reaperbleiem.interactor.InfoMessageInteractor
import com.outsidesource.oskitcompose.interactor.collectAsState
import com.outsidesource.oskitcompose.lib.rememberInject
import com.outsidesource.oskitcompose.modifier.kmpOuterShadow
import com.outsidesource.oskitcompose.popup.KmpPopup
import com.outsidesource.oskitcompose.popup.PopupPositionProvider

@Composable
fun InfoMessageContainer(modifier: Modifier = Modifier, interactor: InfoMessageInteractor = rememberInject()) {
    val state = interactor.collectAsState()
    val message = state.currentMessage ?: return

    KmpPopup(
        isFullScreen = false,
        popupPositionProvider =
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset {
                    return IntOffset(x = windowSize.width / 2 - popupContentSize.width / 2, y = windowSize.height)
                }
            },
    ) {
        val transition = remember(message) { Animatable(initialValue = 0f) }
        val progress = remember(message) { Animatable(initialValue = 1f) }

        LaunchedEffect(message) {
            transition.animateTo(targetValue = 1f)
            progress.animateTo(targetValue = 0f, animationSpec = tween(durationMillis = 2000, easing = LinearEasing))
            transition.animateTo(targetValue = 0f)
            interactor.onMessageFinished()
        }

        Row(
            modifier =
                modifier
                    .graphicsLayer {
                        alpha = transition.value
                        translationY = ((1f - transition.value) * 10f).dp.toPx()
                        scaleX = (transition.value * .1f) + .9f
                        scaleY = (transition.value * .1f) + .9f
                    }
                    .padding(bottom = 16.dp)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 16.dp)
                    .kmpOuterShadow(
                        blur = 8.dp,
                        color = Color.Black.copy(alpha = .25f),
                        offset = DpOffset(0.dp, 2.dp),
                        shape = CircleShape,
                    )
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = message.text)
        }
    }
}
