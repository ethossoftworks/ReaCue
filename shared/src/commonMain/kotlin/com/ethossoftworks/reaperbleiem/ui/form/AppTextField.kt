package com.ethossoftworks.reaperbleiem.ui.form

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethossoftworks.reaperbleiem.ui.theme.AppTheme
import com.ethossoftworks.reaperbleiem.ui.theme.AppThemeProvider
import com.outsidesource.oskitcompose.modifier.kmpOuterShadow
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import reaper_ble_iem.shared.generated.resources.Res
import reaper_ble_iem.shared.generated.resources.close

@Composable
fun AppTextField(
    label: String? = null,
    value: String,
    onChange: (String) -> Unit,
    iconStart: (@Composable () -> Unit)? = null,
    iconEnd: (@Composable () -> Unit)? = null,
    shape: Shape = remember { RoundedCornerShape(8.dp) },
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    placeholder: String? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    fontFamily: FontFamily = AppTheme.typography.defaultFontFamily,
) {
    val theme = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val textStyle = remember { TextStyle(fontFamily = fontFamily, color = theme.textPrimary) }
    val focusManager = LocalFocusManager.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) {
            Text(text = label, fontSize = 14.sp, color = theme.textPrimary)
        }
        BasicTextField(
            modifier =
                Modifier.fillMaxWidth()
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.Tab) {
                            focusManager.moveFocus(
                                if (e.isShiftPressed) FocusDirection.Previous else FocusDirection.Next
                            )
                            return@onPreviewKeyEvent true
                        }
                        false
                    }
                    .then(fieldModifier),
            value = value,
            onValueChange = onChange,
            cursorBrush = SolidColor(theme.textPrimary),
            textStyle = textStyle,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            decorationBox = { field ->
                Row(
                    modifier =
                        modifier
                            .heightIn(min = 44.dp)
                            .semantics { role = Role.Button }
                            .border(1.dp, color = theme.strokePrimary, shape = shape)
                            .kmpOuterShadow(
                                blur = 4.dp,
                                offset = DpOffset(0.dp, 4.dp),
                                color = Color.Black.copy(alpha = .25f),
                                shape = shape,
                            )
                            .background(brush = theme.bgControl, shape = shape)
                            .background(
                                color =
                                    if (isHovered) {
                                        theme.accentTint
                                    } else {
                                        Color.Transparent
                                    },
                                shape = shape,
                            )
                            .clip(shape)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    iconStart?.let { iconStart() }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(modifier = Modifier.alpha(.5f), text = placeholder, style = textStyle)
                        }
                        field()
                    }
                    if (iconEnd != null) {
                        iconEnd()
                    } else {
                        AppTextFieldButton(icon = Res.drawable.close, onClick = { onChange("") })
                    }
                }
            },
        )
    }
}

@Composable
fun AppTextFieldButton(onClick: () -> Unit, icon: DrawableResource) {
    val colors = AppTheme.colors

    Box(
        modifier =
            Modifier.size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .background(colors.bgPrimary30, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            modifier = Modifier.size(10.dp),
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.textPrimary),
        )
    }
}

@Preview
@Composable
private fun AppTextFieldPreview() {
    AppThemeProvider {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppTextField(modifier = Modifier.fillMaxWidth(), value = "Test", onChange = {})
            AppTextField(modifier = Modifier.fillMaxWidth(), value = "", placeholder = "Placeholder", onChange = {})
        }
    }
}
