package org.tekfive.kviash.http

import org.tekfive.kviash.KviashConfiguration
import org.tekfive.kviash.exchange.RedirectType
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.Writer

class HttpResponse(
    val source: HttpResponseSource,
    val configuration: KviashConfiguration
) {
    var status: Int
        get() = source.status
        set(value) {
            source.setStatus(value)
        }

    val headers: List<HttpHeader>
        get() = source.headers

    /**
     * A committed response has already had its status code and headers written.
     * @return a boolean indicating if the response has been committed
     */
    val committed: Boolean
        get() = source.committed

    val contentLength: Long?
        get() = source.getHeaderValues(HttpHeader.ContentLength).firstOrNull()?.toLongOrNull()

    val contentType: String?
        get() = source.getHeaderValues(HttpHeader.ContentType).firstOrNull()

    val location: String?
        get() = source.getHeaderValues(HttpHeader.Location).firstOrNull()

    val outputStream: OutputStream
        get() = source.outputStream

    val outputWriter: Writer
        get() = source.outputWriter

    val outputBufferedWriter: BufferedWriter
        get() = outputWriter.let {
            it as? BufferedWriter ?: BufferedWriter(it, configuration.outputBufferSize)
        }

    fun addHeader(name: String, value: String) {
        addHeader(HttpHeader(name, value))
    }

    fun addHeader(header: HttpHeader) {
        source.addHeader(header)
    }

    fun setHeader(header: HttpHeader) {
        source.setHeader(header)
    }

    fun addVary(value: String) {
        val values = source.getHeaderValues(HttpHeader.Vary)
            .flatMap { it.split(',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()

        if (values.none { it.equals(value, ignoreCase = true) }) {
            values.add(value)
        }
        source.setHeader(HttpHeader(HttpHeader.Vary, values.joinToString(", ")))
    }

    fun setContentLength(contentLength: Long) {
        source.setHeader(HttpHeader(HttpHeader.ContentLength, contentLength.toString()))
    }

    fun setContentType(contentType: String) {
        source.setHeader(HttpHeader(HttpHeader.ContentType, contentType))
    }

    fun addCookie(cookie: ResponseCookie) {
        source.addCookie(cookie)
    }

    /**
     * Set status and commit response.
     */
    fun sendStatus(status: Int) {
        this.status = status
        source.commit()
    }

    fun sendRedirect(location: String, status: Int = RedirectType.MOVED_PERMANENTLY.code) {
        setHeader(HttpHeader.Location.toHttpHeader(location))
        source.setStatus(status)
        source.commit()
    }

    fun commit() {
        source.commit()
    }

    fun createdBufferedResponse(outputBuffer: OutputStream): HttpResponse {
        return HttpResponse(source.createdBufferedResponse(outputBuffer), configuration)
    }

}
