package org.tekfive.kviash.exchange.interceptors

import org.tekfive.kviash.http.HttpHeader
import org.tekfive.kviash.exchange.Exchange
import java.io.ByteArrayOutputStream
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

        val compressedBuffer = ByteArrayOutputStream()
        GZIPOutputStream(compressedBuffer).use { gzipOutput ->
            val bufferedResponse = exchange.response.createdBufferedResponse(gzipOutput)
            continuePipeline(Exchange(exchange, response = bufferedResponse))
            bufferedResponse.commit()
        }
        val compressedBody = compressedBuffer.toByteArray()

        exchange.response.setHeader(HttpHeader(HttpHeader.ContentEncoding, "gzip"))
        exchange.response.addVary(HttpHeader.AcceptEncoding)
        exchange.response.setContentLength(compressedBody.size.toLong())
        exchange.response.outputStream.write(compressedBody)
        exchange.response.commit()
    }

    private fun acceptsGzip(exchange: Exchange): Boolean {
        val acceptEncoding = exchange.request.getFirstHeaderValue(HttpHeader.AcceptEncoding) ?: return false
        return acceptEncoding.split(',').any { value ->
            val parts = value.split(';').map { it.trim() }
            parts.firstOrNull().equals("gzip", ignoreCase = true) &&
                parts.drop(1).none { parameter ->
                    val (name, quality) = parameter.split('=', limit = 2).let {
                        it.firstOrNull()?.trim() to it.getOrNull(1)?.trim()
                    }
                    name.equals("q", ignoreCase = true) && quality?.toDoubleOrNull() == 0.0
                }
        }
    }

    companion object {
        val instance = GZipResponseInterceptor()
    }
}
