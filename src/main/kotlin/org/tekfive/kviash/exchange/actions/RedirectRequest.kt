package org.tekfive.kviash.exchange.actions

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.tekfive.kviash.isUrl
import org.tekfive.kviash.exchange.RedirectType
import org.tekfive.kviash.exchange.ExchangeAction
import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.exchange.RedirectTo
import kotlin.reflect.KFunction

class RedirectRequest(
    val ignoreResponseSent: Boolean = false,
    val redirectType: RedirectType = RedirectType.SEE_OTHER
) : ExchangeAction {
    private val log: Logger = LoggerFactory.getLogger(RedirectRequest::class.java)

    override fun invoke(exchange: Exchange): Any? {

        if (exchange.response.committed) {
            if (!ignoreResponseSent) {
                log.warn("Unable to check redirect status because response already sent for route: ${exchange.routePath}")
            }
        } else {
            var value = exchange.actionResult
            if (value is RedirectTo) {
                if (value.routeFunction != null) {
                    value = value.routeFunction
                } else {
                    value = value.routePathOrURL
                }
            }

            if (value != null) {
                val redirectUrl = if (value is KFunction<*>) {
                    exchange.routes.getUrl(value)
                } else {
                    val redirectCandidate = value.toString()
                    if (RedirectTo.hasRedirectPrefix(redirectCandidate)) {
                        val urlOrPath = RedirectTo.removeRedirectPrefix(redirectCandidate)
                        if (urlOrPath.isUrl) {
                            urlOrPath
                        } else {
                            exchange.routes.getUrl(urlOrPath)
                        }
                    } else {
                        null
                    }
                }

                if (!redirectUrl.isNullOrBlank()) {
                    exchange.response.sendRedirect(redirectUrl, redirectType.code)
                }
            }
        }
        return null
    }
}