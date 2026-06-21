package com.ethossoftworks.reacue

import com.ethossoftworks.reacue.lib.bluetooth.IKmpBlePeripheralManager
import com.ethossoftworks.reacue.lib.bluetooth.KmpBlePeripheralManager
import com.ethossoftworks.reacue.service.iem.BlePeripheralIemService
import com.ethossoftworks.reacue.service.iem.IIemService
import org.koin.dsl.bind
import org.koin.dsl.module

val macOsDiModule = module {
    single { BlePeripheralIemService(get(), get(), get()) } bind IIemService::class
    single { KmpBlePeripheralManager() } bind IKmpBlePeripheralManager::class
}
