package org.tekfive.kviash.routing

import org.tekfive.kviash.http.toPath
import org.tekfive.kviash.http.toPathSegments
import org.tekfive.kviash.exchange.RedirectTo
import org.tekfive.kviash.exchange.RedirectType
import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.exchange.ExchangePipeline
import org.tekfive.kviash.exchange.actions.static.KeyedResourceDir
import java.net.URLEncoder
import kotlin.reflect.KFunction

fun KFunction<*>.throwRedirectTo(): Nothing {
    throw RedirectTo(this)
}

fun KFunction<*>.throwRedirectTo(type: RedirectType): Nothing {
    throw RedirectTo(this, type)
}

fun KFunction<*>.toRoutePath(): String {
    val routes = Exchange.getRoutesLocal()
    if (routes == null) {
        throw ThreadLocalContextException()
    }

    return routes.getRoutePath(this)
}

class Routes internal constructor(
    val exchange: Exchange,
) {

    fun getRootUrl(): String {
        return exchange.configuration.getRootUrl(exchange.request)
    }

    fun getUrl(function: KFunction<*>, rootUrl: String, vararg pathValues: Any): String {
        return getUrl(getRoutePath(function), rootUrl, *pathValues)
    }

    fun getUrl(function: KFunction<*>, vararg pathValues: Any): String {
        return getUrl(getRoutePath(function), *pathValues)
    }

    fun getUrl(path: String, vararg pathValues: Any): String {
        return getUrl(path, exchange.configuration.getRootUrl(exchange.request), *pathValues)
    }

    fun getUrlPath(function: KFunction<*>, vararg pathValues: Any): String {
        return getUrlPath(getRoutePath(function), *pathValues)
    }

    fun getUrlPath(path: String, vararg pathValues: Any): String {
        var path = path

        if (pathValues.isNotEmpty()) {
            val indicesSegments = path.toPathSegments(exchange.pipeline.treeNode.ignoreTrailingSlash).toMutableList()
            for (pathValue in pathValues) {
                val nextReplacementIndex = indicesSegments.indexOfFirst {  it.startsWith("{") && it.endsWith("}") }
                if (nextReplacementIndex < 0) {
                    break
                }

                val rawValue = if (pathValue is HasRouteParameter) {
                    pathValue.getParameter()
                } else {
                    pathValue.toString()
                }
                indicesSegments[nextReplacementIndex] = if (indicesSegments[nextReplacementIndex] == "{**}") {
                    rawValue.split('/').joinToString("/") { encodePathSegment(it) }
                } else {
                    encodePathSegment(rawValue)
                }
            }

            path = indicesSegments.toPath()
        }

        return path
    }


    fun getUrl(path: String, rootUrl: String, vararg pathValues: Any): String {
        return rootUrl + getUrlPath(path, *pathValues)
    }

    fun jsUrl(jsPath: String): String {
        return url(KeyedResourceDir.JS.key, jsPath)
    }

    fun cssUrl(jsPath: String): String {
        return url(KeyedResourceDir.CSS.key, jsPath)
    }

    fun imgUrl(jsPath: String): String {
        return url(KeyedResourceDir.IMG.key, jsPath)
    }

    fun fontUrl(jsPath: String): String {
        return url(KeyedResourceDir.FONT.key, jsPath)
    }

    fun url(type: String, resource: String): String {
        return url(type, resource, exchange.configuration.getRootUrl(exchange.request))
    }

    fun url(type: String, resource: String, rootUrl: String): String {
        val path = findTypedUrl(type, resource)
            ?: throw IllegalArgumentException("No UrlPlugin registered for URL type: $type")
        return rootUrl + path
    }

    private fun findTypedUrl(type: String, resource: String): String? {
        val treeNode = exchange.pipeline.treeNodeOrNull
        if (treeNode != null) {
            val result = treeNode.root.findTypedUrl(type, resource)
            if (result != null) return result
        }
        for ((_, routeTree) in Router.routeTreesByName) {
            val result = routeTree.findTypedUrl(type, resource)
            if (result != null) return result
        }
        return null
    }

    fun getRefererUrl(defaultRouteFunction: KFunction<*>): String {
        val referer = exchange.request.referer
        return if (!referer.isNullOrBlank()) {
            referer
        } else {
            getUrl(defaultRouteFunction, exchange.request)
        }
    }

    fun getRoutePath(function: KFunction<*>): String {
        var paths = exchange.pipeline.treeNode.root.findRoutePaths(function)

        if (paths.isEmpty()) {
            for (routeTreeNode in Router.routeTreesByName.map { it.second }) {
                paths = routeTreeNode.findRoutePaths(function)
                if (paths.isNotEmpty()) {
                    break
                }
            }
        }

        return paths.firstOrNull() ?: throw RouteFunctionNotMappedException(function )
    }

    companion object {
        @JvmStatic
        fun get(): Routes {
            return routes
        }

        @JvmStatic
        val routes: Routes
            get() = Exchange.getRoutesLocal()
                ?: throw IllegalStateException("No thread local exchange set.")
    }

}

private fun encodePathSegment(value: String): String {
    return URLEncoder.encode(value, Charsets.UTF_8)
        .replace("+", "%20")
        .replace("%7E", "~", ignoreCase = true)
}
