package org.tekfive.kviash.exchange.actions.adapters.servlet.javax

import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.exchange.actions.adapters.ForwardAdapter
import org.tekfive.kviash.http.adapters.servlet.javax.JavaxRequestAdapter
import org.tekfive.kviash.http.adapters.servlet.javax.JavaxResponseAdapter

open class JavaxForwardAdapter() : ForwardAdapter {
    override fun forwardTo(path: String, exchange: Exchange) {
        val servletRequest = (exchange.request.source as JavaxRequestAdapter).servletRequest
        val servletResponse = (exchange.response.source as JavaxResponseAdapter).servletResponse

        servletRequest.getRequestDispatcher(path).forward(servletRequest, servletResponse)
    }
}
