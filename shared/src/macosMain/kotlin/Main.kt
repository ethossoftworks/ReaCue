import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Window
import com.ethossoftworks.reaperbleiem.PlatformContext
import com.ethossoftworks.reaperbleiem.initKoin
import com.ethossoftworks.reaperbleiem.service.bluetooth.AppleKmpBlePeripheralManager
import com.ethossoftworks.reaperbleiem.service.bluetooth.BlePeripheralService
import com.ethossoftworks.reaperbleiem.service.iem.NetworkIemService
import com.ethossoftworks.reaperbleiem.ui.app.App
import platform.AppKit.NSApp
import platform.AppKit.NSApplication

val koin = initKoin(platformContext = PlatformContext()).koin

fun main() {
    NSApplication.sharedApplication()
    Window("Native MacOS App") {
        LaunchedEffect(Unit) {
            val iemService = NetworkIemService()
            iemService.subscribe().collect {
                println(it)
            }

            val kmpBle = BlePeripheralService(AppleKmpBlePeripheralManager())
            kmpBle.start()
        }

        App()
    }
    NSApp?.run()
}
