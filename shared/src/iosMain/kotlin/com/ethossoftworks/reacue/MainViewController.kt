package com.ethossoftworks.reacue

import com.ethossoftworks.reacue.lib.initLogger
import com.ethossoftworks.reacue.ui.app.App
import com.outsidesource.oskitcompose.systemui.KmpAppLifecycleObserver
import com.outsidesource.oskitcompose.systemui.KmpAppLifecycleObserverContext
import com.outsidesource.oskitcompose.systemui.SystemBarColorEffect
import com.outsidesource.oskitcompose.systemui.SystemBarIconColor
import com.outsidesource.oskitcompose.uikit.OSComposeUIViewController
import platform.UIKit.UIViewController

@Suppress("FunctionNaming")
fun MainViewController(): UIViewController {
    initKoinIos()
    initLogger()
    KmpAppLifecycleObserver.init(KmpAppLifecycleObserverContext())

    return OSComposeUIViewController {
        SystemBarColorEffect(statusBarIconColor = SystemBarIconColor.Light)
        App()
    }
}
