package org.tekfive.kviash.exchange

import org.tekfive.kviash.KviashConfiguration
import org.tekfive.kviash.http.HttpRequest
import org.tekfive.kviash.http.HttpRequestParameters
import org.tekfive.kviash.http.HttpRequestPath
import org.tekfive.kviash.http.HttpResponse
import org.tekfive.kviash.routing.Routes
import kotlin.reflect.KFunction

class Exchange constructor(
    val request: HttpRequest,
    val requestPath: HttpRequestPath,
    val response: HttpResponse,
    internal val pipeline: ExchangePipeline,
    val routePath: String = pipeline.routePath.toString(),
    val configuration: KviashConfiguration = request.configuration,
) {

    val createdAt = pipeline.configuration.instant().toEpochMilli()

    val state: ExchangeState
        get() = _state

    val routeName: String
        get() = pipeline.name

    val routeFunction: KFunction<*>?
        get() = pipeline.exchangeFunction

    val routes: Routes = Routes(this)

    val parameters: HttpRequestParameters by lazy { HttpRequestParameters(request) }

    val processorValues: List<Any>
        get() = _processorValues.toList()

    val exceptions: List<Exception>
        get() = _exceptions.toList()

    internal var _state: ExchangeState = ExchangeState.INITIALIZED

    internal var _actionResult: Any? = null

    internal val _processorValues = mutableListOf<Any>()

    internal val _exceptions = mutableListOf<Exception>()

    val actionResult: Any?
        get() = _actionResult


    init {
        request["exchange"] = this
        request["routes"] = routes
        request["requestPath"] = requestPath
        request["parameters"] = parameters
    }


    constructor(
        source: Exchange,
        request: HttpRequest = source.request,
        requestPath: HttpRequestPath = source.requestPath,
        response: HttpResponse = source.response,
    ) : this(request, requestPath, response, source.pipeline)


    fun getRequestOrRouteAttribute(name: String): Any? {
        return request[name]
            ?:
            pipeline.routeAttributes[name]
    }

    fun getRouteAttribute(name: String): Any? {
        return pipeline.routeAttributes[name]
    }

    override fun toString(): String {
        return routePath
    }


    companion object {
        internal val exchangeLocal = ThreadLocal<Exchange>()

        @JvmStatic
        fun getExchange(): Exchange? {
            return exchangeLocal.get()
        }

        @JvmStatic
        fun getRoutesLocal(): Routes? {
            return getExchange()?.routes
        }
    }
}
