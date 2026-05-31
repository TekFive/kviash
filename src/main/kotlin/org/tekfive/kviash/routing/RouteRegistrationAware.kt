package org.tekfive.kviash.routing

interface RouteRegistrationAware {
    fun onRouteRegistered(route: String)
}
