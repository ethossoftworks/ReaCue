package com.ethossoftworks.reaperbleiem.coordinator

import androidx.compose.animation.ExperimentalAnimationApi
import com.ethossoftworks.reaperbleiem.Route
import com.outsidesource.oskitcompose.router.PushFromRightRouteTransition
import com.outsidesource.oskitkmp.coordinator.Coordinator
import com.outsidesource.oskitkmp.lib.Platform
import com.outsidesource.oskitkmp.lib.current

@OptIn(ExperimentalAnimationApi::class)
class AppCoordinator :
    Coordinator(
        initialRoute = if (Platform.current.isMobile) Route.Scan else Route.Iem,
        defaultTransition = PushFromRightRouteTransition,
    )
