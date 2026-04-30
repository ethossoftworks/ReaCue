package com.ethossoftworks.reaperbleiem

import com.outsidesource.oskitkmp.router.IRoute

sealed class Route : IRoute {
    data object Home : Route()
}
