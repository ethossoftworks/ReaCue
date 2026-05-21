package com.ethossoftworks.reaperbleiem.ui.app

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ethossoftworks.reaperbleiem.ui.form.AppDropdownItem
import com.ethossoftworks.reaperbleiem.ui.form.AppPopoverButton
import org.jetbrains.compose.resources.stringResource
import reaper_ble_iem.shared.generated.resources.Res
import reaper_ble_iem.shared.generated.resources.about
import reaper_ble_iem.shared.generated.resources.menu
import reaper_ble_iem.shared.generated.resources.settings

@Composable
fun RowScope.AppToolbar(title: String? = null, actions: @Composable (close: () -> Unit) -> Unit) {
    title?.let { Text(text = it.uppercase(), fontSize = 18.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = 0.0.sp) }
    Spacer(modifier = Modifier.weight(1f))
    AppPopoverButton(icon = Res.drawable.menu) { close ->
        actions(close)
        AppDropdownItem(text = stringResource(Res.string.settings), onClick = { close() })
        AppDropdownItem(text = stringResource(Res.string.about), onClick = { close() })
    }
}
