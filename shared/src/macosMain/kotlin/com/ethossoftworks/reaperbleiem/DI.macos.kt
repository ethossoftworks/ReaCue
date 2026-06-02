package com.ethossoftworks.reaperbleiem

import com.ethossoftworks.reaperbleiem.lib.bluetooth.IKmpBlePeripheralManager
import com.ethossoftworks.reaperbleiem.lib.bluetooth.KmpBlePeripheralManager
import com.ethossoftworks.reaperbleiem.service.iem.BlePeripheralIemService
import com.ethossoftworks.reaperbleiem.service.iem.IIemService
import org.koin.dsl.bind
import org.koin.dsl.module

val macOsDiModule = module {
    single { BlePeripheralIemService(get(), get(), get()) } bind IIemService::class
    single { KmpBlePeripheralManager() } bind IKmpBlePeripheralManager::class
}
