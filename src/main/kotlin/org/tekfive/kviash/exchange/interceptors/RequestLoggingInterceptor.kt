package org.tekfive.kviash.exchange.interceptors

import org.slf4j.LoggerFactory
import org.tekfive.kviash.exchange.Exchange

/**
 * Logs each request's HTTP method, path, response status code, and processing duration.
 *
 * Use this interceptor for access logging and basic observability. It measures the wall-clock
 * time the pipeline takes to process the request and logs a single line at `INFO` level via
 * SLF4J. Logging happens in a `finally` block so the duration is captured even when the
 * pipeline throws an exception.
 *
 * Output format: `GET /api/users 200 12ms`
 *
 * ```kotlin
 * RouteTable.register(interceptors = listOf(RequestLoggingInterceptor())) {
 *     add(controller::getUsers)
 * }
 * ```
 */
class RequestLoggingInterceptor(
    val loggerName: String = "org.tekfive.kviash.access",
) : PipelineInterceptor {

    private val log = LoggerFactory.getLogger(loggerName)

    override fun intercept(exchange: Exchange, continuePipeline: (Exchange) -> Unit) {
        val startTime = System.nanoTime()
        try {
            continuePipeline(exchange)
        } finally {
            val durationMs = (System.nanoTime() - startTime) / 1_000_000
            val method = exchange.request.method ?: "UNKNOWN"
            val path = exchange.request.path
            val status = exchange.response.status
            log.info("{} {} {} {}ms", method, path, status, durationMs)
        }
    }
}
