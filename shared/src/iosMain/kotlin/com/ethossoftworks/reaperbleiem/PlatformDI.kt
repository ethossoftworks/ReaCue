package com.ethossoftworks.reaperbleiem

import com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBle
import com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleCbCentralQueue
import com.outsidesource.oskitkmp.storage.KmpKvStore
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.CoreBluetooth.CBCentralManager

actual class PlatformContext

private var swiftModule: Module = module {}

fun initKoinIos() {
    swiftModule = module { }

    initKoin(platformContext = PlatformContext())
}

actual fun platformModule(platformContext: PlatformContext): Module = module {
    includes(swiftModule)
    single { KmpKvStore() }
    single { KmpBle { CBCentralManager(null, KmpBleCbCentralQueue) } }
}

actual fun platformMockModule(platformContext: PlatformContext): Module = module {}
