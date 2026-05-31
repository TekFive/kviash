package org.tekfive.kviash.exchange.actions.security

import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.exchange.ExchangeAction
import org.tekfive.kviash.http.HttpHeader
import java.util.Base64

/**
 * Enforces HTTP Basic authentication by decoding the `Authorization` header and validating
 * credentials via a configurable callback.
 *
 * Use this action as a pre-action to protect routes that require simple username/password
 * authentication, such as internal system panels, monitoring endpoints, or development APIs.
 * Basic auth is straightforward to implement in any HTTP client and requires no session state,
 * making it well-suited for machine-to-machine communication or low-ceremony access control.
 *
 * When valid credentials are provided, the authenticated username is stored as a request
 * attribute ([AuthenticatedUserAttribute]) and the pipeline continues. When credentials are
 * missing, malformed, or rejected by the [authenticate] callback, the action responds
 * with `401 Unauthorized` and a `WWW-Authenticate` header that causes browsers to display
 * their built-in login prompt.
 *
 * Basic auth transmits credentials as Base64-encoded plaintext. Always use HTTPS in production
 * to protect credentials in transit.
 *
 * ```kotlin
 * RouteTable.register(preActions = listOf(
 *     BasicAuthAction(realm = "Admin") { user, pass ->
 *         user == "system" && pass == expectedPassword
 *     }
 * )) {
 *     add(controller::getAdminDashboard)
 * }
 * ```
 */
class BasicAuthAction(
    val realm: String = "Restricted",
    val authenticate: (username: String, password: String) -> Boolean,
) : ExchangeAction {

    override fun invoke(exchange: Exchange): Any? {
        val authHeader = exchange.request.getFirstHeaderValue(HttpHeader.Authorization)

        if (authHeader != null && authHeader.startsWith("Basic ", ignoreCase = true)) {
            val credentials = decodeCredentials(authHeader.substring(6))
            if (credentials != null && authenticate(credentials.first, credentials.second)) {
                exchange.request[AuthenticatedUserAttribute] = credentials.first
                return null
            }
        }

        exchange.response.addHeader(HttpHeader.WWWAuthenticate, "Basic realm=\"$realm\"")
        exchange.response.sendStatus(401)
        return null
    }

    companion object {
        const val AuthenticatedUserAttribute = "BasicAuthAction:username"

        internal fun decodeCredentials(encoded: String): Pair<String, String>? {
            return try {
                val decoded = String(Base64.getDecoder().decode(encoded))
                val colon = decoded.indexOf(':')
                if (colon < 0) null
                else decoded.substring(0, colon) to decoded.substring(colon + 1)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
