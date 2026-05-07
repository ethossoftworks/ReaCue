package com.ethossoftworks.reaperbleiem

import com.ethossoftworks.reaperbleiem.lib.bluetooth.AppleKmpBlePeripheralManager
import com.ethossoftworks.reaperbleiem.service.bluetooth.IKmpBlePeripheralManager
import com.ethossoftworks.reaperbleiem.service.iem.BlePeripheralIemService
import com.ethossoftworks.reaperbleiem.service.iem.IIemService
import org.koin.dsl.bind
import org.koin.dsl.module

val macOsDiModule = module {
    single { BlePeripheralIemService(get(), get()) } bind IIemService::class
    single { AppleKmpBlePeripheralManager() } bind IKmpBlePeripheralManager::class
}