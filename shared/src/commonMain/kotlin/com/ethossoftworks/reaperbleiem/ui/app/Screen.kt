package com.ethossoftworks.reaperbleiem.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ethossoftworks.reaperbleiem.ui.theme.AppTheme
import com.outsidesource.oskitcompose.systemui.KmpWindowInsets
import com.outsidesource.oskitcompose.systemui.bottom
import com.outsidesource.oskitcompose.systemui.top
import com.outsidesource.oskitcompose.systemui.vertical

@Composable
fun Screen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(24.dp),
    toolbar: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val theme = AppTheme.colors

    Column(
        modifier =
            modifier
                .windowInsetsPadding(if (toolbar != null) KmpWindowInsets.bottom else KmpWindowInsets.vertical)
                .fillMaxSize()
                .background(color = theme.bgPrimary)
    ) {
        toolbar?.let {
            Row(
                modifier = Modifier.windowInsetsPadding(KmpWindowInsets.top).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                it()
            }
        }
        Box(modifier = Modifier.padding(contentPadding), content = content)
    }
}
