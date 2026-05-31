package org.tekfive.kviash.exchange.actions

import org.tekfive.kviash.DefaultKviashConfiguration
import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.exchange.ExchangeAction
import org.tekfive.kviash.exchange.ExchangePipeline
import org.tekfive.kviash.exchange.actions.adapters.ForwardAdapter
import org.tekfive.kviash.http.*
import org.tekfive.kviash.routing.RoutePath
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Mock implementations
// ---------------------------------------------------------------------------

private class ActionMockRequestSource(
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

private class ActionMockResponseSource : HttpResponseSource {
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

    val bodyBytes: ByteArray get() = _outputStream.toByteArray()
    val bodyText: String get() = _outputStream.toString(Charsets.UTF_8)
}

private class MockForwardAdapter : ForwardAdapter {
    var lastPath: String? = null
    override fun forwardTo(path: String, exchange: Exchange) {
        lastPath = path
    }
}

private fun createExchange(
    actionResult: Any? = null,
    requestSource: ActionMockRequestSource = ActionMockRequestSource(),
    responseSource: ActionMockResponseSource = ActionMockResponseSource(),
): Exchange {
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
    val request = HttpRequest(requestSource, DefaultKviashConfiguration)
    val response = HttpResponse(responseSource, DefaultKviashConfiguration)
    val requestPath = HttpRequestPath(requestSource.path.toPathSegments(true))
    val exchange = Exchange(request, requestPath, response, pipeline)
    exchange._actionResult = actionResult
    return exchange
}

// ---------------------------------------------------------------------------
// ForwardRequest tests
// ---------------------------------------------------------------------------

class ForwardRequestTest {

    @Test
    fun `forwards to action result path`() {
        val adapter = MockForwardAdapter()
        val forward = ForwardRequest(adapter)
        val exchange = createExchange(actionResult = "/views/home.jsp")

        forward.invoke(exchange)

        assertEquals("/views/home.jsp", adapter.lastPath)
    }

    @Test
    fun `prepends root dispatch path`() {
        val adapter = MockForwardAdapter()
        val forward = ForwardRequest(adapter, rootDispatchPath = "/WEB-INF/views")
        val exchange = createExchange(actionResult = "home.jsp")

        forward.invoke(exchange)

        assertEquals("/WEB-INF/views/home.jsp", adapter.lastPath)
    }

    @Test
    fun `does not double-prepend root dispatch path`() {
        val adapter = MockForwardAdapter()
        val forward = ForwardRequest(adapter, rootDispatchPath = "/WEB-INF/views")
        val exchange = createExchange(actionResult = "/WEB-INF/views/home.jsp")

        forward.invoke(exchange)

        assertEquals("/WEB-INF/views/home.jsp", adapter.lastPath)
    }

    @Test
    fun `does not forward when action result is null`() {
        val adapter = MockForwardAdapter()
        val forward = ForwardRequest(adapter)
        val exchange = createExchange(actionResult = null)

        forward.invoke(exchange)

        assertNull(adapter.lastPath)
    }

    @Test
    fun `does not forward when action result is blank`() {
        val adapter = MockForwardAdapter()
        val forward = ForwardRequest(adapter)
        val exchange = createExchange(actionResult = "   ")

        forward.invoke(exchange)

        assertNull(adapter.lastPath)
    }

    @Test
    fun `does not forward when NoForward attribute is set on request`() {
        val adapter = MockForwardAdapter()
        val forward = ForwardRequest(adapter)
        val requestSource = ActionMockRequestSource()
        requestSource.setAttribute(ForwardRequest.NoForward, true)
        val exchange = createExchange(actionResult = "/view.jsp", requestSource = requestSource)

        forward.invoke(exchange)

        assertNull(adapter.lastPath)
    }

    @Test
    fun `extension filter allows matching extension`() {
        val adapter = MockForwardAdapter()
        val forward = ForwardRequest(adapter, extensions = "jsp html")
        val exchange = createExchange(actionResult = "/views/home.jsp")

        forward.invoke(exchange)

        assertEquals("/views/home.jsp", adapter.lastPath)
    }

    @Test
    fun `extension filter blocks non-matching extension`() {
        val adapter = MockForwardAdapter()
        val forward = ForwardRequest(adapter, extensions = "jsp")
        val exchange = createExchange(actionResult = "/views/home.html")

        forward.invoke(exchange)

        assertNull(adapter.lastPath)
    }

    @Test
    fun `null extensions allows any path`() {
        val adapter = MockForwardAdapter()
        val forward = ForwardRequest(adapter, extensions = null)
        val exchange = createExchange(actionResult = "/anything.xyz")

        forward.invoke(exchange)

        assertEquals("/anything.xyz", adapter.lastPath)
    }

    @Test
    fun `root dispatch path normalizes slashes`() {
        val forward = ForwardRequest(MockForwardAdapter(), rootDispatchPath = "WEB-INF/views")
        assertEquals("/WEB-INF/views/", forward.rootDispatchPath)
    }

    @Test
    fun `root dispatch path defaults to slash`() {
        val forward = ForwardRequest(MockForwardAdapter())
        assertEquals("/", forward.rootDispatchPath)
    }

    @Test
    fun `blank root dispatch path defaults to slash`() {
        val forward = ForwardRequest(MockForwardAdapter(), rootDispatchPath = "  ")
        assertEquals("/", forward.rootDispatchPath)
    }

    @Test
    fun `rejects path containing dot-dot traversal`() {
        val adapter = MockForwardAdapter()
        val forward = ForwardRequest(adapter)
        val exchange = createExchange(actionResult = "../../WEB-INF/web.xml")

        forward.invoke(exchange)

        assertNull(adapter.lastPath)
    }

    @Test
    fun `rejects path with dot-dot in middle`() {
        val adapter = MockForwardAdapter()
        val forward = ForwardRequest(adapter, rootDispatchPath = "/WEB-INF/views")
        val exchange = createExchange(actionResult = "/WEB-INF/views/../secrets/passwords.txt")

        forward.invoke(exchange)

        assertNull(adapter.lastPath)
    }

    @Test
    fun `NoForward companion constant`() {
        assertTrue(ForwardRequest.NoForward.contains("NoForward"))
        assertEquals(ForwardRequest.NoForward to true, ForwardRequest.NoForwardAttribute)
    }
}
