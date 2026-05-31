package org.tekfive.kviash.exchange

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.tekfive.kviash.KviashConfiguration
import org.tekfive.kviash.exchange.interceptors.PipelineInterceptor
import org.tekfive.kviash.routing.PrimitiveParameterType
import org.tekfive.kviash.routing.GobblerSegmentMatcher
import org.tekfive.kviash.routing.LiteralRouteSegmentMatcher
import org.tekfive.kviash.routing.PatternRouteSegmentMatcher
import org.tekfive.kviash.routing.RoutePath
import org.tekfive.kviash.routing.RouteSegmentMatcher
import org.tekfive.kviash.routing.RouteSegmentType
import org.tekfive.kviash.routing.RouteTree
import org.tekfive.kviash.routing.RoutesConfigurationException
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KFunction

class ExchangePipeline (
    val configuration: KviashConfiguration,
    val routePath: RoutePath,
    val interceptors: List<PipelineInterceptor>,
    val preActions: List<ExchangeAction>,
    val action: ExchangeAction,
    val postActions: List<ExchangeAction>,
    val routeAttributes: Map<String, Any?>,
    val acceptTypes: Set<String> = emptySet(),
) {

    private val log: Logger = LoggerFactory.getLogger(ExchangePipeline::class.java)

    private var _routeParameterSegmentIndices: List<Int>

    private var _routeSegmentMatchers: List<RouteSegmentMatcher>

    internal lateinit var treeNode: RouteTree

    internal val treeNodeOrNull: RouteTree?
        get() = if (::treeNode.isInitialized) treeNode else null

    internal val routeSegmentMatchers: List<RouteSegmentMatcher>
        get() = _routeSegmentMatchers

    internal val routeParameterSegmentIndices: List<Int>
        get() = _routeParameterSegmentIndices

    val exchangeFunction: KFunction<*>? = if (action is ExchangeFunction) { action.function } else { null }

    val name: String = if (exchangeFunction == null) routePath.toString() else exchangeFunction.name

    init {
        /**
         * Verify that all action function parameters match up. If they don't then this pipeline cannot be satisfied.
         */
        val processorFunctions = preActions.filterIsInstance<ExchangeFunction>().map { "preprocessor" to it }.toMutableList()
        if (action is ExchangeFunction) {
            processorFunctions.add("processor" to action)
        }
        processorFunctions.addAll(postActions.filterIsInstance<ExchangeFunction>().map { "postprocessor" to it })
        val requiredPathParameterTypes = mutableListOf<PrimitiveParameterType>()

        if (processorFunctions.isNotEmpty()) {
            val (maxLabel, maxProcessorFunction) = processorFunctions.maxBy { it.second.requiredPathParameterTypes.size }
            if (maxProcessorFunction.requiredPathParameterTypes.isNotEmpty()) {
                requiredPathParameterTypes.addAll(maxProcessorFunction.requiredPathParameterTypes)
                for ((label, processorFunction) in processorFunctions) {
                    if (processorFunction.requiredPathParameterTypes != maxProcessorFunction.requiredPathParameterTypes.take(processorFunction.requiredPathParameterTypes.size)) {
                        throw RoutesConfigurationException(
                            "Route $label ${processorFunction.function.name} route path input parameters is not consistent with $maxLabel ${maxProcessorFunction.function.name} parameters."
                        )
                    }
                }
            }
        }

        val routeParameterSegmentIndices = mutableListOf<Int>()
        val routeSegmentMatchers = mutableListOf<RouteSegmentMatcher>()

        for (segment in routePath.segments) {
            when (segment.type) {
                RouteSegmentType.LITERAL -> {
                    routeSegmentMatchers.add(LiteralRouteSegmentMatcher(segment.value!!, configuration.ignoreRoutePathCase))
                }

                RouteSegmentType.EXPLICIT_EXPRESSION -> {
                    routeSegmentMatchers.add(PatternRouteSegmentMatcher.Companion(segment.value!!, configuration.ignoreRoutePathCase))
                    val pathParameterType = requiredPathParameterTypes.removeFirstOrNull()
                    if (pathParameterType != null) {
                        routeParameterSegmentIndices.add(segment.index)
                    }
                }

                RouteSegmentType.INFERRED_EXPRESSION -> {
                    val pathParameterType = requiredPathParameterTypes.removeFirstOrNull()
                    if (pathParameterType == null) {
                        throw RoutesConfigurationException("Route $routePath does not have function parameter to define pattern for segment $segment at index ${segment.index}.")
                    }

                    routeSegmentMatchers.add(PatternRouteSegmentMatcher(pathParameterType.regex, "{}"))
                    routeParameterSegmentIndices.add(segment.index)
                }

                RouteSegmentType.WILDCARD_EXPRESSION -> {
                    val pathParameterType = requiredPathParameterTypes.removeFirstOrNull()
                    if (pathParameterType != null) {
                        if (pathParameterType != PrimitiveParameterType.StringType) {
                            throw RoutesConfigurationException("Route $routePath wildcard segment at index ${segment.index} must have parameter type String but has type ${pathParameterType.label}.")
                        }
                        routeParameterSegmentIndices.add(segment.index)
                    }
                    routeSegmentMatchers.add(PatternRouteSegmentMatcher(PrimitiveParameterType.StringType.regex, "{*}"))
                }

                RouteSegmentType.PATH_GOBBLER -> {
                    if (routePath.segments.last() != segment) {
                        throw RoutesConfigurationException("Route $routePath cannot have additional segments after gobbler pattern {**}.")
                    }
                    routeSegmentMatchers.add(GobblerSegmentMatcher)
                }
            }
        }

        /**
         * If there are still required path parameter types left then they can't all be satisfied by the route path.
         */
        if (requiredPathParameterTypes.isNotEmpty()) {
            throw RoutesConfigurationException("Route $routePath has more parameters defined from path segments than can be provided.")
        }


        _routeParameterSegmentIndices = routeParameterSegmentIndices
        _routeSegmentMatchers = routeSegmentMatchers
    }

    operator fun invoke(exchange: Exchange) {
        Exchange.exchangeLocal.set(exchange)
        try {
            runWithInterceptors(interceptors.toMutableList(), exchange)
        } finally {
            Exchange.exchangeLocal.remove()
        }
    }

    private fun runWithInterceptors(interceptors: MutableList<PipelineInterceptor>, exchange: Exchange) {
        if (interceptors.isNotEmpty()) {
            val interceptor = interceptors.removeFirst()
            interceptor.intercept(exchange) { exchange ->
                runWithInterceptors(interceptors, exchange)
            }
            return
        }

        try {
            exchange.request["exchange"] = exchange

            val checkStatusAfter:(() -> Unit) -> Unit = { action ->
                val previousStatus = exchange.response.status
                val previouslyCommitted = exchange.response.committed

                action()

                val nextStatus = exchange.response.status
                if (previouslyCommitted) {
                    if (nextStatus != previousStatus) {
                        log.warn("Response status change from: $previousStatus to: $nextStatus for exchange $exchange but the response was already committed.")
                    }
                } else if (previousStatus != nextStatus && nextStatus.isHttpRedirect && exchange.response.location.isNullOrBlank()) {
                    log.warn("Redirect status: ${exchange.response.status} for exchange $exchange with no Location header.")
                }
            }

            exchange._state = ExchangeState.PRE_ACTIONS
            for (preAction in preActions) {
                if (!exchange.response.committed) {
                    try {
                        checkStatusAfter {
                            val value = preAction(exchange)
                            if (value != null) {
                                exchange._processorValues.add(value)
                            }
                        }
                    } catch (e: Exception) {
                        onException(e, exchange)
                    }
                }
            }

            if (!exchange.response.committed) {
                exchange._state = ExchangeState.ACTION
                try {
                    checkStatusAfter {
                        val result = action(exchange)
                        if ((result is String) && RedirectTo.hasRedirectPrefix(result)) {
                            throw RedirectTo(RedirectTo.removeRedirectPrefix(result))
                        }

                        if (result != Unit) {
                            exchange._actionResult = result
                        }
                    }
                } catch (e: Exception) {
                    onException(e, exchange)
                }
            }

            exchange._state = ExchangeState.POST_ACTIONS
            for (postAction in postActions) {
                if (!exchange.response.committed) {
                    try {
                        checkStatusAfter {
                            val value = postAction(exchange)
                            if (value != null) {
                                exchange._processorValues.add(value)
                            }
                        }
                    } catch (e: Exception) {
                        onException(e, exchange)
                    }
                }
            }
        } finally {
            exchange._state = ExchangeState.COMPLETE
        }
    }

    private fun onException(e: Exception, exchange: Exchange) {
        val e = if (e is InvocationTargetException) {
            e.targetException as? Exception
                ?:
                throw e.targetException
        } else {
            e
        }

        if (exchange.response.committed) {
            log.warn("Exception thrown in exchange $exchange whose response has already been committed.", e)
            return
        }

        exchange._exceptions.add(e)

        if (e is ExchangeException) {
            when (e) {
                is RedirectTo -> {
                    e.sendRedirect(exchange)
                }

                is ReturnErrorStatus -> {
                    if (!exchange.response.committed && !exchange.response.status.isHttpError) {
                        exchange.response.status = e.errorCode.code
                        if (e.body != null) {
                            if (e.contentType != null) {
                                exchange.response.setContentType(e.contentType)
                            }
                            exchange.response.outputWriter.write(e.body)
                            exchange.response.outputWriter.flush()
                        }
                    }
                }

                is TerminateExchangeException -> throw e
            }
        } else {
            configuration.exchangeErrorLogger.error(e, exchange)
            if (!exchange.response.committed && !exchange.response.status.isHttpError) {
                exchange.response.status = HttpErrorCode.INTERNAL_SERVER_ERROR.code
            }
        }
    }
}