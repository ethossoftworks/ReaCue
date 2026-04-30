package com.ethossoftworks.reaperbleiem.coordinator

import androidx.compose.animation.ExperimentalAnimationApi
import com.ethossoftworks.reaperbleiem.Route
import com.outsidesource.oskitcompose.router.PushFromRightRouteTransition
import com.outsidesource.oskitkmp.coordinator.Coordinator

@OptIn(ExperimentalAnimationApi::class)
class AppCoordinator : Coordinator(initialRoute = Route.Home, defaultTransition = PushFromRightRouteTransition)
