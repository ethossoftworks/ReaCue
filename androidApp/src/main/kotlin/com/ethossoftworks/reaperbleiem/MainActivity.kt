package com.ethossoftworks.reaperbleiem

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.ethossoftworks.reaperbleiem.lib.ActivityHolder
import com.ethossoftworks.reaperbleiem.ui.app.App
import com.outsidesource.oskitkmp.capability.KmpCapabilities
import com.outsidesource.oskitkmp.capability.KmpCapabilityContext

class MainActivity : ComponentActivity() {
    val activityHolder: ActivityHolder by koin.inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        activityHolder.activity = this

        val capabilities by koin.inject<KmpCapabilities>()
        capabilities.init(KmpCapabilityContext(this))

        val isTablet = resources.getBoolean(R.bool.isTablet)
        requestedOrientation =
            if (isTablet) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }

        setContent { App() }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityHolder.activity = null
    }
}
