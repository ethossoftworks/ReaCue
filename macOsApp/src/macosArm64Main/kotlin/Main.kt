import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import co.touchlab.kermit.CommonWriter
import com.ethossoftworks.reaperbleiem.PlatformContext
import com.ethossoftworks.reaperbleiem.initKoin
import com.ethossoftworks.reaperbleiem.lib.initLogger
import com.ethossoftworks.reaperbleiem.macOsDiModule
import com.ethossoftworks.reaperbleiem.ui.app.App
import com.outsidesource.oskitcompose.systemui.KmpWindowInsetsHolder
import com.outsidesource.oskitcompose.systemui.LocalKmpWindowInsets
import platform.AppKit.NSApplication
import platform.AppKit.NSApplicationActivationPolicy
import platform.AppKit.NSWindowDidChangeBackingPropertiesNotification
import platform.AppKit.NSWindowStyleMaskFullSizeContentView
import platform.AppKit.NSWindowTitleHidden
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue

fun main() {
    initKoin(platformContext = PlatformContext(), extraModules = arrayOf(macOsDiModule)).koin
    initLogger(CommonWriter())

    val app = NSApplication.sharedApplication()
    app.setActivationPolicy(NSApplicationActivationPolicy.NSApplicationActivationPolicyRegular)

    Window("Reaper BLE IEM") {
        // TODO: Figure out window issues on MacOS Native
        window.styleMask = window.styleMask or NSWindowStyleMaskFullSizeContentView
        window.titleVisibility = NSWindowTitleHidden
        window.titlebarAppearsTransparent = true

        var density by remember { mutableStateOf(Density(window.backingScaleFactor.toFloat())) }

        LaunchedEffect(Unit) {
            NSNotificationCenter.defaultCenter.addObserverForName(
                name = NSWindowDidChangeBackingPropertiesNotification,
                `object` = window,
                queue = NSOperationQueue.mainQueue,
            ) {
                val newScale = window.backingScaleFactor.toFloat()
                density = Density(newScale)
            }
        }

        CompositionLocalProvider(
            LocalDensity provides density,
            LocalKmpWindowInsets provides KmpWindowInsetsHolder(top = 24.dp),
        ) {
            App()
        }
    }

    app.run()
}
