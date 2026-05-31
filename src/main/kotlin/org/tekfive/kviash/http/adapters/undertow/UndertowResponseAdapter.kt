package org.tekfive.kviash.http.adapters.undertow

import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.CookieImpl
import io.undertow.util.HttpString
import org.tekfive.kviash.http.HttpHeader
import org.tekfive.kviash.http.HttpResponseSource
import org.tekfive.kviash.http.ResponseCookie
import org.tekfive.kviash.http.SameSite
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer

class UndertowResponseAdapter(val exchange: HttpServerExchange) : HttpResponseSource {

    override val status: Int
        get() = exchange.statusCode

    override val headers: List<HttpHeader>
        get() = exchange.responseHeaders.headerNames.map { name ->
            HttpHeader(name.toString(), exchange.responseHeaders.get(name).toList())
        }

    override val committed: Boolean
        get() = exchange.isResponseStarted

    override val outputStream: OutputStream by lazy {
        if (!exchange.isBlocking) exchange.startBlocking()
        exchange.outputStream
    }

    override val outputWriter: Writer by lazy {
        OutputStreamWriter(outputStream)
    }

    override fun addCookie(cookie: ResponseCookie) {
        val undertowCookie = CookieImpl(cookie.name, cookie.value)

        cookie.path?.let { undertowCookie.path = it }
        cookie.domain?.let { undertowCookie.domain = it }
        cookie.maxAge?.let { undertowCookie.maxAge = it.inWholeSeconds.toInt() }
        cookie.secure?.let { undertowCookie.isSecure = it }
        cookie.httpOnly?.let { undertowCookie.isHttpOnly = it }
        cookie.sameSite?.let {
            undertowCookie.sameSiteMode = when (it) {
                SameSite.Lax -> "Lax"
                SameSite.Strict -> "Strict"
                SameSite.None -> "None"
            }
        }

        exchange.setResponseCookie(undertowCookie)
    }

    override fun addHeader(header: HttpHeader) {
        for (value in header.values) {
            exchange.responseHeaders.add(HttpString(header.name), value)
        }
    }

    override fun setHeader(header: HttpHeader) {
        exchange.responseHeaders.put(HttpString(header.name), header.delimitedValue)
    }

    override fun getHeaderValues(name: String): List<String> {
        return exchange.responseHeaders.get(name)?.toList() ?: emptyList()
    }

    override fun commit() {
        if (!exchange.isResponseStarted && exchange.isBlocking) {
            exchange.outputStream.flush()
        }
    }

    override fun createdBufferedResponse(outputBuffer: OutputStream): HttpResponseSource {
        return UndertowBufferedResponseAdapter(this, outputBuffer)
    }

    override fun setStatus(status: Int) {
        exchange.statusCode = status
    }
}

class UndertowBufferedResponseAdapter(
    private val delegate: UndertowResponseAdapter,
    private val outputBuffer: OutputStream,
) : HttpResponseSource {
    private var writer: Writer? = null

    override val status: Int
        get() = delegate.status

    override val headers: List<HttpHeader>
        get() = delegate.headers

    override val committed: Boolean
        get() = delegate.committed

    override val outputStream: OutputStream
        get() = outputBuffer

    override val outputWriter: Writer
        get() {
            if (writer == null) {
                writer = OutputStreamWriter(outputBuffer)
            }
            return writer!!
        }

    override fun addCookie(cookie: ResponseCookie) = delegate.addCookie(cookie)

    override fun addHeader(header: HttpHeader) = delegate.addHeader(header)

    override fun setHeader(header: HttpHeader) = delegate.setHeader(header)

    override fun getHeaderValues(name: String): List<String> = delegate.getHeaderValues(name)

    override fun commit() {
        writer?.flush()
        outputBuffer.flush()
    }

    override fun createdBufferedResponse(outputBuffer: OutputStream): HttpResponseSource {
        return UndertowBufferedResponseAdapter(delegate, outputBuffer)
    }

    override fun setStatus(status: Int) = delegate.setStatus(status)
}
