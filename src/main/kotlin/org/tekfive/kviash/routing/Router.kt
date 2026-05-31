package org.tekfive.kviash.routing

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.exchange.ExchangePipeline
import org.tekfive.kviash.exchange.HttpErrorCode
import org.tekfive.kviash.exchange.TerminateExchangeException
import org.tekfive.kviash.exchange.isHttpError
import org.tekfive.kviash.http.AcceptType
import org.tekfive.kviash.http.HttpMethod
import org.tekfive.kviash.http.HttpRequest
import org.tekfive.kviash.http.HttpRequestPath
import org.tekfive.kviash.http.HttpRequestSource
import org.tekfive.kviash.http.HttpResponse
import org.tekfive.kviash.http.HttpResponseSource
import org.tekfive.kviash.http.toPathSegments

object Router {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    private val registryLock = Any()
    private val _routeTreesByName = mutableListOf<Pair<String, RouteTree>>()

    internal val routeTreesByName: List<Pair<String, RouteTree>>
        get() = synchronized(registryLock) { _routeTreesByName.toList() }

    /**
     * This is expected to only be used for testing purposes.
     */
    fun clearRegistry() {
        synchronized(registryLock) {
            _routeTreesByName.clear()
        }
    }

    internal fun register(name: String, routeTree: RouteTree) {
        synchronized(registryLock) {
            require(_routeTreesByName.none { it.first.equals(name, true) }) { "A route tree with name: $name is already registered." }
            _routeTreesByName.add(name to routeTree)
        }
    }

    override fun toString(): String {
        val routeTrees = routeTreesByName
        if (routeTrees.isEmpty()) {
            return "Router: (no route tables registered)"
        }
        return routeTrees.joinToString("\n\n") { (name, tree) ->
            "[$name]\n${tree.toRouteTableString()}"
        }
    }

    fun route(requestSource: HttpRequestSource, responseSource: HttpResponseSource, routeTreeNames: List<String> = emptyList()) {
        val registeredRouteTrees = routeTreesByName

        check(registeredRouteTrees.isNotEmpty()) { "No routes have been registered." }

        val method = HttpMethod.fromName(requestSource.method)
        if (method == null) {
            log.warn("Unable to route HTTP request: ${requestSource.path} with unknown HTTP method: ${requestSource.method}")
            responseSource.setStatus(404)
            return
        }

        val routeTrees = if (routeTreeNames.isNotEmpty()) {
            val matchedRouteTrees = mutableListOf<RouteTree>()
            for (routesName in routeTreeNames) {
                val routeTree = registeredRouteTrees.firstOrNull { it.first.equals(routesName, true) }?.second
                if (routeTree == null) {
                    throw IllegalArgumentException("No routes registered with name: $routesName")
                }
                matchedRouteTrees.add(routeTree)
            }
            matchedRouteTrees
        } else {
            registeredRouteTrees.map { it.second }
        }


        val acceptedTypes = AcceptType.parse(
            requestSource.headers.filter { it.first.equals("Accept", true) }.flatMap { it.second }
        )

        var pipeline: ExchangePipeline? = null
        val requestPaths = mutableMapOf<Boolean, HttpRequestPath>()
        for (routeTree in routeTrees) {
            val requestPath = requestPaths.getOrPut(routeTree.ignoreTrailingSlash) { HttpRequestPath(requestSource.path.toPathSegments(routeTree.ignoreTrailingSlash)) }
            pipeline = routeTree.findPipeline(method, requestPath, acceptedTypes)
            if (pipeline != null) {
                break
            }
        }

        if (pipeline != null) {
            val requestPath = requestPaths.getOrPut(pipeline.treeNode.ignoreTrailingSlash) { HttpRequestPath(requestSource.path.toPathSegments(pipeline.treeNode.ignoreTrailingSlash)) }
            val request = HttpRequest(requestSource, pipeline.configuration)
            val response = HttpResponse(responseSource, pipeline.configuration)
            val exchange = Exchange(request, requestPath, response, pipeline)

            try {
                pipeline(exchange)
            } catch (e: TerminateExchangeException) {} catch (e : Exception) {
                pipeline.configuration.exchangeErrorLogger.error(e, exchange)
            }
        } else {
            var notFoundPipeline: ExchangePipeline? = null
            for (routeTree in routeTrees) {
                val requestPath = requestPaths.getOrPut(routeTree.ignoreTrailingSlash) { HttpRequestPath(requestSource.path.toPathSegments(routeTree.ignoreTrailingSlash)) }
                notFoundPipeline = routeTree.findNotFoundPipeline(requestPath)
                if (notFoundPipeline != null) break
            }

            if (notFoundPipeline != null) {
                val requestPath = requestPaths.getOrPut(notFoundPipeline.treeNode.ignoreTrailingSlash) { HttpRequestPath(requestSource.path.toPathSegments(notFoundPipeline.treeNode.ignoreTrailingSlash)) }
                val request = HttpRequest(requestSource, notFoundPipeline.configuration)
                val response = HttpResponse(responseSource, notFoundPipeline.configuration)
                responseSource.setStatus(404)
                val exchange = Exchange(request, requestPath, response, notFoundPipeline)

                try {
                    notFoundPipeline(exchange)
                } catch (e: TerminateExchangeException) {} catch (e: Exception) {
                    notFoundPipeline.configuration.exchangeErrorLogger.error(e, exchange)
                }
            } else {
                responseSource.setStatus(404)
            }
        }
    }
}
