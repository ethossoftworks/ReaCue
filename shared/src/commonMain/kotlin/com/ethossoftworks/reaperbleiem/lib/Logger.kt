package com.ethossoftworks.reaperbleiem.lib

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.platformLogWriter
import com.outsidesource.kmpbuild.KmpBuildEnvironment
import com.outsidesource.kmpbuild.KmpBuildInfo

fun initLogger(logger: LogWriter = platformLogWriter()) {
    if (KmpBuildInfo.environment == KmpBuildEnvironment.Production) {
        Logger.setLogWriters(emptyList())
    } else {
        Logger.setLogWriters(listOf(logger))
    }
}
