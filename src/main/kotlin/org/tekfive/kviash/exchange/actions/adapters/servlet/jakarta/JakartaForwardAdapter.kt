package org.tekfive.kviash.exchange.actions.adapters.servlet.jakarta

import jakarta.servlet.ServletOutputStream
import jakarta.servlet.WriteListener
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import org.tekfive.kviash.exchange.actions.adapters.ForwardAdapter
import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.http.adapters.servlet.jakarta.JakartaRequestAdapter
import org.tekfive.kviash.http.adapters.servlet.jakarta.JakartaResponseAdapter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter

open class JakartaForwardAdapter() : ForwardAdapter {
    override fun forwardTo(path: String, exchange: Exchange) {
        val servletRequest = (exchange.request.source as JakartaRequestAdapter).servletRequest
        val servletResponse = (exchange.response.source as JakartaResponseAdapter).servletResponse

        servletRequest.getRequestDispatcher(path).forward(servletRequest, servletResponse)
    }
}

