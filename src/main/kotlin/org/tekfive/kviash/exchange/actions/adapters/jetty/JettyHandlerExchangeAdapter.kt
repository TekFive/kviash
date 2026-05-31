package org.tekfive.kviash.exchange.actions.adapters.jetty

import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.util.Callback
import org.tekfive.kviash.http.adapters.jetty.JettyRequestAdapter
import org.tekfive.kviash.http.adapters.jetty.JettyResponseAdapter
import org.tekfive.kviash.routing.Router

class JettyHandlerExchangeAdapter(private val routeNames: List<String> = emptyList()) : Handler.Abstract() {

    override fun handle(request: Request, response: Response, callback: Callback): Boolean {
        try {
            val httpRequest = JettyRequestAdapter(request)
            val httpResponse = JettyResponseAdapter(request, response)

            Router.route(httpRequest, httpResponse, routeNames)

            callback.succeeded()
        } catch (e: Exception) {
            callback.failed(e)
        }
        return true
    }
}
