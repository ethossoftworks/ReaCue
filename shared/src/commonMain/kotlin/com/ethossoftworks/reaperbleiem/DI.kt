package com.ethossoftworks.reaperbleiem

import com.ethossoftworks.reaperbleiem.coordinator.AppCoordinator
import com.ethossoftworks.reaperbleiem.interactor.CapabilityInteractor
import com.ethossoftworks.reaperbleiem.interactor.IemInteractor
import com.ethossoftworks.reaperbleiem.interactor.InfoMessageInteractor
import com.ethossoftworks.reaperbleiem.lib.KmpBuildEnvironmentOverrider
import com.ethossoftworks.reaperbleiem.service.iem.NetworkIemService
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
    single { AppCoordinator() }
    single { KmpBuildEnvironmentOverrider(get()) }
    single { KmpCapabilities() }

    single { NetworkIemService() }

    // App Interactors
    single { CapabilityInteractor(get()) }
    single { InfoMessageInteractor() }
    single { IemInteractor(get()) }
}

fun mockModule() = module {}
