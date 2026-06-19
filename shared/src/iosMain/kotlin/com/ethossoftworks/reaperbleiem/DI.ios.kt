package com.ethossoftworks.reaperbleiem

import com.ethossoftworks.reaperbleiem.lib.bluetooth.IKmpBleCentralManager
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBleCentralManager
import com.ethossoftworks.reaperbleiem.service.iem.BleCentralIemService
import com.ethossoftworks.reaperbleiem.service.iem.IIemService
import com.ethossoftworks.reaperbleiem.service.talkback.IMicrophoneCaptureService
import com.ethossoftworks.reaperbleiem.service.talkback.IosMicrophoneCaptureService
import org.koin.dsl.bind
import org.koin.dsl.module

fun initKoinIos() {
    initKoin(platformContext = PlatformContext(), extraModules = arrayOf(iosDiModule))
}

val iosDiModule = module {
    single { IosMicrophoneCaptureService() } bind IMicrophoneCaptureService::class
    single { BleCentralIemService(get(), get(), get()) } bind IIemService::class
    single { KmpBleCentralManager() } bind IKmpBleCentralManager::class
}
