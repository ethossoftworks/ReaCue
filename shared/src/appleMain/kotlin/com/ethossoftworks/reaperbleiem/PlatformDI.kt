package com.ethossoftworks.reaperbleiem

import com.outsidesource.oskitkmp.storage.KmpKvStore
import org.koin.core.module.Module
import org.koin.dsl.module

actual class PlatformContext

private var swiftModule: Module = module {}

fun initKoinIos() {
    swiftModule = module { }

    initKoin(platformContext = PlatformContext())
}

actual fun platformModule(platformContext: PlatformContext): Module = module {
    includes(swiftModule)
    single { KmpKvStore() }
}

actual fun platformMockModule(platformContext: PlatformContext): Module = module {}
