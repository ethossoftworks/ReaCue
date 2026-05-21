package com.ethossoftworks.reaperbleiem.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethossoftworks.reaperbleiem.ui.form.AppCircleButton
import com.ethossoftworks.reaperbleiem.ui.form.AppDropdownItem
import com.ethossoftworks.reaperbleiem.ui.form.AppPopoverButton
import com.ethossoftworks.reaperbleiem.ui.theme.AppTheme
import com.outsidesource.oskitcompose.interactor.collectAsState
import com.outsidesource.oskitcompose.lib.rememberInject
import com.outsidesource.oskitcompose.systemui.KmpWindowInsets
import com.outsidesource.oskitcompose.systemui.top
import org.jetbrains.compose.resources.stringResource
import reaper_ble_iem.shared.generated.resources.Res
import reaper_ble_iem.shared.generated.resources.about
import reaper_ble_iem.shared.generated.resources.arrow_back
import reaper_ble_iem.shared.generated.resources.menu
import reaper_ble_iem.shared.generated.resources.settings

@Composable
fun AppToolbar(
    title: String? = null,
    additionalMenuItems: @Composable (close: () -> Unit) -> Unit = {},
    interactor: AppToolbarViewInteractor = rememberInject(),
) {
    val state = interactor.collectAsState()
    val theme = AppTheme.colors

    Row(
        modifier = Modifier.windowInsetsPadding(KmpWindowInsets.top).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.hasBackStack) {
            AppCircleButton(icon = Res.drawable.arrow_back, onClick = interactor::onBackClick)
        }
        title?.let {
            Text(text = it.uppercase(), fontSize = 18.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = 0.0.sp)
        }
        Spacer(modifier = Modifier.weight(1f))
        AppPopoverButton(icon = Res.drawable.menu) { close ->
            additionalMenuItems(close)
            AppDropdownItem(
                text = stringResource(Res.string.settings),
                onClick = {
                    close()
                    interactor.onSettingsClick()
                },
            )
            AppDropdownItem(
                text = stringResource(Res.string.about),
                onClick = {
                    close()
                    interactor.onAboutClick()
                },
            )
        }
    }
}
