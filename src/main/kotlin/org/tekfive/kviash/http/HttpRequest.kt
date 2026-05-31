package org.tekfive.kviash.http

import org.tekfive.kviash.exchange.ReturnErrorStatus
import org.tekfive.kviash.exchange.throwRedirectTo
import java.io.BufferedReader
import java.io.InputStream
import java.io.Reader
import java.net.URI
import java.net.URL
import kotlin.reflect.KFunction

class HttpRequest(
    val source: HttpRequestSource,
    internal val configuration: org.tekfive.kviash.KviashConfiguration
) {
    val httpProtocol: String
        get() = source.httpProtocol

    val directUrlProtocol: String
        get() = source.urlProtocol

    val urlProtocol: String
        get() = forwardedRequestInfo.urlProtocol

    val forwardedUrlProtocol: String?
        get() = headers.findByName(HttpHeader.XForwardedProto)?.firstValue

    val headers: List<HttpHeader> by lazy {
        source.headers.map { HttpHeader(it.first, it.second) }
    }

    val parameters: List<HttpRequestParameter>
        get() = source.parameters.map { HttpRequestParameter(it.first, it.second) }

    val directHost: String?
        get() = headers.findByName(HttpHeader.Host)?.firstValue

    val host: String?
        get() = forwardedRequestInfo.host

    val forwardedHost: String?
        get() = headers.findByName(HttpHeader.XForwardedHost)?.firstValue

    val forwardedFor: String?
        get() = headers.findByName(HttpHeader.XForwardedFor)?.firstValue

    val directPort: Int
        get() = source.port

    val port: Int
        get() = forwardedRequestInfo.port

    val forwardedPort: Int?
        get() = forwardedPortValue?.toIntOrNull()

    val forwardedPortValue: String?
        get() = headers.findByName(HttpHeader.XForwardedPort)?.firstValue

    val referer: String?
        get() = headers.findByName(HttpHeader.Referer)?.firstValue

    val userAgent: String?
        get() = headers.findByName(HttpHeader.UserAgent)?.firstValue

    val method: HttpMethod? by lazy { HttpMethod.fromName(source.method) }

    val url: URL by lazy {
        val urlProtocol = this.urlProtocol
        val authority = host ?: "NoHostHeaderSent"
        val urlHostName = parseAuthority(authority).host

        val urlPort = if (isDefaultPort(urlProtocol, port)) -1 else port
        URI(
            urlProtocol,
            null,
            urlHostName,
            urlPort,
            path,
            queryString,
            null,
        ).toURL()
    }

    val directClientIp: String
        get() = source.clientIp

    val clientIp: String
        get() = forwardedRequestInfo.clientIp

    val path: String
        get() = source.path

    val queryString: String?
        get() = source.queryString?.let { it.ifBlank { null } }

    val cookies: List<RequestCookie> by lazy {
        source.getCookies()
    }

    val contentType: String?
        get() = headers.findByName(HttpHeader.ContentType)?.firstValue

    val contentLength: Long?
        get() = headers.findByName(HttpHeader.ContentLength)?.firstValue?.toLongOrNull()

    val content: HttpRequestContent? by lazy {
        HttpRequestContent(this)
    }

    val inputStream: InputStream?
        get() = source.inputStream

    val inputReader: Reader?
        get() = source.getInputReader()

    val inputBufferedReader: BufferedReader?
        get() = inputReader?.let {
            it as? BufferedReader ?: BufferedReader(it, configuration.inputBufferSize)
        }

    fun getHeader(name: String): HttpHeader? {
        return headers.findByName(name)
    }

    fun getFirstHeaderValue(name: String): String? {
        return getHeader(name)?.firstValue
    }

    fun getCookie(name: String): RequestCookie? {
        return cookies.find { it.name == name }
    }

    operator fun get(key: String): Any? {
        return source.getAttribute(key)
    }

    operator fun set(key: String, value: Any?) {
        source.setAttribute(key, value)
    }

    fun getSession(createIfNotExists: Boolean = true): HttpSession? {
        return source.getSession(createIfNotExists)
    }

    fun throwRedirectToReferer(defaultRoute: KFunction<*>? = null, vararg parameters: Any): Nothing {
        val referer = referer
        if (referer.isNullOrBlank()) {
            if (defaultRoute == null) {
                ReturnErrorStatus.onBadRequest()
            } else {
                defaultRoute.throwRedirectTo(*parameters)
            }
        } else {
            referer.throwRedirectTo()
        }
    }

    private val forwardedRequestInfo: ForwardedRequestInfo by lazy {
        ForwardedHeaderResolver(configuration.trustedProxyCidrs).resolve(this)
    }

    private fun isDefaultPort(protocol: String, port: Int): Boolean {
        return (protocol.equals("http", ignoreCase = true) && port == 80) ||
            (protocol.equals("https", ignoreCase = true) && port == 443)
    }
}
