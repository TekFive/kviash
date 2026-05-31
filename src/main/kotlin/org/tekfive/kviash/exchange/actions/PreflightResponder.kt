package org.tekfive.kviash.exchange.actions

import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.exchange.ExchangeAction
import org.tekfive.kviash.http.HttpHeader
import org.tekfive.kviash.http.HttpMethod
import org.tekfive.kviash.http.HttpRequestPath
import org.tekfive.kviash.http.toPathSegments
import org.tekfive.kviash.routing.Router

/**
 * An action that responds to HTTP OPTIONS requests with the methods registered at the request path.
 *
 * Responds with a `204 No Content` status and an `Allow` header listing the HTTP methods registered
 * at the matched path (plus OPTIONS itself). If no methods are found at the path, the `Allow` header
 * is omitted but the 204 response is still sent.
 *
 * Methods are collected across all registered route trees, not just the tree this action belongs to.
 *
 * This responder is typically registered once at the top of the route tree using a gobbler path
 * so that it handles OPTIONS for all routes:
 * ```
 * RouteTable.register("my-routes") {
 *     add("/{**}", HttpMethod.OPTIONS, processor = PreflightResponder)
 *
 *     add("/items", HttpMethod.GET) { ... }
 *     add("/items", HttpMethod.POST) { ... }
 * }
 * ```
 */
object PreflightResponder : ExchangeAction {

    override fun invoke(exchange: Exchange): Any? {
        val methods = mutableSetOf<HttpMethod>()
        val requestPaths = mutableMapOf<Boolean, HttpRequestPath>()

        for ((_, routeTree) in Router.routeTreesByName) {
            val requestPath = requestPaths.getOrPut(routeTree.ignoreTrailingSlash) {
                HttpRequestPath(exchange.request.source.path.toPathSegments(routeTree.ignoreTrailingSlash))
            }
            val treeMethods = routeTree.findRegisteredMethods(requestPath)
            if (treeMethods != null) {
                methods.addAll(treeMethods)
            }
        }

        if (methods.isNotEmpty()) {
            val allowMethods = (methods + HttpMethod.OPTIONS).joinToString(", ") { it.name }
            exchange.response.addHeader(HttpHeader.Allow, allowMethods)
        }

        exchange.response.sendStatus(204)
        return null
    }
}
