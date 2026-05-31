package org.tekfive.kviash.routing

import org.tekfive.kviash.exchange.ExchangeAction
import org.tekfive.kviash.exchange.ExchangeFunction
import org.tekfive.kviash.http.AcceptType
import org.tekfive.kviash.http.HttpMethod
import org.tekfive.kviash.http.HttpRequestPath

import org.tekfive.kviash.exchange.ExchangePipeline
import org.tekfive.kviash.exchange.HttpErrorCode
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

internal class RouteTree private constructor(
    val parent: RouteTree?,
    val matcher: RouteSegmentMatcher,
    val ignoreTrailingSlash: Boolean,
    val enableRouteCache: Boolean,
    val maxCacheSize: Int?,
) : Comparable<RouteTree> {

    val depth: Int by lazy { if (parent == null) 0 else parent.depth + 1 }

    private val pipelinesByMethod: MutableMap<HttpMethod, MutableList<ExchangePipeline>> = mutableMapOf()

    var notFoundPipeline: ExchangePipeline? = null
        private set

    private var children: MutableList<RouteTree> = mutableListOf()

    private val functionsToRoutePaths = mutableMapOf<KFunction<*>, MutableList<String>>()

    private val literalPathCache by lazy { ConcurrentHashMap<String, RouteTree>() }

    lateinit var root: RouteTree
        private set
    
    val routePath: String by lazy {
        if (parent == null) {
            ""
        } else {
            "${parent.routePath}/${matcher.label}"
        }
    }

    constructor(
        ignoreTrailingSlash: Boolean,
        enableRouteCache: Boolean = true,
        maxCacheSize: Int? = null,
    ) : this(null, RootTreeSegmentMatcher, ignoreTrailingSlash, enableRouteCache, maxCacheSize) {
        root = this
    }

    fun findTypedUrl(type: String, resource: String): String? {
        for (pipelines in pipelinesByMethod.values) {
            for (pipeline in pipelines) {
                val plugin = pipeline.action as? UrlPlugin ?: continue
                if (type in plugin.urlTypes()) {
                    return plugin.typedUrl(type, resource)
                }
            }
        }
        for (child in children) {
            val result = child.findTypedUrl(type, resource)
            if (result != null) return result
        }
        return null
    }

    fun findNotFoundPipeline(segments: HttpRequestPath): ExchangePipeline? {
        return traverseForNotFoundPipeline(segments, null)
    }

    private fun traverseForNotFoundPipeline(segments: HttpRequestPath, bestSoFar: ExchangePipeline?): ExchangePipeline? {
        val best = notFoundPipeline ?: bestSoFar

        if (segments.empty) {
            return best
        }

        val (segment, remaining) = segments.pop()
        for (child in children) {
            if (child.matcher !is GobblerSegmentMatcher && child.matcher.matches(segment)) {
                return child.traverseForNotFoundPipeline(remaining, best)
            }
        }

        return best
    }

    fun findRegisteredMethods(segments: HttpRequestPath): Set<HttpMethod>? {
        if (segments.empty || matcher.terminal) {
            return if (pipelinesByMethod.isNotEmpty()) pipelinesByMethod.keys else null
        }

        val (segment, remaining) = segments.pop()
        var gobblerChild: RouteTree? = null
        for (child in children) {
            if (child.matcher.matches(segment)) {
                if (child.matcher is GobblerSegmentMatcher) {
                    gobblerChild = child
                } else {
                    val methods = child.findRegisteredMethods(remaining)
                    if (methods != null) return methods
                }
            }
        }

        return gobblerChild?.findRegisteredMethods(remaining)
    }

    fun findPipeline(method: HttpMethod, segments: HttpRequestPath, acceptedTypes: List<String> = emptyList()): ExchangePipeline? {
        if (root.enableRouteCache) {
            val cachedNode = root.literalPathCache[segments.path]
            if (cachedNode != null) {
                val pipelines = cachedNode.pipelinesByMethod[method]
                if (pipelines != null) {
                    return matchByAcceptType(pipelines, acceptedTypes)
                }
                return null
            }
        }

        val result = traverseForPipeline(method, segments, acceptedTypes)

        if (root.enableRouteCache && result != null && result.treeNode.isAllLiteralPath()) {
            val cache = root.literalPathCache
            val maxSize = root.maxCacheSize
            if (maxSize == null || cache.size < maxSize) {
                cache[segments.path] = result.treeNode
            }
        }

        return result
    }

    private fun traverseForPipeline(method: HttpMethod, segments: HttpRequestPath, acceptedTypes: List<String>): ExchangePipeline? {
        if (segments.empty || matcher.terminal) {
            val pipelines = pipelinesByMethod[method]
            if (pipelines != null) {
                return matchByAcceptType(pipelines, acceptedTypes)
            }

            return null
        }

        val (segment, segments) = segments.pop()
        var gobblerChild: RouteTree? = null
        for (child in children) {
            if (child.matcher.matches(segment)) {
                if (child.matcher is GobblerSegmentMatcher) {
                    gobblerChild = child
                } else {
                    val node = child.traverseForPipeline(method, segments, acceptedTypes)
                    if (node != null) {
                        return node
                    }
                }
            }
        }

        if (gobblerChild != null) {
            val node = gobblerChild.traverseForPipeline(method, segments, acceptedTypes)
            if (node != null) {
                return node
            }
        }

        return null
    }

    private fun isAllLiteralPath(): Boolean {
        var node: RouteTree? = this
        while (node != null) {
            val m = node.matcher
            if (m !is LiteralRouteSegmentMatcher && m !is RootTreeSegmentMatcher) {
                return false
            }
            node = node.parent
        }
        return true
    }

    private fun matchByAcceptType(pipelines: List<ExchangePipeline>, acceptedTypes: List<String>): ExchangePipeline? {
        var fallback: ExchangePipeline? = null
        val typedPipelines = mutableListOf<ExchangePipeline>()
        for (pipeline in pipelines) {
            if (pipeline.acceptTypes.isEmpty()) {
                fallback = pipeline
            } else {
                typedPipelines.add(pipeline)
            }
        }

        if (typedPipelines.isNotEmpty() && acceptedTypes.isNotEmpty()) {
            for (acceptedType in acceptedTypes) {
                for (pipeline in typedPipelines) {
                    if (pipeline.acceptTypes.any { AcceptType.matches(acceptedType, it) }) {
                        return pipeline
                    }
                }
            }
        }

        return fallback
    }

    fun findRoutePaths(function: KFunction<*>): List<String> {
        return root.functionsToRoutePaths[function] ?: emptyList()
    }

    fun copy(parent: RouteTree? = null): RouteTree {
        val copy = RouteTree(parent, matcher, ignoreTrailingSlash, enableRouteCache, maxCacheSize)
        copy.root = parent?.root ?: copy
        copy.notFoundPipeline = notFoundPipeline
        for ((method, pipelines) in pipelinesByMethod) {
            copy.pipelinesByMethod[method] = pipelines.toMutableList()
        }
        copy.functionsToRoutePaths.putAll(functionsToRoutePaths)

        for (child in children) {
            copy.children.add(child.copy(copy))
        }

        return copy
    }

    @Synchronized
    fun add(methods: Set<HttpMethod>, pipeline: ExchangePipeline, matchers: MutableList<RouteSegmentMatcher> = pipeline.routeSegmentMatchers.toMutableList()) {
        require(methods.isNotEmpty()) { "methods cannot be empty" }

        if (matchers.isEmpty()) {
            pipeline.treeNode = this
            for (method in methods) {
                val existing = pipelinesByMethod[method]
                if (existing != null) {
                    for (existingPipeline in existing) {
                        if (existingPipeline.acceptTypes.isEmpty() && pipeline.acceptTypes.isEmpty()) {
                            throw IllegalStateException("Duplicate routes mapped for path ${pipeline.routePath} and method: $method")
                        }
                        if (existingPipeline.acceptTypes.isNotEmpty() && pipeline.acceptTypes.isNotEmpty()) {
                            val overlap = existingPipeline.acceptTypes.intersect(pipeline.acceptTypes)
                            if (overlap.isNotEmpty()) {
                                throw IllegalStateException("Duplicate routes mapped for path ${pipeline.routePath}, method: $method, and accept types: $overlap")
                            }
                        }
                    }
                }

                pipelinesByMethod.getOrPut(method) { mutableListOf() }.add(pipeline)
            }

            if (pipeline.action is ExchangeFunction) {
                root.functionsToRoutePaths.getOrPut(pipeline.action.function) { mutableListOf() }.add(routePath)
            }
            return
        }

        val childMatcher = matchers.removeFirst()
        for (child in children) {
            if (child.matcher == childMatcher) {
                child.add(methods, pipeline, matchers)
                return
            }
        }

        val child = RouteTree(this, childMatcher, ignoreTrailingSlash, enableRouteCache, maxCacheSize)
        child.root = root
        children.add(child)
        children.sort()

        child.add(methods, pipeline, matchers)
    }

    @Synchronized
    fun addNotFound(pipeline: ExchangePipeline, matchers: MutableList<RouteSegmentMatcher> = pipeline.routeSegmentMatchers.toMutableList()) {
        if (matchers.isEmpty()) {
            pipeline.treeNode = this
            require(notFoundPipeline == null) { "Duplicate not-found handler for path $routePath" }
            notFoundPipeline = pipeline
            return
        }

        val childMatcher = matchers.removeFirst()
        for (child in children) {
            if (child.matcher == childMatcher) {
                child.addNotFound(pipeline, matchers)
                return
            }
        }

        val child = RouteTree(this, childMatcher, ignoreTrailingSlash, enableRouteCache, maxCacheSize)
        child.root = root
        children.add(child)
        children.sort()
        child.addNotFound(pipeline, matchers)
    }

    override fun toString(): String {
        return routePath
    }

    /**
     * Returns a formatted string showing all registered routes in this tree.
     * Each line shows: METHOD  /path  →  ClassName.functionName
     */
    fun toRouteTableString(): String {
        val lines = mutableListOf<String>()
        collectRoutes(this, lines)

        if (lines.isEmpty()) {
            return "(no routes registered)"
        }

        val maxMethodLen = lines.maxOf { it.substringBefore("  ").length }
        val maxPathLen = lines.maxOf { it.substringAfter("  ").substringBefore("  → ").length }

        return lines.map { line ->
            val parts = line.split("  → ", limit = 2)
            val methodAndPath = parts[0]
            val handler = if (parts.size > 1) parts[1] else ""
            val method = methodAndPath.substringBefore("  ")
            val path = methodAndPath.substringAfter("  ")
            "${method.padEnd(maxMethodLen)}  ${path.padEnd(maxPathLen)}  →  $handler".trimEnd()
        }.joinToString("\n")
    }

    private fun collectRoutes(node: RouteTree, lines: MutableList<String>) {
        for ((method, pipelines) in node.pipelinesByMethod) {
            for (pipeline in pipelines) {
                val path = node.routePath.ifEmpty { "/" }
                val handler = formatHandler(pipeline.action)
                lines.add("${method.name}  $path  → $handler")
            }
        }
        for (child in node.children) {
            collectRoutes(child, lines)
        }
    }

    private fun formatHandler(action: ExchangeAction): String {
        if (action is ExchangeFunction) {
            val func = action.function
            val className = func.javaMethod?.declaringClass?.simpleName ?: ""
            val methodName = func.name
            return if (className.isNotEmpty()) "$className.$methodName" else methodName
        }
        return action::class.simpleName ?: action.toString()
    }

    override fun compareTo(other: RouteTree): Int {
        return matcher.compareTo(other.matcher)
    }
}