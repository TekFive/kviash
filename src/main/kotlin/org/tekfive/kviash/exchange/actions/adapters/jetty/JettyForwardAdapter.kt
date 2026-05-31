package org.tekfive.kviash.exchange.actions.adapters.jetty

import org.eclipse.jetty.http.HttpURI
import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.util.Callback
import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.exchange.actions.adapters.ForwardAdapter
import org.tekfive.kviash.http.adapters.jetty.JettyRequestAdapter
import org.tekfive.kviash.http.adapters.jetty.JettyResponseAdapter

open class JettyForwardAdapter(private val handler: Handler) : ForwardAdapter {
    override fun forwardTo(path: String, exchange: Exchange) {
        val jettyRequest = (exchange.request.source as JettyRequestAdapter).jettyRequest
        val jettyResponse = (exchange.response.source as JettyResponseAdapter).jettyResponse

        val forwardUri = HttpURI.build(jettyRequest.httpURI).path(path).asImmutable()
        val forwardedRequest = object : Request.Wrapper(jettyRequest) {
            override fun getHttpURI(): HttpURI = forwardUri
        }

        handler.handle(forwardedRequest, jettyResponse, Callback.NOOP)
    }
}
