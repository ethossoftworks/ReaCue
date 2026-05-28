package com.ethossoftworks.reaperbleiem.ui.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.ethossoftworks.reaperbleiem.ui.theme.AppTheme
import com.outsidesource.oskitcompose.modifier.kmpOuterShadow
import com.outsidesource.oskitcompose.popup.Popover
import com.outsidesource.oskitcompose.popup.PopoverAnchors
import org.jetbrains.compose.resources.DrawableResource

@Composable
fun AppPopoverButton(icon: DrawableResource, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    Box {
        var isVisible by remember { mutableStateOf(false) }

        AppCircleButton(icon = icon, onClick = { isVisible = true })

        Popover(
            isVisible = isVisible,
            onDismissRequest = { isVisible = false },
            anchors = PopoverAnchors.ExternalBottomAlignEnd,
        ) {
            AppPopover(content = { content({ isVisible = false }) })
        }
    }
}

@Composable
fun AppPopover(content: @Composable ColumnScope.() -> Unit) {
    val theme = AppTheme.colors
    val shape = remember { RoundedCornerShape(8.dp) }

    Column(
        modifier =
            Modifier.border(1.dp, color = theme.strokePrimary, shape = shape)
                .kmpOuterShadow(
                    blur = 12.dp,
                    offset = DpOffset(0.dp, 4.dp),
                    color = Color.Black.copy(alpha = .25f),
                    shape = shape,
                )
                .clip(shape)
                .background(brush = theme.bgControl, shape = shape)
                .width(200.dp)
    ) {
        content()
    }
}
