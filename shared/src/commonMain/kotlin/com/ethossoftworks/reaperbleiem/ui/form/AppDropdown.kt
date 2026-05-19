package com.ethossoftworks.reaperbleiem.ui.form

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethossoftworks.reaperbleiem.ui.theme.AppTheme
import com.ethossoftworks.reaperbleiem.ui.theme.AppThemeProvider
import reaper_ble_iem.shared.generated.resources.Res
import reaper_ble_iem.shared.generated.resources.arrow_down

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDropdown(
    valueLabel: String,
    modifier: Modifier = Modifier,
    itemContent: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val theme = AppTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val dismiss = remember { { expanded = false } }

    ExposedDropdownMenuBox(modifier = modifier, expanded = expanded, onExpandedChange = { expanded = it }) {
        AppButton(
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            label = valueLabel,
            onClick = {},
            iconEnd = Res.drawable.arrow_down,
            iconSize = DpSize(12.dp, 12.dp),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = theme.bgPopup,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(width = 1.dp, color = theme.strokePrimary),
            content = { itemContent(dismiss) },
        )
    }
}

@Composable
fun AppDropdownItem(text: String, onClick: () -> Unit) {
    val theme = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val ripple = ripple(color = theme.accent)

    Box(
        modifier =
            Modifier.fillMaxWidth()
                .background(if (isHovered) theme.accentTint else Color.Transparent)
                .clickable(onClick = onClick, indication = ripple, interactionSource = interactionSource)
                .padding(vertical = 12.dp, horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = text, fontSize = 14.sp)
    }
}

@Preview
@Composable
private fun AppDropdownPreview() {
    AppThemeProvider {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppDropdown(valueLabel = "Test", itemContent = {})
        }
    }
}
