package com.ethossoftworks.reaperbleiem

import android.content.Context
import com.ethossoftworks.reaperbleiem.lib.ActivityHolder
import com.ethossoftworks.reaperbleiem.lib.bluetooth.IKmpBlePeripheralManager
import com.ethossoftworks.reaperbleiem.service.bluetooth.KmpBleCentralManager
import com.ethossoftworks.reaperbleiem.service.iem.BleCentralIemService
import com.ethossoftworks.reaperbleiem.service.iem.BlePeripheralIemService
import com.ethossoftworks.reaperbleiem.service.iem.IIemService
import com.outsidesource.oskitkmp.storage.KmpKvStore
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

actual data class PlatformContext(val context: Context)

actual fun platformModule(platformContext: PlatformContext): Module = module {
    single { KmpKvStore(appContext = platformContext.context) }
    single { ActivityHolder() }
    single { KmpBleCentralManager(platformContext.context) }
    single { BleCentralIemService(get()) } bind IIemService::class
}

actual fun platformMockModule(platformContext: PlatformContext): Module = module {}
