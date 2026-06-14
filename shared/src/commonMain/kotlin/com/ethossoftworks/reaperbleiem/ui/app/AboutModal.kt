package com.ethossoftworks.reaperbleiem.ui.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethossoftworks.reaperbleiem.interactor.AboutViewInteractor
import com.ethossoftworks.reaperbleiem.ui.form.AppButton
import com.ethossoftworks.reaperbleiem.ui.theme.AppTheme
import com.ethossoftworks.reaperbleiem.ui.theme.appModalSurface
import com.outsidesource.kmpbuild.KmpBuildEnvironment
import com.outsidesource.kmpbuild.KmpBuildInfo
import com.outsidesource.oskitcompose.interactor.collectAsState
import com.outsidesource.oskitcompose.lib.rememberInject
import com.outsidesource.oskitcompose.popup.Modal
import com.outsidesource.oskitcompose.popup.ModalStyles
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import reacue.shared.generated.resources.Res
import reacue.shared.generated.resources.app_icon
import reacue.shared.generated.resources.app_name
import reacue.shared.generated.resources.open_source_licenses
import reacue.shared.generated.resources.project_page

@Composable
fun AboutModal(isVisible: Boolean, onDismissRequest: () -> Unit, interactor: AboutViewInteractor = rememberInject()) {
    val state = interactor.collectAsState()
    val theme = AppTheme.colors

    val uriHandler = LocalUriHandler.current
    val environment = KmpBuildInfo.environment
    val versionText = remember {
        buildString {
            append("${KmpBuildInfo.version} (${KmpBuildInfo.build})")
            if (environment != KmpBuildEnvironment.Production) {
                append(" (${environment.name.lowercase()})")
            }
        }
    }

    Modal(
        modifier = Modifier.widthIn(max = 300.dp).appModalSurface().padding(AppTheme.dimensions.screenPadding),
        isVisible = isVisible,
        styles = ModalStyles.UserDefinedContent,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(
                modifier = Modifier.size(100.dp),
                painter = painterResource(Res.drawable.app_icon),
                contentDescription = null,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = stringResource(Res.string.app_name), fontSize = 20.sp, color = theme.textPrimary)
                Text(text = versionText, fontSize = 14.sp, color = theme.textSecondary)
            }
            Text(
                text =
                    "© 2026-${Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year} Ethos Softworks",
                fontSize = 12.sp,
                color = theme.textSecondary,
            )
            AppButton(
                label = stringResource(Res.string.open_source_licenses),
                onClick = {
                    onDismissRequest()
                    interactor.onOssDisclaimerClick()
                },
            )

            AppButton(
                label = stringResource(Res.string.project_page),
                onClick = {
                    onDismissRequest()
                    interactor.onProjectPageClick(uriHandler)
                },
            )
        }
    }

    OssDisclaimerModal(isVisible = state.isOssDisclaimerVisible, onDismissRequest = interactor::onOssDisclaimerDismiss)
}
