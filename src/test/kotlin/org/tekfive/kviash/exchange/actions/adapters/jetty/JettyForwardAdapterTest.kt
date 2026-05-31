package org.tekfive.kviash.exchange.actions.adapters.jetty

import org.eclipse.jetty.http.HttpFields
import org.eclipse.jetty.http.HttpURI
import org.eclipse.jetty.io.Content
import org.eclipse.jetty.server.*
import org.eclipse.jetty.util.Callback
import org.tekfive.kviash.DefaultKviashConfiguration
import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.exchange.ExchangeAction
import org.tekfive.kviash.exchange.ExchangePipeline
import org.tekfive.kviash.http.HttpRequest
import org.tekfive.kviash.http.HttpRequestPath
import org.tekfive.kviash.http.HttpResponse
import org.tekfive.kviash.http.adapters.jetty.JettyRequestAdapter
import org.tekfive.kviash.http.adapters.jetty.JettyResponseAdapter
import org.tekfive.kviash.routing.RoutePath
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class JettyForwardAdapterTest {

    @Test
    fun `forwards to handler with modified URI path`() {
        var capturedRequest: Request? = null
        var capturedResponse: Response? = null
        val handler = object : Handler.Abstract() {
            override fun handle(request: Request, response: Response, callback: Callback): Boolean {
                capturedRequest = request
                capturedResponse = response
                callback.succeeded()
                return true
            }
        }

        val mockRequest = StubJettyRequest("http://localhost:8080/original/path")
        val mockResponse = StubJettyResponse(mockRequest)
        val exchange = createExchange(mockRequest, mockResponse)

        val adapter = JettyForwardAdapter(handler)
        adapter.forwardTo("/WEB-INF/views/home.jsp", exchange)

        assertEquals("/WEB-INF/views/home.jsp", capturedRequest?.httpURI?.path)
        assertSame(mockResponse, capturedResponse)
    }

    @Test
    fun `preserves original URI host and port`() {
        var capturedRequest: Request? = null
        val handler = object : Handler.Abstract() {
            override fun handle(request: Request, response: Response, callback: Callback): Boolean {
                capturedRequest = request
                callback.succeeded()
                return true
            }
        }

        val mockRequest = StubJettyRequest("http://localhost:8080/original?q=test")
        val mockResponse = StubJettyResponse(mockRequest)
        val exchange = createExchange(mockRequest, mockResponse)

        val adapter = JettyForwardAdapter(handler)
        adapter.forwardTo("/new/path", exchange)

        val uri = capturedRequest!!.httpURI
        assertEquals("/new/path", uri.path)
        assertEquals("localhost", uri.host)
        assertEquals(8080, uri.port)
        assertEquals("http", uri.scheme)
    }

    @Test
    fun `wrapped request delegates to original request`() {
        var capturedRequest: Request? = null
        val handler = object : Handler.Abstract() {
            override fun handle(request: Request, response: Response, callback: Callback): Boolean {
                capturedRequest = request
                callback.succeeded()
                return true
            }
        }

        val mockRequest = StubJettyRequest("http://localhost:8080/original")
        val mockResponse = StubJettyResponse(mockRequest)
        val exchange = createExchange(mockRequest, mockResponse)

        val adapter = JettyForwardAdapter(handler)
        adapter.forwardTo("/forwarded", exchange)

        assertEquals("GET", capturedRequest!!.method)
        assertSame(mockRequest.headers, capturedRequest.headers)
    }

    // -- helpers --

    private fun createExchange(jettyRequest: Request, jettyResponse: Response): Exchange {
        val requestAdapter = JettyRequestAdapter(jettyRequest)
        val responseAdapter = JettyResponseAdapter(jettyRequest, jettyResponse)
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

// ---------------------------------------------------------------------------
// Minimal Jetty stub implementations for testing
// ---------------------------------------------------------------------------

private class StubJettyRequest(uri: String) : Request {
    private val httpUri = HttpURI.from(uri)
    private val attrs = mutableMapOf<String, Any?>()
    private val httpFields = HttpFields.EMPTY

    // Request
    override fun getId(): String = "test-request"
    override fun getComponents(): Components = throw NotImplementedError()
    override fun getConnectionMetaData(): ConnectionMetaData = throw NotImplementedError()
    override fun getMethod(): String = "GET"
    override fun getHttpURI(): HttpURI = httpUri
    override fun getContext(): Context = throw NotImplementedError()
    override fun getHeaders(): HttpFields = httpFields
    override fun getTrailers(): HttpFields? = null
    override fun getBeginNanoTime(): Long = 0
    override fun getHeadersNanoTime(): Long = 0
    override fun isSecure(): Boolean = false
    override fun consumeAvailable(): Boolean = true
    override fun addIdleTimeoutListener(onIdleTimeout: Predicate<TimeoutException>?) {}
    override fun addFailureListener(onFailure: Consumer<Throwable>?) {}
    override fun getSession(create: Boolean): Session? = null
    override fun getTunnelSupport(): TunnelSupport? = null
    override fun addHttpStreamWrapper(p0: Function<HttpStream?, HttpStream?>?) {}

    // Content.Source
    override fun read(): Content.Chunk? = null
    override fun demand(demandCallback: Runnable) {}
    override fun fail(failure: Throwable) {}

    // Attributes
    override fun getAttribute(name: String): Any? = attrs[name]
    override fun getAttributeNameSet(): Set<String> = attrs.keys
    override fun setAttribute(name: String, attribute: Any?): Any? = attrs.put(name, attribute)
    override fun removeAttribute(name: String): Any? = attrs.remove(name)
}

private class StubJettyResponse(private val request: Request) : Response {
    private val httpFields = HttpFields.build()

    // Response
    override fun getRequest(): Request = request
    override fun getStatus(): Int = 200
    override fun setStatus(code: Int) {}
    override fun getHeaders(): HttpFields.Mutable = httpFields
    override fun getTrailersSupplier(): Supplier<HttpFields>? = null
    override fun setTrailersSupplier(trailers: Supplier<HttpFields>?) {}
    override fun isCommitted(): Boolean = false
    override fun hasLastWrite(): Boolean = false
    override fun isCompletedSuccessfully(): Boolean = false
    override fun reset() {}
    override fun writeInterim(status: Int, headers: HttpFields?): CompletableFuture<Void> =
        CompletableFuture.completedFuture(null)

    // Content.Sink
    override fun write(last: Boolean, byteBuffer: ByteBuffer?, callback: Callback) {
        callback.succeeded()
    }
}
