package com.ethossoftworks.reaperbleiem

import com.ethossoftworks.reaperbleiem.service.iem.IemContext
import com.outsidesource.oskitkmp.router.IRoute

sealed class Route : IRoute {
    data object Scan : Route()

    data class Iem(val context: IemContext) : Route()
}
