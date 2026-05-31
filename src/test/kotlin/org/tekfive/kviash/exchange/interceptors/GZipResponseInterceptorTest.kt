package org.tekfive.kviash.exchange.interceptors

import org.tekfive.kviash.DefaultKviashConfiguration
import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.exchange.ExchangeAction
import org.tekfive.kviash.exchange.ExchangePipeline
import org.tekfive.kviash.http.*
import org.tekfive.kviash.routing.RoutePath
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Mock implementations
// ---------------------------------------------------------------------------

private class GzipMockRequestSource(
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

private class GzipMockResponseSource : HttpResponseSource {
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
    override fun commit() { _committed = true }

    override fun createdBufferedResponse(outputBuffer: OutputStream): HttpResponseSource {
        return GzipBufferedResponseSource(this, outputBuffer)
    }

    val rawBytes: ByteArray get() {
        _outputWriter.flush()
        return _outputStream.toByteArray()
    }
}

private class GzipBufferedResponseSource(
    private val parent: GzipMockResponseSource,
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

// ---------------------------------------------------------------------------
// Helper
// ---------------------------------------------------------------------------

private val noOpAction: ExchangeAction = { null }

private fun createExchange(
    responseSource: GzipMockResponseSource = GzipMockResponseSource(),
    requestHeaders: List<Pair<String, List<String>>> = listOf("Host" to listOf("localhost")),
): Exchange {
    val requestSource = GzipMockRequestSource(headers = requestHeaders)
    val routePath = RoutePath(listOf("/test").flatMap { it.toPathSegments(true) })
    val pipeline = ExchangePipeline(
        DefaultKviashConfiguration,
        routePath,
        interceptors = emptyList(),
        preActions = emptyList(),
        action = noOpAction,
        postActions = emptyList(),
        routeAttributes = emptyMap(),
    )
    val request = HttpRequest(requestSource, DefaultKviashConfiguration)
    val response = HttpResponse(responseSource, DefaultKviashConfiguration)
    val requestPath = HttpRequestPath(requestSource.path.toPathSegments(true))
    return Exchange(request, requestPath, response, pipeline)
}

private fun decompressGzip(bytes: ByteArray): String {
    return GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader().readText()
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

class GZipResponseInterceptorTest {

    @Test
    fun `adds Content-Encoding gzip header`() {
        val responseSource = GzipMockResponseSource()
        val exchange = createExchange(responseSource)

        GZipResponseInterceptor().intercept(exchange) { ex ->
            ex.response.outputStream.write("Hello".toByteArray())
        }

        val contentEncoding = responseSource.headers
            .filter { it.name.equals("Content-Encoding", true) }
            .flatMap { it.values }
        assertTrue(contentEncoding.contains("gzip"))
    }

    @Test
    fun `output is valid gzip compressed data`() {
        val responseSource = GzipMockResponseSource()
        val exchange = createExchange(responseSource)

        val originalText = "Hello, compressed world!"
        GZipResponseInterceptor().intercept(exchange) { ex ->
            ex.response.outputStream.write(originalText.toByteArray())
        }

        val compressed = responseSource.rawBytes
        assertTrue(compressed.isNotEmpty())
        val decompressed = decompressGzip(compressed)
        assertEquals(originalText, decompressed)
    }

    @Test
    fun `compressed output is smaller than original for large content`() {
        val responseSource = GzipMockResponseSource()
        val exchange = createExchange(responseSource)

        val originalText = "This is a repeated sentence for compression testing. ".repeat(100)
        GZipResponseInterceptor().intercept(exchange) { ex ->
            ex.response.outputStream.write(originalText.toByteArray())
        }

        val compressed = responseSource.rawBytes
        assertTrue(compressed.size < originalText.toByteArray().size, "Compressed size (${compressed.size}) should be smaller than original (${originalText.toByteArray().size})")
    }

    @Test
    fun `handles empty body`() {
        val responseSource = GzipMockResponseSource()
        val exchange = createExchange(responseSource)

        GZipResponseInterceptor().intercept(exchange) { _ ->
            // write nothing
        }

        val compressed = responseSource.rawBytes
        // Should still produce valid gzip (empty payload)
        val decompressed = decompressGzip(compressed)
        assertEquals("", decompressed)
    }

    @Test
    fun `handles binary content`() {
        val responseSource = GzipMockResponseSource()
        val exchange = createExchange(responseSource)

        val binaryData = ByteArray(256) { it.toByte() }
        GZipResponseInterceptor().intercept(exchange) { ex ->
            ex.response.outputStream.write(binaryData)
        }

        val compressed = responseSource.rawBytes
        val decompressed = GZIPInputStream(ByteArrayInputStream(compressed)).readBytes()
        assertTrue(binaryData.contentEquals(decompressed))
    }

    @Test
    fun `pipeline receives different exchange than original`() {
        val responseSource = GzipMockResponseSource()
        val exchange = createExchange(responseSource)

        var innerExchange: Exchange? = null
        GZipResponseInterceptor().intercept(exchange) { ex ->
            innerExchange = ex
        }

        assertNotNull(innerExchange)
        assertTrue(innerExchange !== exchange)
    }

    @Test
    fun `pipeline writes go through gzip, not directly to original stream`() {
        val responseSource = GzipMockResponseSource()
        val exchange = createExchange(responseSource)

        val text = "Test content"
        GZipResponseInterceptor().intercept(exchange) { ex ->
            ex.response.outputStream.write(text.toByteArray())
        }

        val rawOutput = responseSource.rawBytes
        // The raw bytes should NOT equal the plain text (they're gzip compressed)
        val plainBytes = text.toByteArray()
        assertTrue(!rawOutput.contentEquals(plainBytes), "Output should be gzip-compressed, not plain text")
    }

    @Test
    fun `writing via outputWriter produces valid compressed output`() {
        val responseSource = GzipMockResponseSource()
        val exchange = createExchange(responseSource)

        val text = "Writer-based content"
        GZipResponseInterceptor().intercept(exchange) { ex ->
            val writer = ex.response.outputWriter
            writer.write(text)
            writer.flush()
        }

        val decompressed = decompressGzip(responseSource.rawBytes)
        assertEquals(text, decompressed)
    }

    @Test
    fun `multiple writes are combined in compressed output`() {
        val responseSource = GzipMockResponseSource()
        val exchange = createExchange(responseSource)

        GZipResponseInterceptor().intercept(exchange) { ex ->
            ex.response.outputStream.write("Part 1. ".toByteArray())
            ex.response.outputStream.write("Part 2. ".toByteArray())
            ex.response.outputStream.write("Part 3.".toByteArray())
        }

        val decompressed = decompressGzip(responseSource.rawBytes)
        assertEquals("Part 1. Part 2. Part 3.", decompressed)
    }

    @Test
    fun `Content-Encoding header is added to original response`() {
        val responseSource = GzipMockResponseSource()
        val exchange = createExchange(responseSource)

        GZipResponseInterceptor().intercept(exchange) { ex ->
            ex.response.outputStream.write("data".toByteArray())
        }

        // Header should be on the original response source, not the buffered one
        val headerValues = responseSource.getHeaderValues("Content-Encoding")
        assertTrue(headerValues.contains("gzip"))
    }

    @Test
    fun `handles large content correctly`() {
        val responseSource = GzipMockResponseSource()
        val exchange = createExchange(responseSource)

        // 1MB of content
        val largeContent = "A".repeat(1024 * 1024)
        GZipResponseInterceptor().intercept(exchange) { ex ->
            ex.response.outputStream.write(largeContent.toByteArray())
        }

        val decompressed = decompressGzip(responseSource.rawBytes)
        assertEquals(largeContent.length, decompressed.length)
        assertEquals(largeContent, decompressed)
    }

    @Test
    fun `companion instance is reusable`() {
        val instance1 = GZipResponseInterceptor.instance
        val instance2 = GZipResponseInterceptor.instance
        assertTrue(instance1 === instance2)
    }

    @Test
    fun `handles unicode content`() {
        val responseSource = GzipMockResponseSource()
        val exchange = createExchange(responseSource)

        val unicodeText = "Hello \u00e9\u00e8\u00ea \u4e16\u754c \ud83c\udf0d"
        GZipResponseInterceptor().intercept(exchange) { ex ->
            ex.response.outputStream.write(unicodeText.toByteArray(Charsets.UTF_8))
        }

        val decompressed = decompressGzip(responseSource.rawBytes)
        assertEquals(unicodeText, decompressed)
    }

    @Test
    fun `adds Vary Accept-Encoding header`() {
        val responseSource = GzipMockResponseSource()
        val exchange = createExchange(responseSource)

        GZipResponseInterceptor().intercept(exchange) { ex ->
            ex.response.outputStream.write("data".toByteArray())
        }

        val vary = responseSource.headers.filter { it.name.equals("Vary", true) }.flatMap { it.values }
        assertTrue(vary.contains("Accept-Encoding"))
    }

    @Test
    fun `checkForAcceptHeader false always compresses regardless of header`() {
        val responseSource = GzipMockResponseSource()
        val exchange = createExchange(responseSource, requestHeaders = listOf("Host" to listOf("localhost")))

        val text = "No accept-encoding header sent"
        GZipResponseInterceptor(checkForAcceptHeader = false).intercept(exchange) { ex ->
            ex.response.outputStream.write(text.toByteArray())
        }

        val decompressed = decompressGzip(responseSource.rawBytes)
        assertEquals(text, decompressed)
    }

    @Test
    fun `checkForAcceptHeader true skips compression when no Accept-Encoding header`() {
        val responseSource = GzipMockResponseSource()
        val exchange = createExchange(responseSource, requestHeaders = listOf("Host" to listOf("localhost")))

        val text = "Plain text"
        GZipResponseInterceptor(checkForAcceptHeader = true).intercept(exchange) { ex ->
            ex.response.outputStream.write(text.toByteArray())
        }

        val raw = responseSource.rawBytes
        assertEquals(text, String(raw))
        val contentEncoding = responseSource.headers.filter { it.name.equals("Content-Encoding", true) }
        assertTrue(contentEncoding.isEmpty())
    }

    @Test
    fun `checkForAcceptHeader true compresses when Accept-Encoding contains gzip`() {
        val responseSource = GzipMockResponseSource()
        val exchange = createExchange(responseSource, requestHeaders = listOf(
            "Host" to listOf("localhost"),
            "Accept-Encoding" to listOf("gzip, deflate, br"),
        ))

        val text = "Should be compressed"
        GZipResponseInterceptor(checkForAcceptHeader = true).intercept(exchange) { ex ->
            ex.response.outputStream.write(text.toByteArray())
        }

        val decompressed = decompressGzip(responseSource.rawBytes)
        assertEquals(text, decompressed)
    }

    @Test
    fun `checkForAcceptHeader true compresses when Accept-Encoding is exactly gzip`() {
        val responseSource = GzipMockResponseSource()
        val exchange = createExchange(responseSource, requestHeaders = listOf(
            "Host" to listOf("localhost"),
            "Accept-Encoding" to listOf("gzip"),
        ))

        val text = "Exact gzip header"
        GZipResponseInterceptor(checkForAcceptHeader = true).intercept(exchange) { ex ->
            ex.response.outputStream.write(text.toByteArray())
        }

        val decompressed = decompressGzip(responseSource.rawBytes)
        assertEquals(text, decompressed)
    }

    @Test
    fun `checkForAcceptHeader true skips when Accept-Encoding has no gzip`() {
        val responseSource = GzipMockResponseSource()
        val exchange = createExchange(responseSource, requestHeaders = listOf(
            "Host" to listOf("localhost"),
            "Accept-Encoding" to listOf("deflate, br"),
        ))

        val text = "Not compressed"
        GZipResponseInterceptor(checkForAcceptHeader = true).intercept(exchange) { ex ->
            ex.response.outputStream.write(text.toByteArray())
        }

        val raw = responseSource.rawBytes
        assertEquals(text, String(raw))
    }

    @Test
    fun `checkForAcceptHeader true handles gzip with quality value`() {
        val responseSource = GzipMockResponseSource()
        val exchange = createExchange(responseSource, requestHeaders = listOf(
            "Host" to listOf("localhost"),
            "Accept-Encoding" to listOf("gzip;q=0.8, deflate"),
        ))

        val text = "Quality value gzip"
        GZipResponseInterceptor(checkForAcceptHeader = true).intercept(exchange) { ex ->
            ex.response.outputStream.write(text.toByteArray())
        }

        val decompressed = decompressGzip(responseSource.rawBytes)
        assertEquals(text, decompressed)
    }
}
