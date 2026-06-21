package com.ethossoftworks.reacue

import com.ethossoftworks.reacue.lib.bluetooth.IKmpBleCentralManager
import com.ethossoftworks.reacue.lib.bluetooth.KmpBleCentralManager
import com.ethossoftworks.reacue.service.iem.BleCentralIemService
import com.ethossoftworks.reacue.service.iem.IIemService
import com.ethossoftworks.reacue.service.talkback.IMicrophoneCaptureService
import com.ethossoftworks.reacue.service.talkback.IosMicrophoneCaptureService
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
