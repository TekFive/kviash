package org.tekfive.kviash

import org.tekfive.ack.Ack
import org.tekfive.kviash.exchange.DefaultExchangeErrorLogger
import org.tekfive.kviash.exchange.ExchangeErrorLogger
import org.tekfive.kviash.http.HttpRequest
import java.time.Instant
import java.time.InstantSource

interface KviashConfiguration : InstantSource {
    val inputBufferSize: Int

    val outputBufferSize: Int

    val trimParameterValues: Boolean

    val ignoreRoutePathCase: Boolean

    val trustedProxyCidrs: List<String>

    val exchangeErrorLogger: ExchangeErrorLogger

    fun getRootUrl(request: HttpRequest): String
}

class ConfigurationOverride(
    val inputBufferSize: Int? = null,
    val outputBufferSize: Int? = null,
    val trimParameterValues: Boolean? = null,
    val ignoreRoutePathCase: Boolean? = null,
    val trustedProxyCidrs: List<String>? = null,
    val exchangeErrorLogger: ExchangeErrorLogger? = null,
    val instant: (() -> Instant)? = null,
    val getRootUrl: ((HttpRequest) -> String)? = null,
)

class StackableConfiguration(
    override val inputBufferSize: Int,
    override val outputBufferSize: Int,
    override val trimParameterValues: Boolean,
    override val ignoreRoutePathCase: Boolean,
    override val trustedProxyCidrs: List<String>,
    override val exchangeErrorLogger: ExchangeErrorLogger,
    val instantHandler: () -> Instant,
    val getRootUrlHandler: (HttpRequest) -> String,
) : KviashConfiguration {

    override fun instant(): Instant {
        return instantHandler()
    }

    override fun getRootUrl(request: HttpRequest): String {
        return getRootUrlHandler(request)
    }

    constructor(override: ConfigurationOverride, baseConfiguration: KviashConfiguration) : this(
        override.inputBufferSize ?: baseConfiguration.inputBufferSize,
        override.outputBufferSize ?: baseConfiguration.outputBufferSize,
        override.trimParameterValues ?: baseConfiguration.trimParameterValues,
        override.ignoreRoutePathCase ?: baseConfiguration.ignoreRoutePathCase,
        override.trustedProxyCidrs ?: baseConfiguration.trustedProxyCidrs,
        override.exchangeErrorLogger ?: baseConfiguration.exchangeErrorLogger,
        override.instant ?: { baseConfiguration.instant() },
        override.getRootUrl ?: { baseConfiguration.getRootUrl(it) },
    )

    constructor(
        baseConfiguration: KviashConfiguration,
        inputBufferSize: Int = baseConfiguration.inputBufferSize,
        outputBufferSize: Int = baseConfiguration.outputBufferSize,
        trimParameterValues: Boolean = baseConfiguration.trimParameterValues,
        ignoreRoutePathCase: Boolean = baseConfiguration.ignoreRoutePathCase,
        trustedProxyCidrs: List<String> = baseConfiguration.trustedProxyCidrs,
        exchangeErrorLogger: ExchangeErrorLogger = baseConfiguration.exchangeErrorLogger,
        instantHandler: () -> Instant = { baseConfiguration.instant() },
        getRootUrlHandler: (org.tekfive.kviash.http.HttpRequest) -> String = { baseConfiguration.getRootUrl(it) }
    ) : this(
        inputBufferSize,
        outputBufferSize,
        trimParameterValues,
        ignoreRoutePathCase,
        trustedProxyCidrs,
        exchangeErrorLogger,
        instantHandler,
        getRootUrlHandler
    )
}

object DefaultKviashConfiguration : BasicKviashConfiguration(null)

open class BasicKviashConfiguration(val rootUrl: String? = null) : KviashConfiguration {
    override val inputBufferSize: Int = inputBufferSizeProperty()

    override val outputBufferSize: Int = outputBufferSizeProperty()

    override val trimParameterValues: Boolean = trimParameterValuesProperty()

    override val ignoreRoutePathCase: Boolean = ignoreRoutePathCaseProperty()

    override val trustedProxyCidrs: List<String> = trustedProxyCidrsProperty.orNull()
        ?.split(',', ' ', '\n', '\t')
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?: emptyList()

    override val exchangeErrorLogger: ExchangeErrorLogger = DefaultExchangeErrorLogger

    override fun instant(): Instant {
        return Instant.now()
    }

    override fun getRootUrl(request: HttpRequest): String {
        return if (rootUrl != null) {
            rootUrl
        } else {
            val host = request.host
                ?: throw IllegalStateException("Request contains no Host header.")

            val protocol = request.urlProtocol.lowercase()

            "$protocol://$host"
        }
    }

    companion object {
        private const val PropertyPrefix = "KVSH"

        val inputBufferSizeProperty = Ack.int("INPUT_BUFFER_SIZE", 8192, namespace = PropertyPrefix, description = "Size in bytes of the HTTP request input buffer.")

        val outputBufferSizeProperty = Ack.int("OUTPUT_BUFFER_SIZE", 8192, namespace = PropertyPrefix, description = "Size in bytes of the HTTP response output buffer.")

        val trimParameterValuesProperty = Ack.boolean("TRIM_PARAMETER_VALUES", true, namespace = PropertyPrefix, description = "Whether request parameter values are trimmed of surrounding whitespace.")

        val ignoreRoutePathCaseProperty = Ack.boolean("IGNORE_ROUTE_PATH_CASE", true, namespace = PropertyPrefix, description = "Whether route path matching is case-insensitive.")

        val legacyTrustedProxyCidrsProperty = Ack.string("TRUSTED_PROXY_CIDRS", description = "Deprecated alias for trusted proxy CIDR ranges; prefer the KVSH-prefixed property.")

        val trustedProxyCidrsProperty = Ack.string("TRUSTED_PROXY_CIDRS", fallback = legacyTrustedProxyCidrsProperty, namespace = PropertyPrefix, description = "Comma/space-separated CIDR ranges of trusted reverse proxies.")
    }
}
