package com.ethossoftworks.reaperbleiem

import com.outsidesource.oskitkmp.router.IRoute

sealed class Route : IRoute {

    data object Splash : Route()

    data object Home : Route()
}
