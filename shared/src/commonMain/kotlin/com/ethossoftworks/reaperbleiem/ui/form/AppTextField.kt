package com.ethossoftworks.reaperbleiem.ui.form

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.ethossoftworks.reaperbleiem.ui.theme.AppTheme
import com.ethossoftworks.reaperbleiem.ui.theme.AppThemeProvider
import com.outsidesource.oskitcompose.layout.FlexRowLayoutScope.weight
import com.outsidesource.oskitcompose.modifier.kmpOuterShadow
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun AppTextField(
    value: String,
    onChange: (String) -> Unit,
    iconStart: DrawableResource? = null,
    iconEnd: DrawableResource? = null,
    iconSize: DpSize = DpSize(22.dp, 22.dp),
    shape: Shape = remember { RoundedCornerShape(8.dp) },
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    placeholder: String? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    modifier: Modifier = Modifier,
) {
    val defaultFontFamily = AppTheme.typography.defaultFontFamily
    val theme = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val textStyle = remember { TextStyle(fontFamily = defaultFontFamily, color = theme.textPrimary) }

    BasicTextField(
        modifier = modifier,
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
                        .background(brush = theme.buttonBg, shape = shape)
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
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                iconStart?.let {
                    Image(
                        modifier = Modifier.size(iconSize),
                        painter = painterResource(it),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(theme.textPrimary),
                    )
                }
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(modifier = Modifier.alpha(.5f), text = placeholder, style = textStyle)
                    }
                    field()
                }
                iconEnd?.let {
                    Spacer(modifier = Modifier.weight(1f))
                    Image(
                        modifier = Modifier.size(iconSize),
                        painter = painterResource(it),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(theme.textPrimary),
                    )
                }
            }
        },
    )
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
