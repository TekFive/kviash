package org.tekfive.kviash.exchange.actions.static

import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.exchange.ExchangeAction
import org.tekfive.kviash.http.HttpHeader

/**
 * Adds a `Cache-Control` response header.
 *
 * Use this as a pre-action to set caching policy on routes.
 *
 * ```kotlin
 * RouteTable.register(preActions = listOf(
 *     AddCacheHeader("public, max-age=86400")
 * )) {
 *     add(controller::getStaticData)
 * }
 * ```
 */
class AddCacheHeader(
    val cacheControl: String = "no-cache",
) : ExchangeAction {

    override fun invoke(exchange: Exchange): Any? {
        exchange.response.addHeader(HttpHeader.CacheControl, cacheControl)
        return null
    }
}
