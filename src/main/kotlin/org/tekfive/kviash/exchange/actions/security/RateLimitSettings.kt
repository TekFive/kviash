package org.tekfive.kviash.exchange.actions.security

import org.tekfive.kviash.exchange.Exchange

interface RateLimitSettings {
    val maxRequests: Int
    val windowMillis: Long
    val clientKeyExtractor: (Exchange) -> String

    class Default(
        override val maxRequests: Int = 100,
        override val windowMillis: Long = 60_000,
        override val clientKeyExtractor: (Exchange) -> String = { it.request.clientIp },
    ) : RateLimitSettings
}
