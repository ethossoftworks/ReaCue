package com.ethossoftworks.reaperbleiem

import android.app.Application
import com.ethossoftworks.reaperbleiem.lib.initLogger
import com.outsidesource.oskitcompose.systemui.KmpAppLifecycleObserver
import com.outsidesource.oskitcompose.systemui.KmpAppLifecycleObserverContext
import org.koin.core.Koin

lateinit var koin: Koin

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        koin = initKoin(platformContext = PlatformContext(this)).koin
        initLogger()
        KmpAppLifecycleObserver.init(KmpAppLifecycleObserverContext())
    }
}
