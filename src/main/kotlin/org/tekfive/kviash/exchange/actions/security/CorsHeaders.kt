package org.tekfive.kviash.exchange.actions.security

import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.exchange.ExchangeAction
import org.tekfive.kviash.http.HttpHeader
import org.tekfive.kviash.http.HttpMethod

/**
 * Handles Cross-Origin Resource Sharing (CORS) by setting the appropriate response headers
 * and responding to preflight `OPTIONS` requests.
 *
 * Use this action as a pre-action when your application serves requests from browser clients hosted on a
 * different origin than the server (e.g., a single-page application on `app.example.com` calling
 * an API on `api.example.com`). Without CORS headers, browsers block these cross-origin requests.
 *
 * For preflight requests (`OPTIONS` with an `Origin` header), the action responds with
 * `204 No Content` and the negotiated CORS headers, committing the response so the pipeline stops.
 * For simple and actual requests, CORS headers are added and the pipeline continues normally.
 *
 * When [allowCredentials] is `true` and [allowedOrigins] contains `"*"`, the action returns
 * the requesting origin rather than the literal `"*"` wildcard, as required by the CORS specification.
 *
 * ```kotlin
 * RouteTable.register(preActions = listOf(
 *     CorsAction(
 *         allowedOrigins = setOf("https://app.example.com"),
 *         allowCredentials = true,
 *     )
 * )) {
 *     add(controller::getData)
 * }
 * ```
 */
class CorsHeaders(
    val allowedOrigins: Set<String> = setOf("*"),
    val allowedMethods: Set<HttpMethod> = setOf(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH),
    val allowedHeaders: Set<String> = setOf("Content-Type", "Authorization"),
    val exposedHeaders: Set<String> = emptySet(),
    val allowCredentials: Boolean = false,
    val maxAge: Long? = 3600,
) : ExchangeAction {

    override fun invoke(exchange: Exchange): Any? {
        val origin = exchange.request.getFirstHeaderValue(HttpHeader.Origin)

        if (origin != null) {
            val allowedOrigin = resolveAllowedOrigin(origin)
            if (allowedOrigin != null) {
                exchange.response.addHeader("Access-Control-Allow-Origin", allowedOrigin)
                if (allowedOrigin != "*") {
                    exchange.response.addVary(HttpHeader.Origin)
                }

                if (allowCredentials) {
                    exchange.response.addHeader("Access-Control-Allow-Credentials", "true")
                }

                if (exposedHeaders.isNotEmpty()) {
                    exchange.response.addHeader("Access-Control-Expose-Headers", exposedHeaders.joinToString(", "))
                }

                if (exchange.request.method == HttpMethod.OPTIONS) {
                    exchange.response.addVary("Access-Control-Request-Method")
                    exchange.response.addVary("Access-Control-Request-Headers")
                    exchange.response.addHeader("Access-Control-Allow-Methods", allowedMethods.joinToString(", "))
                    exchange.response.addHeader("Access-Control-Allow-Headers", allowedHeaders.joinToString(", "))
                    maxAge?.let {
                        exchange.response.addHeader("Access-Control-Max-Age", it.toString())
                    }
                    exchange.response.sendStatus(204)
                }
            }
        }

        return null
    }

    private fun resolveAllowedOrigin(origin: String): String? {
        if ("*" in allowedOrigins) {
            return if (allowCredentials) origin else "*"
        }
        return if (allowedOrigins.any { it.equals(origin, ignoreCase = true) }) origin else null
    }
}
