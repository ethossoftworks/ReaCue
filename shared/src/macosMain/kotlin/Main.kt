import androidx.compose.ui.window.Window
import com.ethossoftworks.reaperbleiem.PlatformContext
import com.ethossoftworks.reaperbleiem.initKoin
import com.ethossoftworks.reaperbleiem.ui.app.App
import platform.AppKit.NSApp
import platform.AppKit.NSApplication

val koin = initKoin(platformContext = PlatformContext()).koin

fun main() {
    NSApplication.sharedApplication()
    Window("Native MacOS App") {
        App()
    }
    NSApp?.run()
}