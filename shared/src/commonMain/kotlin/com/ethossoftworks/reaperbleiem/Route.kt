package com.ethossoftworks.reaperbleiem

import com.outsidesource.oskitkmp.router.IRoute
import com.outsidesource.oskitkmp.router.IWebRoute

sealed class Route :
    IRoute {

    data object Splash : Route()
    data object Home : Route()
}
