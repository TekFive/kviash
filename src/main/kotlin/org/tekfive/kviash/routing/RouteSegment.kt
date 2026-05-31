package org.tekfive.kviash.routing

internal class RouteSegment(
    val index: Int,
    val type: RouteSegmentType,
    val value: String?
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RouteSegment

        if (index != other.index) return false
        if (type != other.type) return false
        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + type.hashCode()
        result = 31 * result + (value?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return when (type) {
            RouteSegmentType.INFERRED_EXPRESSION -> "{}"
            RouteSegmentType.WILDCARD_EXPRESSION -> "{*}"
            RouteSegmentType.EXPLICIT_EXPRESSION -> "{${value}}"
            RouteSegmentType.PATH_GOBBLER -> "{**}"
            RouteSegmentType.LITERAL -> value!!
        }
    }


    companion object {
        operator fun invoke(index: Int, urlSegment: String): RouteSegment {
            val urlSegment = urlSegment.trim()
            return if (urlSegment.startsWith("{") && urlSegment.endsWith("}")) {
                val value = urlSegment.substring(1, urlSegment.length - 1).trim()
                if (value.isBlank()) {
                    RouteSegment(index, RouteSegmentType.INFERRED_EXPRESSION, null)
                } else if (value == "*") {
                    RouteSegment(index, RouteSegmentType.WILDCARD_EXPRESSION, null)
                } else if (value == "**") {
                    RouteSegment(index, RouteSegmentType.PATH_GOBBLER, null)
                } else {
                    RouteSegment(index, RouteSegmentType.EXPLICIT_EXPRESSION, value)
                }
            } else {
                RouteSegment(index, RouteSegmentType.LITERAL, urlSegment)
            }
        }
    }
}