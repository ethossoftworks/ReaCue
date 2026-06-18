package com.ethossoftworks.reaperbleiem.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethossoftworks.reaperbleiem.ui.app.AppToolbar
import com.ethossoftworks.reaperbleiem.ui.app.Screen
import com.ethossoftworks.reaperbleiem.ui.form.AppButton
import com.ethossoftworks.reaperbleiem.ui.form.AppButtonType
import com.ethossoftworks.reaperbleiem.ui.form.AppDropdownItem
import com.ethossoftworks.reaperbleiem.ui.form.AppTextField
import com.ethossoftworks.reaperbleiem.ui.theme.AppTheme
import com.ethossoftworks.reaperbleiem.ui.theme.appModalSurface
import com.outsidesource.oskitcompose.interactor.collectAsState
import com.outsidesource.oskitcompose.lib.rememberInject
import com.outsidesource.oskitcompose.popup.Modal
import com.outsidesource.oskitcompose.popup.ModalStyles
import com.outsidesource.oskitcompose.scrollbars.KmpScrollbarStyle
import com.outsidesource.oskitcompose.scrollbars.KmpVerticalScrollbar
import com.outsidesource.oskitcompose.scrollbars.rememberKmpScrollbarAdapter
import com.outsidesource.oskitkmp.text.parseFloatOrNull
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import reacue.shared.generated.resources.Res
import reacue.shared.generated.resources.add
import reacue.shared.generated.resources.apply
import reacue.shared.generated.resources.are_you_sure
import reacue.shared.generated.resources.cancel
import reacue.shared.generated.resources.host_id
import reacue.shared.generated.resources.remove
import reacue.shared.generated.resources.reset_to_default
import reacue.shared.generated.resources.saving
import reacue.shared.generated.resources.settings
import reacue.shared.generated.resources.talkback_channel
import reacue.shared.generated.resources.talkback_channel_description

@Composable
fun CentralSettingsScreen(interactor: CentralSettingsScreenViewInteractor = rememberInject()) {
    val state = interactor.collectAsState()
    val colors = AppTheme.colors

    DisposableEffect(Unit) {
        interactor.onMount()
        onDispose { interactor.onUnmount() }
    }

    val scrollState = rememberScrollState()

    Screen(
        toolbar = {
            AppToolbar(
                title = stringResource(Res.string.settings),
                additionalMenuItems = { close ->
                    AppDropdownItem(
                        text = stringResource(Res.string.reset_to_default),
                        onClick = {
                            close()
                            interactor.onResetToDefaultClick()
                        },
                    )
                },
            )
        },
        contentPadding = PaddingValues.Zero,
    ) {
        Column(
            modifier =
                Modifier.verticalScroll(state = (scrollState))
                    .padding(PaddingValues(AppTheme.dimensions.screenPadding)),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            AppTextField(
                label = stringResource(Res.string.talkback_channel),
                description = stringResource(Res.string.talkback_channel_description),
                value = state.talkBackChannel,
                placeholder = state.originalSettings.talkbackChannel.toString(),
                onChange = { interactor.onTalkbackChannelChanged(it) } ,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            AppButton(
                modifier = Modifier.align(Alignment.End),
                label = stringResource(if (state.isSaving) Res.string.saving else Res.string.apply),
                onClick = interactor::onApplyClick,
                enabled = !state.isSaving,
            )
        }
        KmpVerticalScrollbar(
            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).fillMaxHeight(),
            adapter = rememberKmpScrollbarAdapter(scrollState),
            style =
                remember {
                    KmpScrollbarStyle(
                        minimalHeight = 48.dp,
                        thickness = 8.dp,
                        unhoverColor = colors.bgPrimary20,
                        hoverColor = colors.accentTint,
                    )
                },
        )
    }

    Modal(
        modifier = Modifier.widthIn(max = 400.dp).appModalSurface().padding(16.dp),
        isVisible = state.isDefaultModalVisible,
        styles = ModalStyles.UserDefinedContent,
        onDismissRequest = interactor::onResetToDefaultCancel,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(text = stringResource(Res.string.reset_to_default), fontWeight = FontWeight.Light, fontSize = 20.sp)
            Text(text = stringResource(Res.string.are_you_sure))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
            ) {
                AppButton(label = stringResource(Res.string.cancel), onClick = interactor::onResetToDefaultCancel)
                AppButton(
                    buttonType = AppButtonType.Destructive,
                    label = stringResource(Res.string.reset_to_default),
                    onClick = interactor::onResetToDefaultConfirmClick,
                )
            }
        }
    }
}
