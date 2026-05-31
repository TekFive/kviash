package org.tekfive.kviash.routing

import org.tekfive.kviash.http.toPath

class RoutePath internal constructor(
    val path: String,
    internal val segments: List<RouteSegment>,
) {

    init {
        for ((index, segment) in segments.withIndex()) {
            when (segment.type) {
                RouteSegmentType.EXPLICIT_EXPRESSION -> {
                    try {
                        Regex(segment.value!!)
                    } catch (e: Exception) {
                        throw IllegalArgumentException("Invalid regular expression \"${segment.value}\".")
                    }
                }

                RouteSegmentType.PATH_GOBBLER -> {
                    if (index < segments.lastIndex) {
                        throw IllegalArgumentException("Gobbler expression must come last in path.")
                    }
                }

                else -> {}
            }
        }
    }

    override fun toString(): String {
        return path
    }

    companion object {
        operator fun invoke(pathSegments: List<String>): RoutePath {
            val parsedSegments = mutableListOf<RouteSegment>()
            for ((index, segment) in pathSegments.withIndex()) {
                parsedSegments.add(RouteSegment.Companion(index, segment))
            }

            return RoutePath(pathSegments.toPath(), parsedSegments)
        }
    }
}