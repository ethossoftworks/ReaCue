import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.DpSize
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
import platform.Foundation.NSBundle
import platform.posix.chdir

fun main() {
    // CMP 1.11.0 on macOS native resolves resources relative to CWD. When running as an .app
    // bundle, redirect CWD to Contents/Resources/ where the stripped resources are staged.
    // Guard on ".app" suffix so the Gradle run task (bare kexe, CWD already correct) is unaffected.
    if (NSBundle.mainBundle.bundlePath.endsWith(".app")) {
        NSBundle.mainBundle.resourcePath?.let { chdir(it) }
    }

    initKoin(platformContext = PlatformContext(), extraModules = arrayOf(macOsDiModule)).koin
    initLogger(CommonWriter())

    val app = NSApplication.sharedApplication()
    app.setActivationPolicy(NSApplicationActivationPolicy.NSApplicationActivationPolicyRegular)

    // Window is resolved to the custom implementation in CustomWindow.kt, which fixes:
    // - title bar visible on launch (chrome configured before makeKeyAndOrderFront)
    // - hit-box drift after monitor change (scene.density updated via notification)
    CustomWindow(title = "ReaCue", minSize = DpSize(480.dp, 400.dp)) {
        CompositionLocalProvider(LocalKmpWindowInsets provides KmpWindowInsetsHolder(top = 30.dp)) { App() }
    }

    app.activateIgnoringOtherApps(true)
    app.run()
}
