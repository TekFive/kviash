package org.tekfive.kviash.routing

import kotlin.reflect.KFunction
import kotlin.reflect.KParameter


/**
 * Thrown when an invalid route configuration is detected at initialization.
 */
class RoutesConfigurationException(message: String) : IllegalArgumentException(message)

/**
 * Thrown when an explicit regular expression is used in a route path segment and is mapped to a parameter in the route function but the route path segment cannot be coerced
 * into the parameter type. This exceptional state cannot be determined at initialization and so will be thrown when requests that meet this condition are received.
 */
class ExpressionParameterCoercionException(
    /**
     * The path segment, exactly as received from the client, that could not coerced.
     */
    val pathSegment: String,
    /**
     * The function defined this route.
     */
    val routeFunction: KFunction<*>,
    /**
     * The parameter whose value could not be determined from the pathSegment.
     */
    val routeParameter: KParameter,
    message: String
) : IllegalStateException(message) {
}

/**
 * Thrown at runtime if a non-nullable route function parameter cannot be supplied upon request.  This exceptional state cannot be
 * determined at initialization and so will be thrown when requests that meet this condition are received.
 */
class RequiredRouteParameterUnavailableException(
    val routeFunction: KFunction<*>,
    val routeParameter: KParameter,
    message: String = "Route function ${routeFunction.name} parameter ${routeParameter.name} is not available."
) : IllegalStateException(message)

class RouteFunctionNotMappedException(val function: KFunction<*>, message: String = "The function ${function.name} has not been mapped to a route.") : IllegalStateException(message)

class ThreadLocalContextException(message: String = "No thread local RouteContext is set.") : IllegalStateException(message)