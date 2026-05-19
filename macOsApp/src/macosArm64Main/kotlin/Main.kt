import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
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

fun main() {
    initKoin(platformContext = PlatformContext(), extraModules = arrayOf(macOsDiModule)).koin
    initLogger(CommonWriter())

    val app = NSApplication.sharedApplication()
    app.setActivationPolicy(NSApplicationActivationPolicy.NSApplicationActivationPolicyRegular)

    // Window is resolved to the custom implementation in CustomWindow.kt, which fixes:
    // - title bar visible on launch (chrome configured before makeKeyAndOrderFront)
    // - hit-box drift after monitor change (scene.density updated via notification)
    CustomWindow("Reaper BLE IEM") {
        CompositionLocalProvider(
            LocalKmpWindowInsets provides KmpWindowInsetsHolder(top = 24.dp),
        ) {
            App()
        }
    }

    app.run()
}
