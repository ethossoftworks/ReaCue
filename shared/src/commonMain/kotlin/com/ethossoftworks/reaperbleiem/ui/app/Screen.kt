package com.ethossoftworks.reaperbleiem.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ethossoftworks.reaperbleiem.ui.theme.AppDimensions
import com.ethossoftworks.reaperbleiem.ui.theme.AppTheme
import com.ethossoftworks.reaperbleiem.ui.theme.LocalAppDimensions
import com.outsidesource.oskitcompose.systemui.KmpWindowInsets
import com.outsidesource.oskitcompose.systemui.bottom
import com.outsidesource.oskitcompose.systemui.vertical

@Composable
fun Screen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(AppTheme.dimensions.screenPadding),
    toolbar: (@Composable () -> Unit)? = null,
    windowInsetsPadding: WindowInsets = if (toolbar != null) KmpWindowInsets.bottom else KmpWindowInsets.vertical,
    content: @Composable BoxScope.() -> Unit,
) {
    val theme = AppTheme.colors

    Column(
        modifier =
            modifier
                .windowInsetsPadding(windowInsetsPadding)
                .fillMaxSize()
                .background(color = theme.bgPrimary)
    ) {
        toolbar?.let { it() }
        Box(modifier = Modifier.padding(contentPadding), content = content)
    }
}
