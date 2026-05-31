package org.tekfive.kviash.routing

internal sealed class RouteSegmentMatcher(
    val label: String,
    val terminal: Boolean
) : Comparable<RouteSegmentMatcher> {
    abstract fun matches(segment: String): Boolean

    override fun toString(): String {
        return label
    }
}

internal object RootTreeSegmentMatcher : RouteSegmentMatcher("", false) {
    override fun matches(segment: String): Boolean {
        return true
    }

    override fun compareTo(other: RouteSegmentMatcher): Int {
        return 1
    }
}

internal class LiteralRouteSegmentMatcher(
    val literalSegment: String,
    val ignoreCase: Boolean) : RouteSegmentMatcher(literalSegment, false) {

    override fun matches(segment: String): Boolean {
        return literalSegment.equals(segment, ignoreCase)
    }

    override fun compareTo(other: RouteSegmentMatcher): Int {
        return when (other) {
            is LiteralRouteSegmentMatcher -> {
                literalSegment.compareTo(other.literalSegment)
            }

            else -> {
                -1
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LiteralRouteSegmentMatcher

        if (ignoreCase != other.ignoreCase) return false
        if (literalSegment != other.literalSegment) return false

        return true
    }

    override fun hashCode(): Int {
        var result = ignoreCase.hashCode()
        result = 31 * result + literalSegment.hashCode()
        return result
    }
}

internal class PatternRouteSegmentMatcher(val regex: Regex, label: String) : RouteSegmentMatcher(label, false) {
    override fun matches(segment: String): Boolean {
        return regex.matches(segment)
    }

    override fun compareTo(other: RouteSegmentMatcher): Int {
        return when (other) {
            is RootTreeSegmentMatcher,
            is GobblerSegmentMatcher -> {
                -1
            }

            is PatternRouteSegmentMatcher -> {
                regex.pattern.compareTo(other.regex.pattern)
            }

            is LiteralRouteSegmentMatcher -> {
                1
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PatternRouteSegmentMatcher

        return regex.pattern == other.regex.pattern
    }

    override fun hashCode(): Int {
        return regex.hashCode()
    }

    override fun toString(): String {
        return regex.pattern
    }

    companion object {
        operator fun invoke(pattern: String, ignoreCase: Boolean): PatternRouteSegmentMatcher {
            val regex = if (ignoreCase) {
                Regex(pattern, RegexOption.IGNORE_CASE)
            } else {
                Regex(pattern)
            }
            return PatternRouteSegmentMatcher(regex, "{$pattern}")
        }
    }

}

internal object GobblerSegmentMatcher : RouteSegmentMatcher("{**}", true) {
    override fun matches(segment: String): Boolean {
        return true
    }

    override fun compareTo(other: RouteSegmentMatcher): Int {
        return if (other is GobblerSegmentMatcher) {
            0
        } else if (other is RootTreeSegmentMatcher) {
            -1
        } else {
            1
        }
    }
}


