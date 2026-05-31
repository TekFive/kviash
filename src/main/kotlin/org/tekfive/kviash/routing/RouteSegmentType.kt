package org.tekfive.kviash.routing

internal enum class RouteSegmentType(val canBeParameter: Boolean, val routeSortOrder: Int) {
    LITERAL(false, 0),
    INFERRED_EXPRESSION(true, 2),
    EXPLICIT_EXPRESSION(true, 1),
    WILDCARD_EXPRESSION(true, 3),
    PATH_GOBBLER(false, 4)
    ;
}