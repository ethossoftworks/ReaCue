import androidx.compose.ui.window.Window
import com.ethossoftworks.reaperbleiem.PlatformContext
import com.ethossoftworks.reaperbleiem.initKoin
import com.ethossoftworks.reaperbleiem.macOsDiModule
import com.ethossoftworks.reaperbleiem.ui.app.App
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AppKit.NSApp
import platform.AppKit.NSApplication
import platform.AppKit.NSWindowStyleMaskFullSizeContentView
import platform.AppKit.NSWindowTitleHidden

@OptIn(ExperimentalForeignApi::class)
fun main() {
    initKoin(platformContext = PlatformContext(), extraModules = arrayOf(macOsDiModule)).koin

    NSApplication.sharedApplication()

    Window("Native MacOS App") {
        window.styleMask = window.styleMask or NSWindowStyleMaskFullSizeContentView
        window.titleVisibility = NSWindowTitleHidden
        window.titlebarAppearsTransparent = true

        App()
    }

    NSApp?.run()
}
