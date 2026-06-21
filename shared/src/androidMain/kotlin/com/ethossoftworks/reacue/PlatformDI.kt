package com.ethossoftworks.reacue

import android.content.Context
import com.ethossoftworks.reacue.lib.ActivityHolder
import com.ethossoftworks.reacue.service.bluetooth.KmpBleCentralManager
import com.ethossoftworks.reacue.service.iem.BleCentralIemService
import com.ethossoftworks.reacue.service.iem.IIemService
import com.ethossoftworks.reacue.service.talkback.AndroidMicrophoneCaptureService
import com.ethossoftworks.reacue.service.talkback.IMicrophoneCaptureService
import com.outsidesource.oskitkmp.storage.KmpKvStore
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

actual data class PlatformContext(val context: Context)

actual fun platformModule(platformContext: PlatformContext): Module = module {
    single { KmpKvStore(appContext = platformContext.context) }
    single { ActivityHolder() }
    single { KmpBleCentralManager(platformContext.context) }
    single { AndroidMicrophoneCaptureService() } bind IMicrophoneCaptureService::class
    single { BleCentralIemService(get(), get(), get()) } bind IIemService::class
}

actual fun platformMockModule(platformContext: PlatformContext): Module = module {}
