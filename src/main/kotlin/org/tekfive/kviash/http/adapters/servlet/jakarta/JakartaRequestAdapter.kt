package org.tekfive.kviash.http.adapters.servlet.jakarta

import jakarta.servlet.http.HttpServletRequest
import org.tekfive.kviash.http.HttpRequestSource
import org.tekfive.kviash.http.HttpSession
import org.tekfive.kviash.http.RequestCookie
import java.io.InputStream
import java.io.Reader

class JakartaRequestAdapter(val servletRequest: HttpServletRequest) : HttpRequestSource {
    override val urlProtocol: String
        get() = servletRequest.scheme

    override val httpProtocol: String
        get() = servletRequest.protocol

    override val port: Int
        get() = servletRequest.serverPort

    override val method: String
        get() = servletRequest.method

    override val path: String
        get() = servletRequest.requestURI

    override val queryString: String?
        get() = servletRequest.queryString

    override val headers: List<Pair<String, List<String>>> by lazy {
        servletRequest.headerNames.toList().map { Pair(it, servletRequest.getHeaders(it).toList()) }
    }

    override val parameters: List<Pair<String, List<String>>>
        get() = servletRequest.parameterMap.entries.map { (k, v) -> Pair(k, v.toList()) }

    override val clientIp: String
        get() = servletRequest.remoteAddr

    override val inputStream: InputStream
        get() = servletRequest.inputStream

    override fun getAttribute(name: String): Any? {
        return servletRequest.getAttribute(name)
    }

    override fun setAttribute(name: String, value: Any?) {
        servletRequest.setAttribute(name, value)
    }

    override fun getSession(createIfNotExists: Boolean): HttpSession? {
        return servletRequest.getSession(createIfNotExists)?.let { JakartaSessionAdapter(it) }
    }

    override fun getCookies(): List<RequestCookie> {
        return servletRequest.cookies?.map { RequestCookie(it.name, it.value) } ?: emptyList()
    }

    override fun getInputReader(): Reader? {
        return servletRequest.reader
    }
}