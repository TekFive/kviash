package org.tekfive.kviash.http

import java.io.OutputStream
import java.io.Writer

interface HttpResponseSource {
    val status: Int

    val headers: List<HttpHeader>

    /**
     * A committed response has already had its status code and headers written.
     * @return a boolean indicating if the response has been committed
     */
    val committed: Boolean

    val outputStream: OutputStream

    val outputWriter: Writer

    fun addCookie(cookie: ResponseCookie)

    fun addHeader(header: HttpHeader)

    fun setStatus(status: Int)

    fun setHeader(header: HttpHeader)

    fun getHeaderValues(name: String): List<String>

    fun commit()

    fun createdBufferedResponse(outputBuffer: OutputStream): HttpResponseSource
}