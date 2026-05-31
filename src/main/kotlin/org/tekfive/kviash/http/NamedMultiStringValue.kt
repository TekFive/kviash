package org.tekfive.kviash.http

fun List<HttpHeader>.anyByName(name: String): Boolean {
    return any { it.name.equals(name, true) }
}

fun List<HttpHeader>.findByName(name: String): HttpHeader? {
    return this.firstOrNull { it.name.equals(name, true) }
}

fun String.toHttpHeader(value: String): HttpHeader {
    return HttpHeader(this, value)
}

class HttpHeader(name: String, values: List<String>) : NamedMultiStringValue("header", name, values) {
    val delimitedValue: String by lazy {
        values.joinToString(", ")
    }

    constructor(name: String, value: String) : this(name, listOf(value))

    operator fun plus(header: NamedMultiStringValue): HttpHeader {
        return HttpHeader(name, values + header.values)
    }

    companion object {
        // Common HTTP Request Headers
        const val Accept = "Accept"
        const val AcceptCharset = "Accept-Charset"
        const val AcceptEncoding = "Accept-Encoding"
        const val AcceptLanguage = "Accept-Language"
        const val Authorization = "Authorization"
        const val CacheControl = "Cache-Control"
        const val Connection = "Connection"
        const val Cookie = "Cookie"
        const val ContentLength = "Content-Length"
        const val ContentType = "Content-Type"
        const val Date = "Date"
        const val Expect = "Expect"
        const val Forwarded = "Forwarded"
        const val From = "From"
        const val Host = "Host"
        const val IfMatch = "If-Match"
        const val IfModifiedSince = "If-Modified-Since"
        const val IfNoneMatch = "If-None-Match"
        const val IfRange = "If-Range"
        const val IfUnmodifiedSince = "If-Unmodified-Since"
        const val MaxForwards = "Max-Forwards"
        const val Origin = "Origin"
        const val Pragma = "Pragma"
        const val ProxyAuthorization = "Proxy-Authorization"
        const val Range = "Range"
        const val Referer = "Referer"
        const val UserAgent = "User-Agent"
        const val Upgrade = "Upgrade"
        const val Via = "Via"
        const val Warning = "Warning"

        // X-Prefixed HTTP Request Headers (Common Extensions)
        const val XForwardedFor = "X-Forwarded-For"
        const val XForwardedHost = "X-Forwarded-Host"
        const val XForwardedProto = "X-Forwarded-Proto"
        const val XForwardedPort = "X-Forwarded-Port"
        const val XForwardedServer = "X-Forwarded-Server"
        const val XRealIP = "X-Real-IP"
        const val XClientIP = "X-Client-IP"
        const val XClusterClientIP = "X-Cluster-Client-IP"
        const val XRequestedWith = "X-Requested-With"
        const val XHttpMethodOverride = "X-HTTP-Method-Override"
        const val XOriginalMethod = "X-Original-Method"
        const val XOriginalURL = "X-Original-URL"
        const val XRewriteURL = "X-Rewrite-URL"
        const val XCSRFToken = "X-CSRF-Token"
        const val XXSRFToken = "X-XSRF-Token"
        const val XAPIKey = "X-API-Key"
        const val XAuthToken = "X-Auth-Token"
        const val XRequestID = "X-Request-ID"
        const val XCorrelationID = "X-Correlation-ID"
        const val XTraceID = "X-Trace-ID"
        const val XCache = "X-Cache"
        const val XCacheLookup = "X-Cache-Lookup"
        const val XResponseTime = "X-Response-Time"
        const val XProxyCache = "X-Proxy-Cache"
        const val XDeviceID = "X-Device-ID"
        const val XAppVersion = "X-App-Version"
        const val XAppPlatform = "X-App-Platform"

        // Common HTTP Response Headers
        const val AcceptRanges = "Accept-Ranges"
        const val AccessControlAllowOrigin = "Access-Control-Allow-Origin"
        const val Age = "Age"
        const val Allow = "Allow"
        const val ContentEncoding = "Content-Encoding"
        const val ContentLanguage = "Content-Language"
        const val ContentLocation = "Content-Location"
        const val ContentDisposition = "Content-Disposition"
        const val ETag = "ETag"
        const val Expires = "Expires"
        const val LastModified = "Last-Modified"
        const val Location = "Location"
        const val ProxyAuthenticate = "Proxy-Authenticate"
        const val RetryAfter = "Retry-After"
        const val Server = "Server"
        const val SetCookie = "Set-Cookie"
        const val Vary = "Vary"
        const val WWWAuthenticate = "WWW-Authenticate"
    }
}

fun List<HttpRequestParameter>.findByName(name: String): HttpRequestParameter? {
    return this.firstOrNull { it.name == name }
}

class HttpRequestParameter(name: String, values: List<String>) : NamedMultiStringValue("parameter", name, values) {
    constructor(name: String, value: String) : this(name, listOf(value))

    operator fun plus(header: NamedMultiStringValue): HttpRequestParameter {
        return HttpRequestParameter(name, values + header.values)
    }
}

sealed class NamedMultiStringValue(
    private val type: String,
    val name: String,
    val values: List<String>
) {

    val firstValue: String? = values.firstOrNull()

    fun hasValue(value: String, ignoreCase: Boolean = false): Boolean {
        return values.any { it.equals(value, ignoreCase) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NamedMultiStringValue

        if (name != other.name) return false
        if (values != other.values) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + values.hashCode()
        return result
    }
}