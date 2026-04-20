package com.ethossoftworks.reaperbleiem

import com.outsidesource.oskitcompose.systemui.KmpAppLifecycleObserver
import com.outsidesource.oskitcompose.systemui.KmpAppLifecycleObserverContext
import com.outsidesource.oskitcompose.uikit.OSComposeUIViewController
import com.ethossoftworks.reaperbleiem.ui.app.App
import platform.UIKit.UIViewController

@Suppress("FunctionNaming")
fun MainViewController(): UIViewController {
    KmpAppLifecycleObserver.init(KmpAppLifecycleObserverContext())

    return OSComposeUIViewController { App() }
}
