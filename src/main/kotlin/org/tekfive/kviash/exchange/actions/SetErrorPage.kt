package org.tekfive.kviash.exchange.actions

import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.exchange.ExchangeAction
import org.tekfive.kviash.exchange.actions.adapters.ForwardAdapter
import org.tekfive.kviash.exchange.isHttpError

/**
 * This should be placed in front of ForwardRequest
 */
class SetErrorPage(
    errorPagePath: String,
    val defaultErrorPage: String = "error.jsp",
    val errorCodesWithPages: List<Int> = listOf(400, 401, 403, 404, 503),
) : ExchangeAction {

    val errorPagePath: String = errorPagePath.trimIndent().removeSuffix("/")

    override fun invoke(exchange: Exchange): Any? {
        val response = exchange.response

        if (response.committed) {
            return null
        }

        if (!response.status.isHttpError) {
            return null
        }

        if (exchange.actionResult != null) {
            return null
        }

        val errorPage = if (errorCodesWithPages.contains(response.status)) {
            "${response.status}.jsp"
        } else {
            defaultErrorPage
        }

        val errorPagePath = "${errorPagePath}/$errorPage"

        exchange._actionResult = errorPagePath
        return null
    }
}