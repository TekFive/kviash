package org.tekfive.kviash.exchange.actions.adapters.undertow

import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import org.tekfive.kviash.http.adapters.undertow.UndertowRequestAdapter
import org.tekfive.kviash.http.adapters.undertow.UndertowResponseAdapter
import org.tekfive.kviash.routing.Router

class UndertowHandlerExchangeAdapter(private val routeNames: List<String> = emptyList()) : HttpHandler {

    override fun handleRequest(exchange: HttpServerExchange) {
        if (exchange.isInIoThread) {
            exchange.dispatch(this)
            return
        }

        exchange.startBlocking()

        val httpRequest = UndertowRequestAdapter(exchange)
        val httpResponse = UndertowResponseAdapter(exchange)

        Router.route(httpRequest, httpResponse, routeNames)
    }
}
