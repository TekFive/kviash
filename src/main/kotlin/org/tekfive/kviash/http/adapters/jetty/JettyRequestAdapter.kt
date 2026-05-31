package org.tekfive.kviash.http.adapters.jetty

import org.eclipse.jetty.io.Content
import org.eclipse.jetty.server.Request
import org.tekfive.kviash.http.HttpRequestSource
import org.tekfive.kviash.http.HttpSession
import org.tekfive.kviash.http.RequestCookie
import java.io.InputStream
import java.io.Reader

class JettyRequestAdapter(val jettyRequest: Request) : HttpRequestSource {
    override val urlProtocol: String
        get() = jettyRequest.httpURI.scheme

    override val httpProtocol: String
        get() = jettyRequest.connectionMetaData.protocol

    override val port: Int
        get() = Request.getServerPort(jettyRequest)

    override val method: String
        get() = jettyRequest.method

    override val path: String
        get() = jettyRequest.httpURI.path

    override val queryString: String?
        get() = jettyRequest.httpURI.query

    override val headers: List<Pair<String, List<String>>> by lazy {
        val headerMap = linkedMapOf<String, MutableList<String>>()
        for (field in jettyRequest.headers) {
            headerMap.getOrPut(field.name) { mutableListOf() }.add(field.value)
        }
        headerMap.map { (name, values) -> Pair(name, values.toList()) }
    }

    override val parameters: List<Pair<String, List<String>>>
        get() {
            val fields = Request.getParameters(jettyRequest)
            return fields.names.map { name ->
                Pair(name, fields.get(name)?.values ?: emptyList())
            }
        }

    override val clientIp: String
        get() = Request.getRemoteAddr(jettyRequest)

    override val inputStream: InputStream
        get() = Content.Source.asInputStream(jettyRequest)

    override fun getAttribute(name: String): Any? {
        return jettyRequest.getAttribute(name)
    }

    override fun setAttribute(name: String, value: Any?) {
        jettyRequest.setAttribute(name, value)
    }

    override fun getSession(createIfNotExists: Boolean): HttpSession? {
        return jettyRequest.getSession(createIfNotExists)?.let { JettySessionAdapter(it) }
    }

    override fun getCookies(): List<RequestCookie> {
        return Request.getCookies(jettyRequest).map { RequestCookie(it.name, it.value) }
    }

    override fun getInputReader(): Reader? {
        return inputStream.reader()
    }
}
