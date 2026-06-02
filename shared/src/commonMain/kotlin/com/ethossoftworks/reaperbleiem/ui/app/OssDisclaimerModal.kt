package com.ethossoftworks.reaperbleiem.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.ethossoftworks.reaperbleiem.ui.form.AppLoadingIndicator
import com.ethossoftworks.reaperbleiem.ui.theme.AppTheme
import com.ethossoftworks.reaperbleiem.ui.theme.appModalSurface
import com.outsidesource.oskitcompose.markdown.Markdown
import com.outsidesource.oskitcompose.markdown.MarkdownStyles
import com.outsidesource.oskitcompose.popup.Modal
import com.outsidesource.oskitcompose.popup.ModalStyles
import com.outsidesource.oskitcompose.scrollbars.KmpScrollbarStyle
import com.outsidesource.oskitcompose.scrollbars.KmpVerticalScrollbar
import com.outsidesource.oskitcompose.scrollbars.rememberKmpScrollbarAdapter
import org.jetbrains.compose.resources.ExperimentalResourceApi
import reaper_ble_iem.shared.generated.resources.Res

@OptIn(ExperimentalResourceApi::class)
@Composable
fun OssDisclaimerModal(isVisible: Boolean, onDismissRequest: () -> Unit) {
    val colors = AppTheme.colors
    val dimensions = AppTheme.dimensions
    val typography = AppTheme.typography
    val defaultFont = typography.defaultFontFamily

    val markdownContent by
        produceState(initialValue = "") { value = Res.readBytes("files/open-source-licenses.md").decodeToString() }

    Modal(
        modifier = Modifier.padding(dimensions.screenPadding).appModalSurface(),
        isVisible = isVisible,
        styles = ModalStyles.UserDefinedContent,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier.heightIn(min = 250.dp, max = 500.dp).widthIn(min = 300.dp, max = 600.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (markdownContent.isBlank()) {
                AppLoadingIndicator()
            } else {
                Box {
                    val scrollState = rememberScrollState()

                    Markdown(
                        styles =
                            remember {
                                MarkdownStyles(
                                    paragraphTextStyle =
                                        TextStyle(fontFamily = defaultFont, color = colors.textPrimary),
                                    linkTextStyle = TextStyle(fontFamily = defaultFont, color = colors.accent),
                                )
                            },
                        modifier =
                            Modifier.fillMaxSize()
                                .padding(
                                    top = dimensions.screenPadding,
                                    start = dimensions.screenPadding,
                                    end = dimensions.screenPadding,
                                )
                                .verticalScroll(scrollState)
                                .padding(bottom = dimensions.screenPadding),
                        text = markdownContent,
                    )

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
            }
        }
    }
}
