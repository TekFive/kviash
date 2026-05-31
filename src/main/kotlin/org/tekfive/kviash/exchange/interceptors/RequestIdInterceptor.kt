package org.tekfive.kviash.exchange.interceptors

import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.http.HttpHeader
import java.util.UUID

/**
 * Assigns a unique identifier to each request for tracing and log correlation.
 *
 * Use this interceptor to propagate or generate a request ID that follows the request through
 * the pipeline and into log output. If the incoming request already carries the configured
 * header (defaults to `X-Request-ID`), that value is reused — this preserves trace continuity
 * when requests pass through load balancers or API gateways that assign IDs upstream.
 * Otherwise, a new UUID is generated.
 *
 * The ID is stored as a request attribute ([RequestIdAttribute]) for use by application code
 * and, when [includeInResponse] is `true`, echoed back in the response header so clients can
 * reference it in bug reports or support tickets.
 *
 * ```kotlin
 * RouteTable.register(interceptors = listOf(RequestIdInterceptor())) {
 *     add(controller::getResource)
 * }
 * ```
 */
class RequestIdInterceptor(
    val headerName: String = HttpHeader.XRequestID,
    val includeInResponse: Boolean = true,
) : PipelineInterceptor {

    override fun intercept(exchange: Exchange, continuePipeline: (Exchange) -> Unit) {
        val requestId = exchange.request.getFirstHeaderValue(headerName)
            ?: UUID.randomUUID().toString()

        exchange.request[RequestIdAttribute] = requestId

        if (includeInResponse) {
            exchange.response.addHeader(headerName, requestId)
        }

        continuePipeline(exchange)
    }

    companion object {
        const val RequestIdAttribute = "RequestIdInterceptor:requestId"
    }
}
