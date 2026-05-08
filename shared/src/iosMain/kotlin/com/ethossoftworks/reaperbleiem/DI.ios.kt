package com.ethossoftworks.reaperbleiem

import com.ethossoftworks.reaperbleiem.lib.bluetooth.AppleKmpBleCentralManager
import com.ethossoftworks.reaperbleiem.lib.bluetooth.AppleKmpBlePeripheralManager
import com.ethossoftworks.reaperbleiem.lib.bluetooth.IKmpBleCentralManager
import com.ethossoftworks.reaperbleiem.lib.bluetooth.IKmpBlePeripheralManager
import com.ethossoftworks.reaperbleiem.service.iem.BleCentralIemService
import com.ethossoftworks.reaperbleiem.service.iem.BlePeripheralIemService
import com.ethossoftworks.reaperbleiem.service.iem.IIemService
import org.koin.dsl.bind
import org.koin.dsl.module

fun initKoinIos() {
    initKoin(platformContext = PlatformContext(), extraModules = arrayOf(iosDiModule))
}

val iosDiModule = module {
    single { BleCentralIemService(get()) } bind IIemService::class
    single { AppleKmpBleCentralManager() } bind IKmpBleCentralManager::class
}