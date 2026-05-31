package org.tekfive.kviash.exchange.interceptors

import org.tekfive.kviash.exchange.Exchange

/**
 * Short-circuits the pipeline with a `503 Service Unavailable` response when maintenance
 * mode is active.
 *
 * Use this interceptor to gracefully take routes offline during deployments, database
 * migrations, or other planned downtime. The [enabled] lambda is evaluated on every request,
 * so maintenance mode can be toggled dynamically at runtime — for example, by checking a
 * feature flag, a file on disk, or an in-memory boolean.
 *
 * When enabled, the interceptor writes the [message] body with the configured [contentType]
 * and optionally sets a `Retry-After` header to tell clients when to try again. The pipeline
 * is not invoked.
 *
 * ```kotlin
 * val maintenanceFlag = AtomicBoolean(false)
 *
 * RouteTable.register(interceptors = listOf(
 *     MaintenanceModeInterceptor(
 *         enabled = { maintenanceFlag.get() },
 *         message = "Back shortly.",
 *         retryAfterSeconds = 300,
 *     )
 * )) {
 *     add(controller::getResource)
 * }
 * ```
 */
class MaintenanceModeInterceptor(
    val enabled: () -> Boolean = { false },
    val message: String = "Service temporarily unavailable for maintenance.",
    val contentType: String = "text/plain",
    val retryAfterSeconds: Long? = null,
) : PipelineInterceptor {

    override fun intercept(exchange: Exchange, continuePipeline: (Exchange) -> Unit) {
        if (enabled()) {
            retryAfterSeconds?.let {
                exchange.response.addHeader("Retry-After", it.toString())
            }
            exchange.response.setContentType(contentType)
            exchange.response.status = 503
            exchange.response.outputWriter.write(message)
            exchange.response.outputWriter.flush()
            return
        }

        continuePipeline(exchange)
    }
}
