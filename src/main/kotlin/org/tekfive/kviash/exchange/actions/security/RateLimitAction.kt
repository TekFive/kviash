package org.tekfive.kviash.exchange.actions.security

import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.exchange.ExchangeAction
import java.util.concurrent.ConcurrentHashMap

/**
 * Throttles requests per client using a fixed-window rate limiting algorithm.
 *
 * Use this action as a pre-action to protect routes against abuse, brute-force attacks, or
 * resource exhaustion. It tracks the number of requests each client makes within a time window
 * and returns `429 Too Many Requests` when the limit is exceeded. Rate limit headers
 * (`X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `Retry-After` on rejection) are added
 * to every response so clients can adapt their request rate.
 *
 * The [clientKeyExtractor] determines how clients are identified. It defaults to
 * [HttpRequest.clientIp][org.tekfive.kviash.http.HttpRequest.clientIp]. When trusted proxy
 * CIDRs are configured, `HttpRequest.clientIp` resolves `Forwarded` and `X-Forwarded-For`
 * through that trust boundary; otherwise it uses the direct peer IP.
 *
 * State is held in memory, so limits are per-process and reset on restart. For distributed
 * rate limiting across multiple application instances, use an external store and a custom
 * action.
 *
 * ```kotlin
 * RouteTable.register(preActions = listOf(
 *     RateLimitAction(maxRequests = 60, windowMillis = 60_000)
 * )) {
 *     add(controller::getResource)
 * }
 * ```
 */
class RateLimitAction(
    val settings: RateLimitSettings = RateLimitSettings.Default(),
) : ExchangeAction {

    constructor(
        maxRequests: Int = 100,
        windowMillis: Long = 60_000,
        clientKeyExtractor: (Exchange) -> String = { it.request.clientIp },
    ) : this(RateLimitSettings.Default(maxRequests, windowMillis, clientKeyExtractor))

    internal val clients = ConcurrentHashMap<String, ClientWindow>()

    override fun invoke(exchange: Exchange): Any? {
        val clientKey = settings.clientKeyExtractor(exchange)
        val now = System.currentTimeMillis()
        val window = clients.compute(clientKey) { _, existing ->
            if (existing == null || now - existing.windowStart >= settings.windowMillis) {
                ClientWindow(now, 1)
            } else {
                ClientWindow(existing.windowStart, existing.count + 1)
            }
        }!!

        val remaining = (settings.maxRequests - window.count).coerceAtLeast(0)
        exchange.response.addHeader("X-RateLimit-Limit", settings.maxRequests.toString())
        exchange.response.addHeader("X-RateLimit-Remaining", remaining.toString())

        if (window.count > settings.maxRequests) {
            val retryAfter = ((window.windowStart + settings.windowMillis - now) / 1000).coerceAtLeast(1)
            exchange.response.addHeader("Retry-After", retryAfter.toString())
            exchange.response.sendStatus(429)
        }

        return null
    }

    internal data class ClientWindow(val windowStart: Long, val count: Int)
}
