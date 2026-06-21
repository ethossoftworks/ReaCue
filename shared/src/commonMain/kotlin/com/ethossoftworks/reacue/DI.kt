package com.ethossoftworks.reacue

import com.ethossoftworks.reacue.coordinator.AppCoordinator
import com.ethossoftworks.reacue.interactor.AboutViewInteractor
import com.ethossoftworks.reacue.interactor.CapabilityInteractor
import com.ethossoftworks.reacue.interactor.IemInteractor
import com.ethossoftworks.reacue.interactor.InfoMessageInteractor
import com.ethossoftworks.reacue.lib.KmpBuildEnvironmentOverrider
import com.ethossoftworks.reacue.service.iem.IemContext
import com.ethossoftworks.reacue.service.iem.NetworkIemService
import com.ethossoftworks.reacue.service.preferences.CentralPreferencesService
import com.ethossoftworks.reacue.service.preferences.PeripheralPreferencesService
import com.ethossoftworks.reacue.ui.app.AppToolbarViewInteractor
import com.ethossoftworks.reacue.ui.iem.IemScreenViewInteractor
import com.ethossoftworks.reacue.ui.scan.ScanScreenViewInteractor
import com.ethossoftworks.reacue.ui.settings.CentralSettingsScreenViewInteractor
import com.ethossoftworks.reacue.ui.settings.PeripheralSettingsScreenViewInteractor
import com.outsidesource.oskitkmp.capability.BluetoothCapabilityFlags
import com.outsidesource.oskitkmp.capability.KmpCapabilities
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

expect fun platformModule(platformContext: PlatformContext): Module

expect fun platformMockModule(platformContext: PlatformContext): Module

expect class PlatformContext

private object OsKoin {
    var hasInitialized: Boolean = false
    var koin: KoinApplication? = null
}

fun initKoin(
    useMocks: Boolean = false,
    appDeclaration: KoinAppDeclaration = {},
    platformContext: PlatformContext,
    extraModules: Array<Module> = emptyArray(),
): KoinApplication {
    if (OsKoin.hasInitialized) return OsKoin.koin!!
    OsKoin.hasInitialized = true

    return startKoin {
            appDeclaration()

            if (useMocks) {
                modules(
                    commonModule(),
                    platformModule(platformContext),
                    mockModule(),
                    platformMockModule(platformContext),
                    *extraModules,
                )
            } else {
                modules(commonModule(), platformModule(platformContext), *extraModules)
            }
        }
        .apply { OsKoin.koin = this }
}

fun commonModule() = module {
    // Libraries
    single { KmpBuildEnvironmentOverrider(get()) }
    single {
        KmpCapabilities(bluetoothFlags = arrayOf(BluetoothCapabilityFlags.Scan, BluetoothCapabilityFlags.Connect))
    }

    // Coordinator
    single { AppCoordinator() }

    // Services
    single { NetworkIemService(get()) }
    single { PeripheralPreferencesService(get()) }
    single { CentralPreferencesService(get()) }

    // App Interactors
    single { CapabilityInteractor(get()) }
    single { InfoMessageInteractor() }
    single { IemInteractor(get()) }

    // View Interactors
    factory { params ->
        val context = params[0] as IemContext
        IemScreenViewInteractor(
            params[0],
            get(),
            get(),
            get(),
            if (context is IemContext.Peripheral) get<PeripheralPreferencesService>() else null,
            if (context is IemContext.Central) get<CentralPreferencesService>() else null,
            get(),
        )
    }
    factory { AboutViewInteractor() }
    factory { ScanScreenViewInteractor(get(), get(), get()) }
    factory { AppToolbarViewInteractor(get()) }
    factory { PeripheralSettingsScreenViewInteractor(get(), get()) }
    factory { CentralSettingsScreenViewInteractor(get(), get()) }
}

fun mockModule() = module {}
