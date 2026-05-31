package org.tekfive.kviash.exchange.actions.adapters.servlet.javax

import org.tekfive.kviash.http.adapters.servlet.javax.JavaxRequestAdapter
import org.tekfive.kviash.http.adapters.servlet.javax.JavaxResponseAdapter
import org.tekfive.kviash.routing.Router
import javax.servlet.ServletConfig
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class JavaxServletExchangeAdapter : HttpServlet() {

    private lateinit var routeNames: List<String>

    override fun init(config: ServletConfig) {
        routeNames = config.getInitParameter("RouteNames")?.let { it.split(",") }?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
    }

    override fun service(request: ServletRequest, response: ServletResponse) {
        val httpRequest = JavaxRequestAdapter(request as HttpServletRequest)
        val httpResponse = JavaxResponseAdapter(response as HttpServletResponse)

        Router.route(httpRequest, httpResponse, routeNames)
    }
}