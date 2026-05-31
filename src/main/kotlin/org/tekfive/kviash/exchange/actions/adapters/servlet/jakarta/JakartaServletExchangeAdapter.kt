package org.tekfive.kviash.exchange.actions.adapters.servlet.jakarta

import jakarta.servlet.ServletConfig
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.tekfive.kviash.http.adapters.servlet.jakarta.JakartaRequestAdapter
import org.tekfive.kviash.http.adapters.servlet.jakarta.JakartaResponseAdapter
import org.tekfive.kviash.routing.RouteTree
import org.tekfive.kviash.routing.Router

class JakartaServletExchangeAdapter : HttpServlet() {

    private lateinit var routeNames: List<String>

    override fun init(config: ServletConfig) {
        routeNames = config.getInitParameter("RouteNames")?.let { it.split(",") }?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
    }

    override fun service(request: ServletRequest, response: ServletResponse) {
        val httpRequest = JakartaRequestAdapter(request as HttpServletRequest)
        val httpResponse = JakartaResponseAdapter(response as HttpServletResponse)

        Router.route(httpRequest, httpResponse, routeNames)
    }
}