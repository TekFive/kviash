package org.tekfive.kviash.http.adapters.undertow

import io.undertow.server.HttpServerExchange
import io.undertow.util.Sessions
import org.tekfive.kviash.http.HttpRequestSource
import org.tekfive.kviash.http.HttpSession
import org.tekfive.kviash.http.RequestCookie
import java.io.InputStream
import java.io.Reader

class UndertowRequestAdapter(val exchange: HttpServerExchange) : HttpRequestSource {
    override val urlProtocol: String
        get() = exchange.requestScheme ?: "http"

    override val httpProtocol: String
        get() = exchange.protocol?.toString() ?: "HTTP/1.1"

    override val port: Int
        get() = exchange.hostPort.let { if (it > 0) it else 80 }

    override val method: String
        get() = exchange.requestMethod.toString()

    override val path: String
        get() = exchange.requestPath

    override val queryString: String?
        get() = exchange.queryString.ifEmpty { null }

    override val headers: List<Pair<String, List<String>>> by lazy {
        exchange.requestHeaders.headerNames.map { name ->
            Pair(name.toString(), exchange.requestHeaders.get(name).toList())
        }
    }

    override val parameters: List<Pair<String, List<String>>>
        get() = exchange.queryParameters.map { (name, values) ->
            Pair(name, values.toList())
        }

    override val clientIp: String
        get() = exchange.sourceAddress.address?.hostAddress ?: "0.0.0.0"

    override val inputStream: InputStream
        get() {
            if (!exchange.isBlocking) exchange.startBlocking()
            return exchange.inputStream
        }

    override fun getAttribute(name: String): Any? {
        return attributes[name]
    }

    override fun setAttribute(name: String, value: Any?) {
        attributes[name] = value
    }

    override fun getSession(createIfNotExists: Boolean): HttpSession? {
        val session = if (createIfNotExists) {
            Sessions.getOrCreateSession(exchange)
        } else {
            Sessions.getSession(exchange)
        }
        return session?.let { UndertowSessionAdapter(it, exchange) }
    }

    override fun getCookies(): List<RequestCookie> {
        return exchange.requestCookies().map { RequestCookie(it.name, it.value) }
    }

    override fun getInputReader(): Reader? {
        return inputStream.reader()
    }

    private val attributes = mutableMapOf<String, Any?>()
}
