package org.tekfive.kviash.http.adapters.jetty

import org.eclipse.jetty.http.HttpCookie as JettyHttpCookie
import org.eclipse.jetty.io.Content
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.util.Callback
import org.tekfive.kviash.http.HttpHeader
import org.tekfive.kviash.http.HttpResponseSource
import org.tekfive.kviash.http.ResponseCookie
import org.tekfive.kviash.http.SameSite
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer

class JettyResponseAdapter(val jettyRequest: Request, val jettyResponse: Response) : HttpResponseSource {

    override val status: Int
        get() = jettyResponse.status

    override val headers: List<HttpHeader>
        get() {
            val headerMap = linkedMapOf<String, MutableList<String>>()
            for (field in jettyResponse.headers) {
                headerMap.getOrPut(field.name) { mutableListOf() }.add(field.value)
            }
            return headerMap.map { (name, values) -> HttpHeader(name, values) }
        }

    override val committed: Boolean
        get() = jettyResponse.isCommitted

    override val outputStream: OutputStream by lazy {
        Content.Sink.asOutputStream(jettyResponse)
    }

    override val outputWriter: Writer by lazy {
        OutputStreamWriter(outputStream)
    }

    override fun addCookie(cookie: ResponseCookie) {
        val builder = JettyHttpCookie.build(cookie.name, cookie.value)

        cookie.path?.let { builder.path(it) }
        cookie.domain?.let { builder.domain(it) }
        cookie.maxAge?.let { builder.maxAge(it.inWholeSeconds) }
        cookie.secure?.let { builder.secure(it) }
        cookie.httpOnly?.let { builder.httpOnly(it) }
        cookie.sameSite?.let {
            val jettySameSite = when (it) {
                SameSite.Lax -> JettyHttpCookie.SameSite.LAX
                SameSite.Strict -> JettyHttpCookie.SameSite.STRICT
                SameSite.None -> JettyHttpCookie.SameSite.NONE
            }
            builder.sameSite(jettySameSite)
        }
        cookie.partitioned?.let { if (it) builder.partitioned(true) }

        Response.addCookie(jettyResponse, builder.build())
    }

    override fun addHeader(header: HttpHeader) {
        for (value in header.values) {
            jettyResponse.headers.add(header.name, value)
        }
    }

    override fun setHeader(header: HttpHeader) {
        jettyResponse.headers.put(header.name, header.delimitedValue)
    }

    override fun getHeaderValues(name: String): List<String> {
        val values = mutableListOf<String>()
        for (field in jettyResponse.headers) {
            if (field.name.equals(name, ignoreCase = true)) {
                values.add(field.value)
            }
        }
        return values
    }

    override fun commit() {
        if (!jettyResponse.isCommitted) {
            jettyResponse.write(false, null, Callback.NOOP)
        }
    }

    override fun createdBufferedResponse(outputBuffer: OutputStream): HttpResponseSource {
        return JettyBufferedResponseAdapter(this, outputBuffer)
    }

    override fun setStatus(status: Int) {
        jettyResponse.status = status
    }
}

class JettyBufferedResponseAdapter(
    private val delegate: JettyResponseAdapter,
    private val outputBuffer: OutputStream
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
        return JettyBufferedResponseAdapter(delegate, outputBuffer)
    }

    override fun setStatus(status: Int) = delegate.setStatus(status)
}
