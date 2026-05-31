package org.tekfive.kviash.routing

/**
 *
 */
internal class RouteSegmentParameter (
    val routeSegment: RouteSegment,
    val parameterType: PrimitiveParameterType,
    val parameterRegex: Regex,
) {
}