package org.tekfive.kviash.exchange.actions.security

import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.exchange.ExchangeAction
import org.tekfive.kviash.http.HttpHeader
import org.tekfive.kviash.http.HttpMethod
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Protects against Cross-Site Request Forgery (CSRF) attacks using the synchronizer token pattern.
 *
 * Use this action as a pre-action on routes that handle state-changing operations triggered by
 * browser forms or AJAX calls. CSRF attacks trick a user's browser into submitting a forged
 * request to your application using the user's existing session cookies. This action prevents
 * that by requiring a per-session token that an attacker cannot predict.
 *
 * On the first request, a cryptographically random token is generated and stored in the user's
 * session. The token is also set as a request attribute ([CsrfTokenAttribute]) so that view
 * templates can embed it in forms or pass it to JavaScript for AJAX headers.
 *
 * For state-changing methods (anything not in [safeMethods], which defaults to GET, HEAD, and
 * OPTIONS), the action validates that the request includes the correct token either as an
 * HTTP header ([tokenHeaderName], defaults to `X-CSRF-Token`) or as a form parameter
 * ([tokenParameterName], defaults to `_csrf`). Requests with a missing or incorrect token
 * receive a `403 Forbidden` response.
 *
 * ```kotlin
 * RouteTable.register(preActions = listOf(CsrfAction())) {
 *     add(controller::getForm)     // GET — token set as attribute for the form
 *     add(controller::postForm)    // POST — token validated
 * }
 * ```
 */
class CsrfAction(
    val tokenSessionKey: String = "csrf_token",
    val tokenHeaderName: String = HttpHeader.XCSRFToken,
    val tokenParameterName: String = "_csrf",
    val safeMethods: Set<HttpMethod> = setOf(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS),
) : ExchangeAction {

    override fun invoke(exchange: Exchange): Any? {
        val session = exchange.request.getSession(true) ?: run {
            exchange.response.sendStatus(500)
            return null
        }

        var token = session.getAttribute(tokenSessionKey) as? String
        if (token == null) {
            token = generateToken()
            session.setAttribute(tokenSessionKey, token)
        }

        exchange.request[CsrfTokenAttribute] = token

        val method = exchange.request.method
        if (method != null && method !in safeMethods) {
            val requestToken = exchange.request.getFirstHeaderValue(tokenHeaderName)
                ?: exchange.request.source.parameters
                    .firstOrNull { it.first == tokenParameterName }
                    ?.second?.firstOrNull()

            if (requestToken == null || !MessageDigest.isEqual(requestToken.toByteArray(), token.toByteArray())) {
                exchange.response.sendStatus(403)
                return null
            }
        }

        return null
    }

    companion object {
        const val CsrfTokenAttribute = "CsrfAction:token"

        private val secureRandom = SecureRandom()

        internal fun generateToken(): String {
            val bytes = ByteArray(32)
            secureRandom.nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}
