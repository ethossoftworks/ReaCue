package com.ethossoftworks.reaperbleiem

import com.ethossoftworks.reaperbleiem.lib.initLogger
import com.ethossoftworks.reaperbleiem.ui.app.App
import com.outsidesource.oskitcompose.systemui.KmpAppLifecycleObserver
import com.outsidesource.oskitcompose.systemui.KmpAppLifecycleObserverContext
import com.outsidesource.oskitcompose.uikit.OSComposeUIViewController
import platform.UIKit.UIViewController

@Suppress("FunctionNaming")
fun MainViewController(): UIViewController {
    initKoinIos()
    initLogger()
    KmpAppLifecycleObserver.init(KmpAppLifecycleObserverContext())

    return OSComposeUIViewController { App() }
}
