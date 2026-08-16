package org.tekfive.kviash.exchange.actions.security

import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.exchange.ExchangeAction
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicLong

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
 * action. At most [maxTrackedClients] active client windows are retained; additional new
 * clients receive a `429` response until an existing window expires.
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
    val maxTrackedClients: Int = 10_000,
) : ExchangeAction {

    constructor(
        maxRequests: Int = 100,
        windowMillis: Long = 60_000,
        clientKeyExtractor: (Exchange) -> String = { it.request.clientIp },
        maxTrackedClients: Int = 10_000,
    ) : this(RateLimitSettings.Default(maxRequests, windowMillis, clientKeyExtractor), maxTrackedClients)

    internal val clients = ConcurrentHashMap<String, ClientWindow>()
    private val clientPermits = Semaphore(maxTrackedClients)
    private val lastCleanupAt = AtomicLong(0)

    init {
        require(settings.maxRequests > 0) { "maxRequests must be greater than zero" }
        require(settings.windowMillis > 0) { "windowMillis must be greater than zero" }
        require(maxTrackedClients > 0) { "maxTrackedClients must be greater than zero" }
    }

    override fun invoke(exchange: Exchange): Any? {
        val clientKey = settings.clientKeyExtractor(exchange)
        val now = System.currentTimeMillis()
        cleanupExpiredClients(now)

        var capacityExceeded = false
        val window = clients.compute(clientKey) { _, existing ->
            if (existing == null || now - existing.windowStart >= settings.windowMillis) {
                if (existing == null && !clientPermits.tryAcquire()) {
                    capacityExceeded = true
                    null
                } else {
                    ClientWindow(now, 1)
                }
            } else {
                ClientWindow(existing.windowStart, existing.count + 1)
            }
        }

        if (capacityExceeded || window == null) {
            exchange.response.addHeader("X-RateLimit-Limit", settings.maxRequests.toString())
            exchange.response.addHeader("X-RateLimit-Remaining", "0")
            exchange.response.addHeader("Retry-After", ((settings.windowMillis + 999) / 1000).toString())
            exchange.response.sendStatus(429)
            return null
        }

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

    private fun cleanupExpiredClients(now: Long) {
        val cleanupInterval = if (clientPermits.availablePermits() == 0) {
            settings.windowMillis.coerceAtMost(1_000)
        } else {
            settings.windowMillis.coerceAtMost(60_000)
        }
        val previousCleanup = lastCleanupAt.get()
        if (now - previousCleanup < cleanupInterval) {
            return
        }
        if (!lastCleanupAt.compareAndSet(previousCleanup, now)) {
            return
        }

        for ((key, window) in clients) {
            if (now - window.windowStart >= settings.windowMillis && clients.remove(key, window)) {
                clientPermits.release()
            }
        }
    }

    internal data class ClientWindow(val windowStart: Long, val count: Int)
}
