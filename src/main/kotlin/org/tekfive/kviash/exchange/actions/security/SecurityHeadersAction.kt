package org.tekfive.kviash.exchange.actions.security

import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.exchange.ExchangeAction

/**
 * Adds standard security response headers to protect against common browser-based attacks.
 *
 * Use this action as a pre-action as a baseline security layer on all routes. It sets headers
 * that instruct browsers to prevent MIME-sniffing, clickjacking, and other client-side attacks.
 * Headers with well-defined values use enum parameters for type safety; headers with
 * application-specific values (CSP, HSTS, Permissions-Policy) remain free-form strings.
 *
 * All headers are configurable and can be disabled individually by setting them to `null`
 * or `false`. The defaults provide a reasonable starting point for most applications:
 *
 * - `X-Content-Type-Options: nosniff` — prevents MIME-type sniffing
 * - `X-Frame-Options: DENY` — prevents clickjacking via iframes
 * - `X-XSS-Protection: 0` — disables the legacy XSS auditor (which can introduce vulnerabilities)
 * - `Referrer-Policy: strict-origin-when-cross-origin` — limits referrer leakage
 *
 * ```kotlin
 * RouteTable.register(preActions = listOf(
 *     SecurityHeadersAction(
 *         frameOptions = FrameOption.SAMEORIGIN,
 *         strictTransportSecurity = "max-age=31536000; includeSubDomains",
 *         contentSecurityPolicy = "default-src 'self'",
 *     )
 * )) {
 *     add(controller::getPage)
 * }
 * ```
 */
class SecurityHeadersAction(
    val contentTypeOptions: Boolean = true,
    val frameOptions: FrameOption? = FrameOption.DENY,
    val xssProtection: Boolean = true,
    val referrerPolicy: ReferrerPolicy? = ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN,
    val contentSecurityPolicy: String? = null,
    val strictTransportSecurity: String? = null,
    val permissionsPolicy: String? = null,
    val crossOriginOpenerPolicy: CrossOriginOpenerPolicy? = null,
    val crossOriginResourcePolicy: CrossOriginResourcePolicy? = null,
) : ExchangeAction {

    override fun invoke(exchange: Exchange): Any? {
        val response = exchange.response

        if (contentTypeOptions) {
            response.addHeader("X-Content-Type-Options", "nosniff")
        }
        frameOptions?.let {
            response.addHeader("X-Frame-Options", it.headerValue)
        }
        if (xssProtection) {
            response.addHeader("X-XSS-Protection", "0")
        }
        referrerPolicy?.let {
            response.addHeader("Referrer-Policy", it.headerValue)
        }
        contentSecurityPolicy?.let {
            response.addHeader("Content-Security-Policy", it)
        }
        strictTransportSecurity?.let {
            response.addHeader("Strict-Transport-Security", it)
        }
        permissionsPolicy?.let {
            response.addHeader("Permissions-Policy", it)
        }
        crossOriginOpenerPolicy?.let {
            response.addHeader("Cross-Origin-Opener-Policy", it.headerValue)
        }
        crossOriginResourcePolicy?.let {
            response.addHeader("Cross-Origin-Resource-Policy", it.headerValue)
        }

        return null
    }

    companion object {
        val instance = SecurityHeadersAction()
    }
}
