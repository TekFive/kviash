package org.tekfive.kviash.exchange.actions.adapters.undertow

import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.util.HttpString
import io.undertow.util.Methods
import org.tekfive.kviash.DefaultKviashConfiguration
import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.exchange.ExchangeAction
import org.tekfive.kviash.exchange.ExchangePipeline
import org.tekfive.kviash.http.HttpRequest
import org.tekfive.kviash.http.HttpRequestPath
import org.tekfive.kviash.http.HttpResponse
import org.tekfive.kviash.http.adapters.undertow.UndertowRequestAdapter
import org.tekfive.kviash.http.adapters.undertow.UndertowResponseAdapter
import org.tekfive.kviash.routing.RoutePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class UndertowForwardAdapterTest {

    @Test
    fun `forwards to handler with modified relative path`() {
        var capturedExchange: HttpServerExchange? = null
        val handler = HttpHandler { exchange ->
            capturedExchange = exchange
        }

        val undertowExchange = createUndertowExchange("/original/path")
        val exchange = createExchange(undertowExchange)

        val adapter = UndertowForwardAdapter(handler)
        adapter.forwardTo("/WEB-INF/views/home.jsp", exchange)

        assertEquals("/WEB-INF/views/home.jsp", capturedExchange?.relativePath)
    }

    @Test
    fun `passes same undertow exchange to handler`() {
        var capturedExchange: HttpServerExchange? = null
        val handler = HttpHandler { exchange ->
            capturedExchange = exchange
        }

        val undertowExchange = createUndertowExchange("/original")
        val exchange = createExchange(undertowExchange)

        val adapter = UndertowForwardAdapter(handler)
        adapter.forwardTo("/forwarded", exchange)

        assertSame(undertowExchange, capturedExchange)
    }

    @Test
    fun `preserves request method on forwarded exchange`() {
        var capturedExchange: HttpServerExchange? = null
        val handler = HttpHandler { exchange ->
            capturedExchange = exchange
        }

        val undertowExchange = createUndertowExchange("/original")
        val exchange = createExchange(undertowExchange)

        val adapter = UndertowForwardAdapter(handler)
        adapter.forwardTo("/forwarded", exchange)

        assertEquals("GET", capturedExchange?.requestMethod?.toString())
    }

    // -- helpers --

    private fun createUndertowExchange(path: String): HttpServerExchange {
        val exchange = HttpServerExchange(null)
        exchange.requestMethod = Methods.GET
        exchange.requestPath = path
        exchange.relativePath = path
        exchange.requestHeaders.put(HttpString("Host"), "localhost")
        return exchange
    }

    private fun createExchange(undertowExchange: HttpServerExchange): Exchange {
        val requestAdapter = UndertowRequestAdapter(undertowExchange)
        val responseAdapter = UndertowResponseAdapter(undertowExchange)
        val routePath = RoutePath(listOf("test"))
        val pipeline = ExchangePipeline(
            DefaultKviashConfiguration,
            routePath,
            interceptors = emptyList(),
            preActions = emptyList(),
            action = ExchangeAction { null },
            postActions = emptyList(),
            routeAttributes = emptyMap(),
        )
        val request = HttpRequest(requestAdapter, DefaultKviashConfiguration)
        val response = HttpResponse(responseAdapter, DefaultKviashConfiguration)
        val requestPath = HttpRequestPath(listOf("test"))
        return Exchange(request, requestPath, response, pipeline)
    }
}
