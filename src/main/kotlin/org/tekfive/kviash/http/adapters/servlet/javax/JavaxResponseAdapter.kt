package org.tekfive.kviash.http.adapters.servlet.javax

import javax.servlet.ServletOutputStream
import javax.servlet.WriteListener
import javax.servlet.http.HttpServletResponseWrapper
import javax.servlet.http.Cookie
import javax.servlet.http.HttpServletResponse
import org.tekfive.kviash.http.HttpHeader
import org.tekfive.kviash.http.HttpResponseSource
import org.tekfive.kviash.http.ResponseCookie
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.Writer
import java.time.Duration
import java.time.Instant

class JavaxResponseAdapter(val servletResponse: HttpServletResponse) : HttpResponseSource {
    override val status: Int
        get() = servletResponse.status

    override val headers: List<HttpHeader>
        get() {
            val headers = mutableListOf<HttpHeader>()
            for (headerName in servletResponse.headerNames) {
                headers.add(HttpHeader(headerName, servletResponse.getHeaders(headerName).toList()))
            }
            return headers
        }


    override val committed: Boolean
        get() = servletResponse.isCommitted

    override val outputStream: OutputStream
        get() = servletResponse.outputStream

    override val outputWriter: Writer
        get() = servletResponse.writer

    override fun addCookie(cookie: ResponseCookie) {
        val sourceCookie = Cookie(cookie.name, cookie.value)

        if (cookie.path != null) {
            sourceCookie.path = cookie.path
        }

        if (cookie.secure != null) {
            sourceCookie.secure = cookie.secure
        }

        if (cookie.httpOnly != null) {
            sourceCookie.isHttpOnly = cookie.httpOnly
        }

        if (cookie.domain != null) {
            sourceCookie.domain = cookie.domain
        }

        if (cookie.maxAge != null) {
            sourceCookie.maxAge = cookie.maxAge.inWholeSeconds.toInt()
        } else if (cookie.expires != null) {
            sourceCookie.maxAge = Duration.between(Instant.now(), cookie.expires).seconds.toInt().coerceAtLeast(0)
        }

        servletResponse.addCookie(sourceCookie)
    }

    override fun addHeader(header: HttpHeader) {
        for (value in header.values) {
            servletResponse.addHeader(header.name, value)
        }
    }

    override fun setHeader(header: HttpHeader) {
        servletResponse.setHeader(header.name, header.delimitedValue)
    }

    override fun setStatus(status: Int) {
        servletResponse.status = status
    }

    override fun getHeaderValues(name: String): List<String> {
        return servletResponse.getHeaders(name)?.toList() ?: emptyList()
    }

    override fun commit() {
        servletResponse.flushBuffer()
    }

    override fun createdBufferedResponse(outputBuffer: OutputStream): HttpResponseSource {
        return JavaxResponseAdapter(BufferedResponseWrapper(servletResponse, outputBuffer))
    }
}

class BufferedResponseWrapper(response: HttpServletResponse, val outputBuffered: OutputStream) : HttpServletResponseWrapper(response) {
    private var writer: PrintWriter? = null
    private var outputStream: ServletOutputStream? = null

    override fun getOutputStream(): ServletOutputStream {
        if (writer != null) throw IllegalStateException("getWriter() has already been called")
        if (outputStream == null) {
            outputStream = object : ServletOutputStream() {
                override fun write(b: Int) = outputBuffered.write(b)
                override fun isReady(): Boolean = true
                override fun setWriteListener(p0: WriteListener?) {}
            }
        }
        return outputStream!!
    }

    override fun getWriter(): PrintWriter {
        if (outputStream != null) throw IllegalStateException("getOutputStream() has already been called")
        if (writer == null) {
            writer = PrintWriter(OutputStreamWriter(outputBuffered, characterEncoding))
        }
        return writer!!
    }

    override fun flushBuffer() {
        writer?.flush()
        outputStream?.flush()
    }
}