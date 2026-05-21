package com.ethossoftworks.reaperbleiem.ui.form

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.ethossoftworks.reaperbleiem.ui.theme.AppTheme
import com.ethossoftworks.reaperbleiem.ui.theme.AppThemeProvider
import com.outsidesource.oskitcompose.modifier.kmpOuterShadow
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import reaper_ble_iem.shared.generated.resources.Res
import reaper_ble_iem.shared.generated.resources.volume_up

@Composable
fun CircleAppButton(
    icon: DrawableResource? = null,
    iconSize: DpSize = DpSize(22.dp, 22.dp),
    padding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonType: AppButtonType = AppButtonType.Default,
) {
    AppButton(
        iconStart = icon,
        iconSize = iconSize,
        padding = padding,
        onClick = onClick,
        modifier = modifier,
        buttonType = buttonType,
        shape = CircleShape,
    )
}

@Composable
fun AppButton(
    iconStart: DrawableResource? = null,
    iconEnd: DrawableResource? = null,
    iconSize: DpSize = DpSize(22.dp, 22.dp),
    label: String? = null,
    shape: Shape = remember { RoundedCornerShape(8.dp) },
    padding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonType: AppButtonType = AppButtonType.Default,
) {
    val theme = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val ripple = ripple(color = if (buttonType == AppButtonType.Error) theme.error else theme.accent)

    Row(
        modifier =
            modifier
                .heightIn(min = 44.dp)
                .semantics { role = Role.Button }
                .border(
                    1.dp,
                    color = if (buttonType == AppButtonType.Error) theme.error else theme.strokePrimary,
                    shape = shape,
                )
                .kmpOuterShadow(
                    blur = 4.dp,
                    offset = DpOffset(0.dp, 4.dp),
                    color = Color.Black.copy(alpha = .25f),
                    shape = shape,
                )
                .background(brush = theme.controlBg, shape = shape)
                .background(
                    color = if (buttonType == AppButtonType.Error) theme.errorTint else Color.Transparent,
                    shape = shape,
                )
                .background(
                    color =
                        if (isHovered) {
                            if (buttonType == AppButtonType.Error) theme.error else theme.accentTint
                        } else {
                            Color.Transparent
                        },
                    shape = shape,
                )
                .clip(shape)
                .clickable(onClick = onClick, interactionSource = interactionSource, indication = ripple)
                .padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        iconStart?.let {
            Image(
                modifier = Modifier.size(iconSize),
                painter = painterResource(it),
                contentDescription = null,
                colorFilter =
                    ColorFilter.tint(if (buttonType == AppButtonType.Error) theme.error else theme.textPrimary),
            )
        }
        label?.let {
            Text(
                text = it,
                color = if (buttonType == AppButtonType.Error) theme.error else theme.textPrimary,
                fontWeight = FontWeight.Normal,
            )
        }
        iconEnd?.let {
            Spacer(modifier = Modifier.weight(1f))
            Image(
                modifier = Modifier.size(iconSize),
                painter = painterResource(it),
                contentDescription = null,
                colorFilter =
                    ColorFilter.tint(if (buttonType == AppButtonType.Error) theme.error else theme.textPrimary),
            )
        }
    }
}

enum class AppButtonType {
    Default,
    Error,
}

@Preview
@Composable
private fun AppButtonPreview() {
    AppThemeProvider {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppButton(label = "Test", onClick = {})
            AppButton(iconStart = Res.drawable.volume_up, label = "Test 2", onClick = {})
            AppButton(
                buttonType = AppButtonType.Error,
                iconStart = Res.drawable.volume_up,
                label = "Test 2",
                onClick = {},
            )
            AppButton(iconEnd = Res.drawable.volume_up, label = "Test 2", onClick = {})
        }
    }
}
