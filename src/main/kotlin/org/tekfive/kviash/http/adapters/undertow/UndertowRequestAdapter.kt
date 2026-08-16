package org.tekfive.kviash.http.adapters.undertow

import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.form.FormData
import io.undertow.server.handlers.form.FormDataParser
import io.undertow.server.handlers.form.FormParserFactory
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
        get() {
            val parameters = linkedMapOf<String, MutableList<String>>()
            exchange.queryParameters.forEach { (name, values) ->
                parameters.getOrPut(name) { mutableListOf() }.addAll(values)
            }
            parsedFormData?.let { formData ->
                formData.forEach { name ->
                    parameters.getOrPut(name) { mutableListOf() }.addAll(formDataValues(formData, name))
                }
            }
            return parameters.map { (name, values) -> name to values.toList() }
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

    private val parsedFormData: FormData? by lazy {
        exchange.getAttachment(FormDataParser.FORM_DATA)
            ?: formParserFactory.createParser(exchange)?.use { it.parseBlocking() }
    }

    private fun formDataValues(formData: FormData, name: String): List<String> {
        return formData[name]
            ?.filterNot { it.isFileItem }
            ?.map { it.value }
            ?: emptyList()
    }

    companion object {
        private val formParserFactory = FormParserFactory.builder().build()
    }
}
