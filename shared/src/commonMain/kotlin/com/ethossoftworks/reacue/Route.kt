package com.ethossoftworks.reacue

import com.ethossoftworks.reacue.service.iem.IemContext
import com.outsidesource.oskitkmp.router.IRoute

sealed class Route : IRoute {
    data object Scan : Route()

    data class Iem(val context: IemContext) : Route()

    data object Settings : Route()
}
