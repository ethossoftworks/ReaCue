import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.Window
import com.ethossoftworks.reaperbleiem.PlatformContext
import com.ethossoftworks.reaperbleiem.initKoin
import com.ethossoftworks.reaperbleiem.macOsDiModule
import com.ethossoftworks.reaperbleiem.ui.app.App
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AppKit.NSApplication
import platform.AppKit.NSApplicationActivationPolicy
import platform.AppKit.NSWindowDidChangeBackingPropertiesNotification
import platform.AppKit.NSWindowStyleMaskFullSizeContentView
import platform.AppKit.NSWindowTitleHidden
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue

@OptIn(ExperimentalForeignApi::class)
fun main() {
    initKoin(platformContext = PlatformContext(), extraModules = arrayOf(macOsDiModule)).koin

    val app = NSApplication.sharedApplication()
    app.setActivationPolicy(NSApplicationActivationPolicy.NSApplicationActivationPolicyRegular)

    Window("Reaper BLE IEM") {
//        window.styleMask = window.styleMask or NSWindowStyleMaskFullSizeContentView
//        window.titleVisibility = NSWindowTitleHidden
//        window.titlebarAppearsTransparent = true

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

        CompositionLocalProvider(LocalDensity provides density) { App() }
    }

    app.run()
}
