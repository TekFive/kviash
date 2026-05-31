package org.tekfive.kviash.exchange.interceptors

import org.tekfive.kviash.http.HttpHeader
import org.tekfive.kviash.http.HttpResponse
import org.tekfive.kviash.exchange.Exchange
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.util.zip.GZIPOutputStream

/**
 * Compresses response bodies using gzip encoding.
 *
 * Use this interceptor on routes that return compressible content (HTML, JSON, CSS, JavaScript)
 * to reduce bandwidth and improve page load times. The interceptor buffers the response body,
 * compresses it with gzip, and sets the `Content-Encoding: gzip` and `Vary: Accept-Encoding`
 * response headers.
 *
 * When [checkForAcceptHeader] is `true`, compression is only applied if the client sends an
 * `Accept-Encoding` header that includes `gzip`. When `false` (the default), all responses
 * are compressed regardless of what the client advertises.
 *
 * ```kotlin
 * RouteTable.register(interceptors = listOf(
 *     GZipResponseInterceptor(checkForAcceptHeader = true)
 * )) {
 *     add(controller::getLargePayload)
 * }
 * ```
 */
class GZipResponseInterceptor(
    val checkForAcceptHeader: Boolean = false,
) : PipelineInterceptor {

    override fun intercept(exchange: Exchange, continuePipeline: (Exchange) -> Unit) {
        if (checkForAcceptHeader && !acceptsGzip(exchange)) {
            continuePipeline(exchange)
            return
        }

        exchange.response.addHeader("Content-Encoding", "gzip")
        exchange.response.addHeader("Vary", "Accept-Encoding")
        val gzipOutput = GZIPOutputStream(exchange.response.outputStream)
        val gzipResponse = exchange.response.createdBufferedResponse(gzipOutput)
        continuePipeline(Exchange(exchange, response = gzipResponse))
        gzipResponse.commit()
        gzipOutput.close()
    }

    private fun acceptsGzip(exchange: Exchange): Boolean {
        val acceptEncoding = exchange.request.getFirstHeaderValue(HttpHeader.AcceptEncoding) ?: return false
        return acceptEncoding.split(',').any { it.trim().lowercase().startsWith("gzip") }
    }

    companion object {
        val instance = GZipResponseInterceptor()
    }
}