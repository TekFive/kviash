package org.tekfive.kviash.exchange.interceptors

import org.tekfive.kviash.DefaultKviashConfiguration
import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.exchange.ExchangeAction
import org.tekfive.kviash.exchange.ExchangePipeline
import org.tekfive.kviash.http.*
import org.tekfive.kviash.routing.RoutePath
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer

open class MockRequestSource(
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
    private var session: MockSession? = null
    override fun getSession(createIfNotExists: Boolean): HttpSession? {
        if (session == null && createIfNotExists) session = MockSession()
        return session
    }
}

class MockResponseSource : HttpResponseSource {
    var _status: Int = 200
    private val _headers = mutableListOf<HttpHeader>()
    private var _committed = false
    val _outputStream = ByteArrayOutputStream()
    private val _outputWriter: Writer by lazy { OutputStreamWriter(_outputStream) }

    override val status: Int get() = _status
    override val headers: List<HttpHeader> get() = _headers.toList()
    override val committed: Boolean get() = _committed
    override val outputStream: OutputStream get() = _outputStream
    override val outputWriter: Writer get() = _outputWriter

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
    override fun commit() {
        _outputWriter.flush()
        _committed = true
    }
    override fun createdBufferedResponse(outputBuffer: OutputStream): HttpResponseSource {
        return MockBufferedResponseSource(this, outputBuffer)
    }

    val bodyText: String get() {
        _outputWriter.flush()
        return _outputStream.toString(Charsets.UTF_8)
    }

    fun headerValues(name: String): List<String> = getHeaderValues(name)
}

class MockBufferedResponseSource(
    private val parent: MockResponseSource,
    private val buffer: OutputStream,
) : HttpResponseSource {
    private val _headers = mutableListOf<HttpHeader>()
    private val _outputWriter: Writer by lazy { OutputStreamWriter(buffer) }

    override val status: Int get() = parent.status
    override val headers: List<HttpHeader> get() = _headers.toList()
    override val committed: Boolean get() = false
    override val outputStream: OutputStream get() = buffer
    override val outputWriter: Writer get() = _outputWriter

    override fun addCookie(cookie: ResponseCookie) {}
    override fun addHeader(header: HttpHeader) { _headers.add(header) }
    override fun setStatus(status: Int) { parent.setStatus(status) }
    override fun setHeader(header: HttpHeader) {
        _headers.removeAll { it.name.equals(header.name, true) }
        _headers.add(header)
    }
    override fun getHeaderValues(name: String): List<String> {
        return _headers.filter { it.name.equals(name, true) }.flatMap { it.values }
    }
    override fun commit() {
        _outputWriter.flush()
        buffer.flush()
    }
    override fun createdBufferedResponse(outputBuffer: OutputStream): HttpResponseSource = this
}

class MockSession : HttpSession {
    override val id: String = "mock-session"
    override val isNew: Boolean = true
    override val creationTime: Long = System.currentTimeMillis()
    override val lastAccessedTime: Long = System.currentTimeMillis()
    private val attrs = mutableMapOf<String, Any?>()
    override val attributeNames: List<String> get() = attrs.keys.toList()
    override fun getAttribute(key: String): Any? = attrs[key]
    override fun setAttribute(key: String, value: Any?) { attrs[key] = value }
    override fun removeAttribute(key: String) { attrs.remove(key) }
    override fun invalidate() { attrs.clear() }
}

fun createTestExchange(
    requestSource: MockRequestSource = MockRequestSource(),
    responseSource: MockResponseSource = MockResponseSource(),
): Exchange {
    val routePath = RoutePath(listOf("/test").flatMap { it.toPathSegments(true) })
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
    return Exchange(request, requestPath, response, pipeline)
}
