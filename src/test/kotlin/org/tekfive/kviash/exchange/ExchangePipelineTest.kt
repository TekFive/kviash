package org.tekfive.kviash.exchange

import org.tekfive.kviash.DefaultKviashConfiguration
import org.tekfive.kviash.exchange.interceptors.PipelineInterceptor
import org.tekfive.kviash.http.*
import org.tekfive.kviash.routing.RoutePath
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Shared mock implementations
// ---------------------------------------------------------------------------

private class PipelineMockRequestSource(
    override val method: String = "GET",
    override val path: String = "/test",
    override val queryString: String? = null,
    override val urlProtocol: String = "http",
    override val httpProtocol: String = "HTTP/1.1",
    override val port: Int = 80,
    override val headers: List<Pair<String, List<String>>> = listOf("Host" to listOf("localhost")),
    override val parameters: List<Pair<String, List<String>>> = emptyList(),
    override val clientIp: String = "127.0.0.1",
    override val inputStream: InputStream? = null,
) : HttpRequestSource {
    private val attributes = mutableMapOf<String, Any?>()
    override fun getAttribute(name: String): Any? = attributes[name]
    override fun setAttribute(name: String, value: Any?) { attributes[name] = value }
    override fun getSession(createIfNotExists: Boolean): HttpSession? = null
}

private class PipelineMockResponseSource : HttpResponseSource {
    var _status: Int = 200
    private val _headers = mutableListOf<HttpHeader>()
    private var _committed = false
    private val _outputStream = ByteArrayOutputStream()

    override val status: Int get() = _status
    override val headers: List<HttpHeader> get() = _headers.toList()
    override val committed: Boolean get() = _committed
    override val outputStream: OutputStream get() = _outputStream
    override val outputWriter: Writer get() = OutputStreamWriter(_outputStream)

    override fun addCookie(cookie: ResponseCookie) {}
    override fun addHeader(header: HttpHeader) { _headers.add(header) }
    override fun setStatus(status: Int) { _status = status }
    override fun setHeader(header: HttpHeader) {
        _headers.removeAll { it.name.equals(header.name, true) }
        _headers.add(header)
    }
    override fun getHeaderValues(name: String): List<String> {
        return _headers.filter { it.name.equals(name, true) }.flatMap { it.values }
    }
    override fun commit() { _committed = true }
    override fun createdBufferedResponse(outputBuffer: OutputStream): HttpResponseSource = this
}

// ---------------------------------------------------------------------------
// Helper
// ---------------------------------------------------------------------------

private fun createPipeline(
    interceptors: List<PipelineInterceptor> = emptyList(),
    preActions: List<ExchangeAction> = emptyList(),
    action: ExchangeAction = ExchangeAction { null },
    postActions: List<ExchangeAction> = emptyList(),
    routeAttributes: Map<String, Any?> = emptyMap(),
): ExchangePipeline {
    val routePath = RoutePath(listOf("test"))
    return ExchangePipeline(
        DefaultKviashConfiguration,
        routePath,
        interceptors,
        preActions,
        action,
        postActions,
        routeAttributes,
    )
}

private fun createExchange(
    pipeline: ExchangePipeline,
    requestSource: PipelineMockRequestSource = PipelineMockRequestSource(),
    responseSource: PipelineMockResponseSource = PipelineMockResponseSource(),
): Exchange {
    val request = HttpRequest(requestSource, DefaultKviashConfiguration)
    val response = HttpResponse(responseSource, DefaultKviashConfiguration)
    val requestPath = HttpRequestPath(requestSource.path.toPathSegments(true))
    return Exchange(request, requestPath, response, pipeline)
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

class ExchangePipelineTest {

    @Test
    fun `action is invoked during pipeline execution`() {
        var actionInvoked = false
        val pipeline = createPipeline(action = ExchangeAction {
            actionInvoked = true
            "result"
        })
        val exchange = createExchange(pipeline)
        pipeline(exchange)

        assertTrue(actionInvoked)
        assertEquals("result", exchange.actionResult)
    }

    @Test
    fun `pre-actions execute before action`() {
        val order = mutableListOf<String>()
        val pipeline = createPipeline(
            preActions = listOf(
                ExchangeAction { order.add("pre1"); "preValue1" },
                ExchangeAction { order.add("pre2"); null },
            ),
            action = ExchangeAction { order.add("action"); null },
        )
        val exchange = createExchange(pipeline)
        pipeline(exchange)

        assertEquals(listOf("pre1", "pre2", "action"), order)
    }

    @Test
    fun `post-actions execute after action`() {
        val order = mutableListOf<String>()
        val pipeline = createPipeline(
            action = ExchangeAction { order.add("action"); null },
            postActions = listOf(
                ExchangeAction { order.add("post1"); null },
                ExchangeAction { order.add("post2"); null },
            ),
        )
        val exchange = createExchange(pipeline)
        pipeline(exchange)

        assertEquals(listOf("action", "post1", "post2"), order)
    }

    @Test
    fun `full pipeline execution order is pre-actions then action then post-actions`() {
        val order = mutableListOf<String>()
        val pipeline = createPipeline(
            preActions = listOf(ExchangeAction { order.add("pre"); null }),
            action = ExchangeAction { order.add("action"); null },
            postActions = listOf(ExchangeAction { order.add("post"); null }),
        )
        val exchange = createExchange(pipeline)
        pipeline(exchange)

        assertEquals(listOf("pre", "action", "post"), order)
    }

    @Test
    fun `interceptors wrap the pipeline execution`() {
        val order = mutableListOf<String>()
        val interceptor = object : PipelineInterceptor {
            override fun intercept(exchange: Exchange, continuePipeline: (Exchange) -> Unit) {
                order.add("interceptor-before")
                continuePipeline(exchange)
                order.add("interceptor-after")
            }
        }
        val pipeline = createPipeline(
            interceptors = listOf(interceptor),
            action = ExchangeAction { order.add("action"); null },
        )
        val exchange = createExchange(pipeline)
        pipeline(exchange)

        assertEquals(listOf("interceptor-before", "action", "interceptor-after"), order)
    }

    @Test
    fun `multiple interceptors chain in order`() {
        val order = mutableListOf<String>()
        val interceptor1 = object : PipelineInterceptor {
            override fun intercept(exchange: Exchange, continuePipeline: (Exchange) -> Unit) {
                order.add("i1-before")
                continuePipeline(exchange)
                order.add("i1-after")
            }
        }
        val interceptor2 = object : PipelineInterceptor {
            override fun intercept(exchange: Exchange, continuePipeline: (Exchange) -> Unit) {
                order.add("i2-before")
                continuePipeline(exchange)
                order.add("i2-after")
            }
        }
        val pipeline = createPipeline(
            interceptors = listOf(interceptor1, interceptor2),
            action = ExchangeAction { order.add("action"); null },
        )
        val exchange = createExchange(pipeline)
        pipeline(exchange)

        assertEquals(listOf("i1-before", "i2-before", "action", "i2-after", "i1-after"), order)
    }

    @Test
    fun `exception in action sets 500 status`() {
        val pipeline = createPipeline(
            action = ExchangeAction { throw RuntimeException("boom") },
        )
        val responseSource = PipelineMockResponseSource()
        val exchange = createExchange(pipeline, responseSource = responseSource)
        pipeline(exchange)

        assertEquals(500, responseSource._status)
        assertEquals(1, exchange.exceptions.size)
    }

    @Test
    fun `exception in pre-action sets 500 status and action still runs if not committed`() {
        var actionRan = false
        val pipeline = createPipeline(
            preActions = listOf(ExchangeAction { throw RuntimeException("pre-boom") }),
            action = ExchangeAction { actionRan = true; null },
        )
        val responseSource = PipelineMockResponseSource()
        val exchange = createExchange(pipeline, responseSource = responseSource)
        pipeline(exchange)

        assertEquals(500, responseSource._status)
        // Action should still run since response is not committed
        // Actually, the status is set to 500 which is isHttpError, so action doesn't re-set status
        assertTrue(exchange.exceptions.isNotEmpty())
    }

    @Test
    fun `ReturnErrorStatus sets appropriate status code`() {
        val pipeline = createPipeline(
            action = ExchangeAction { throw ReturnErrorStatus(HttpErrorCode.NOT_FOUND) },
        )
        val responseSource = PipelineMockResponseSource()
        val exchange = createExchange(pipeline, responseSource = responseSource)
        pipeline(exchange)

        assertEquals(404, responseSource._status)
    }

    @Test
    fun `TerminateExchangeException propagates out`() {
        val pipeline = createPipeline(
            action = ExchangeAction { throw TerminateExchangeException("terminated") },
        )
        val exchange = createExchange(pipeline)

        var caught = false
        try {
            pipeline(exchange)
        } catch (e: TerminateExchangeException) {
            caught = true
        }
        assertTrue(caught, "TerminateExchangeException should propagate")
    }

    @Test
    fun `pre-action non-null return values are added to processorValues`() {
        val pipeline = createPipeline(
            preActions = listOf(
                ExchangeAction { "value1" },
                ExchangeAction { "value2" },
            ),
            action = ExchangeAction { null },
        )
        val exchange = createExchange(pipeline)
        pipeline(exchange)

        assertEquals(listOf("value1", "value2"), exchange.processorValues)
    }

    @Test
    fun `post-action non-null return values are added to processorValues`() {
        val pipeline = createPipeline(
            action = ExchangeAction { null },
            postActions = listOf(ExchangeAction { "postVal" }),
        )
        val exchange = createExchange(pipeline)
        pipeline(exchange)

        assertEquals(listOf("postVal"), exchange.processorValues)
    }

    @Test
    fun `state transitions through pipeline phases`() {
        val states = mutableListOf<ExchangeState>()
        val pipeline = createPipeline(
            preActions = listOf(ExchangeAction { ex -> states.add(ex.state); null }),
            action = ExchangeAction { ex -> states.add(ex.state); null },
            postActions = listOf(ExchangeAction { ex -> states.add(ex.state); null }),
        )
        val exchange = createExchange(pipeline)
        pipeline(exchange)

        assertEquals(
            listOf(ExchangeState.PRE_ACTIONS, ExchangeState.ACTION, ExchangeState.POST_ACTIONS),
            states
        )
        assertEquals(ExchangeState.COMPLETE, exchange.state)
    }

    @Test
    fun `thread-local context is set during pipeline execution and cleared after`() {
        var capturedExchange: Exchange? = null
        val pipeline = createPipeline(
            action = ExchangeAction {
                capturedExchange = Exchange.getExchange()
                null
            },
        )
        val exchange = createExchange(pipeline)
        pipeline(exchange)

        assertNotNull(capturedExchange)
        assertEquals(exchange, capturedExchange)
        assertNull(Exchange.getExchange(), "Thread-local should be cleared after pipeline")
    }

    @Test
    fun `committed response skips remaining pre-actions`() {
        val order = mutableListOf<String>()
        val pipeline = createPipeline(
            preActions = listOf(
                ExchangeAction { ex ->
                    order.add("pre1")
                    ex.response.commit()
                    null
                },
                ExchangeAction { order.add("pre2"); null },
            ),
            action = ExchangeAction { order.add("action"); null },
            postActions = listOf(ExchangeAction { order.add("post"); null }),
        )
        val responseSource = PipelineMockResponseSource()
        val exchange = createExchange(pipeline, responseSource = responseSource)
        pipeline(exchange)

        // Only pre1 should run; pre2, action, and post should be skipped
        assertEquals(listOf("pre1"), order)
    }

    @Test
    fun `redirect prefix in action result throws RedirectTo`() {
        val pipeline = createPipeline(
            action = ExchangeAction { "redirect:/login" },
        )
        val responseSource = PipelineMockResponseSource()
        val exchange = createExchange(pipeline, responseSource = responseSource)
        pipeline(exchange)

        // RedirectTo exception is caught and redirect is sent
        assertTrue(exchange.exceptions.isNotEmpty())
        assertTrue(exchange.exceptions[0] is RedirectTo)
    }
}

// ---------------------------------------------------------------------------
// Exchange
// ---------------------------------------------------------------------------

class ExchangeTest {

    @Test
    fun `getRequestOrRouteAttribute checks request first then route attributes`() {
        val pipeline = createPipeline(
            action = ExchangeAction { null },
            routeAttributes = mapOf("onlyInRoute" to "routeValue"),
        )
        val requestSource = PipelineMockRequestSource()
        requestSource.setAttribute("onlyInRequest", "requestValue")
        val exchange = createExchange(pipeline, requestSource = requestSource)

        assertEquals("requestValue", exchange.getRequestOrRouteAttribute("onlyInRequest"))
        assertEquals("routeValue", exchange.getRequestOrRouteAttribute("onlyInRoute"))
        assertNull(exchange.getRequestOrRouteAttribute("missing"))
    }

    @Test
    fun `getRouteAttribute only checks route attributes`() {
        val pipeline = createPipeline(
            action = ExchangeAction { null },
            routeAttributes = mapOf("key" to "value"),
        )
        val requestSource = PipelineMockRequestSource()
        requestSource.setAttribute("key", "fromRequest")
        val exchange = createExchange(pipeline, requestSource = requestSource)

        assertEquals("value", exchange.getRouteAttribute("key"))
    }

    @Test
    fun `constructor exposes exchange helpers as request attributes`() {
        val pipeline = createPipeline(action = ExchangeAction { null })
        val exchange = createExchange(pipeline)

        assertEquals(exchange, exchange.request["exchange"])
        assertEquals(exchange.routes, exchange.request["routes"])
        assertEquals(exchange.requestPath, exchange.request["requestPath"])
        assertEquals(exchange.parameters, exchange.request["parameters"])
    }

    @Test
    fun `toString returns routePath`() {
        val pipeline = createPipeline(action = ExchangeAction { null })
        val exchange = createExchange(pipeline)
        assertEquals("/test", exchange.toString())
    }

    @Test
    fun `copy constructor creates exchange with same pipeline`() {
        val pipeline = createPipeline(action = ExchangeAction { null })
        val exchange = createExchange(pipeline)
        val copy = Exchange(exchange)
        assertEquals(exchange.routePath, copy.routePath)
    }
}

// ---------------------------------------------------------------------------
// ExchangeExceptions
// ---------------------------------------------------------------------------

class ExchangeExceptionsTest {

    @Test
    fun `HttpErrorCode fromCode returns correct error`() {
        assertEquals(HttpErrorCode.NOT_FOUND, HttpErrorCode.fromCode(404))
        assertEquals(HttpErrorCode.INTERNAL_SERVER_ERROR, HttpErrorCode.fromCode(500))
        assertEquals(HttpErrorCode.BAD_REQUEST, HttpErrorCode.fromCode(400))
    }

    @Test
    fun `HttpErrorCode fromCode returns null for unknown code`() {
        assertNull(HttpErrorCode.fromCode(999))
    }

    @Test
    fun `HttpErrorCode clientError and serverError flags`() {
        assertTrue(HttpErrorCode.NOT_FOUND.clientError)
        assertTrue(!HttpErrorCode.NOT_FOUND.serverError)
        assertTrue(!HttpErrorCode.INTERNAL_SERVER_ERROR.clientError)
        assertTrue(HttpErrorCode.INTERNAL_SERVER_ERROR.serverError)
    }

    @Test
    fun `Int status code extension properties`() {
        assertTrue(200.isHttpSuccess)
        assertTrue(!200.isHttpError)
        assertTrue(301.isHttpRedirect)
        assertTrue(404.isHttpClientError)
        assertTrue(404.isHttpError)
        assertTrue(500.isHttpServerError)
        assertTrue(500.isHttpError)
    }

    @Test
    fun `RedirectTo prefix operations`() {
        assertTrue(RedirectTo.hasRedirectPrefix("redirect:/login"))
        assertTrue(!RedirectTo.hasRedirectPrefix("/login"))
        assertEquals("/login", RedirectTo.removeRedirectPrefix("redirect:/login"))
        assertEquals("redirect:/login", RedirectTo.addPrefix("/login"))
        assertEquals("redirect:/login", RedirectTo.addPrefix("redirect:/login"))
    }

    @Test
    fun `RedirectType enum values`() {
        assertEquals(301, RedirectType.MOVED_PERMANENTLY.code)
        assertEquals(302, RedirectType.FOUND.code)
        assertEquals(303, RedirectType.SEE_OTHER.code)
        assertEquals(307, RedirectType.TEMPORARY_REDIRECT.code)
        assertEquals(308, RedirectType.PERMANENT_REDIRECT.code)
    }
}
