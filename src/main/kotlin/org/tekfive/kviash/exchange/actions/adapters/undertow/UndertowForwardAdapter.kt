package org.tekfive.kviash.exchange.actions.adapters.undertow

import io.undertow.server.HttpHandler
import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.exchange.actions.adapters.ForwardAdapter
import org.tekfive.kviash.http.adapters.undertow.UndertowRequestAdapter

open class UndertowForwardAdapter(private val handler: HttpHandler) : ForwardAdapter {
    override fun forwardTo(path: String, exchange: Exchange) {
        val undertowExchange = (exchange.request.source as UndertowRequestAdapter).exchange
        undertowExchange.relativePath = path
        handler.handleRequest(undertowExchange)
    }
}
