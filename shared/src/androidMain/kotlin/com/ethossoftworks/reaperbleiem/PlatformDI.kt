package com.ethossoftworks.reaperbleiem

import android.content.Context
import com.ethossoftworks.reaperbleiem.lib.ActivityHolder
import com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleCentralManager
import com.outsidesource.oskitkmp.storage.KmpKvStore
import org.koin.core.module.Module
import org.koin.dsl.module

actual data class PlatformContext(val context: Context)

actual fun platformModule(platformContext: PlatformContext): Module = module {
    single { KmpKvStore(appContext = platformContext.context) }
    single { ActivityHolder() }
    single { KmpBleCentralManager(platformContext.context) }
}

actual fun platformMockModule(platformContext: PlatformContext): Module = module {}
