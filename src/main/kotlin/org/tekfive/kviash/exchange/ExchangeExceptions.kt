package org.tekfive.kviash.exchange

import org.tekfive.kviash.isUrl
import kotlin.reflect.KFunction

sealed class ExchangeException(message: String) : Exception(message)

/**
 * Indicates that the exchange should be terminated without anymore processing.
 */
class TerminateExchangeException(message: String = "${Exchange.getExchange()} terminated.") : ExchangeException(message) {
}

enum class HttpErrorCode(val code: Int, val description: String) {
    // --- 4xx Client Errors ---
    BAD_REQUEST(400, "Bad Request"),
    UNAUTHORIZED(401, "Unauthorized"),
    PAYMENT_REQUIRED(402, "Payment Required"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Not Found"),
    METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
    NOT_ACCEPTABLE(406, "Not Acceptable"),
    PROXY_AUTHENTICATION_REQUIRED(407, "Proxy Authentication Required"),
    REQUEST_TIMEOUT(408, "Request Timeout"),
    CONFLICT(409, "Conflict"),
    GONE(410, "Gone"),
    LENGTH_REQUIRED(411, "Length Required"),
    PRECONDITION_FAILED(412, "Precondition Failed"),
    PAYLOAD_TOO_LARGE(413, "Payload Too Large"),
    URI_TOO_LONG(414, "URI Too Long"),
    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported Media Type"),
    RANGE_NOT_SATISFIABLE(416, "Range Not Satisfiable"),
    EXPECTATION_FAILED(417, "Expectation Failed"),
    IM_A_TEAPOT(418, "I'm a teapot"),
    MISDIRECTED_REQUEST(421, "Misdirected Request"),
    UNPROCESSABLE_ENTITY(422, "Unprocessable Entity"),
    LOCKED(423, "Locked"),
    FAILED_DEPENDENCY(424, "Failed Dependency"),
    TOO_EARLY(425, "Too Early"),
    UPGRADE_REQUIRED(426, "Upgrade Required"),
    PRECONDITION_REQUIRED(428, "Precondition Required"),
    TOO_MANY_REQUESTS(429, "Too Many Requests"),
    REQUEST_HEADER_FIELDS_TOO_LARGE(431, "Request Header Fields Too Large"),
    UNAVAILABLE_FOR_LEGAL_REASONS(451, "Unavailable For Legal Reasons"),

    // --- 5xx Server Errors ---
    INTERNAL_SERVER_ERROR(500, "Internal Server Error"),
    NOT_IMPLEMENTED(501, "Not Implemented"),
    BAD_GATEWAY(502, "Bad Gateway"),
    SERVICE_UNAVAILABLE(503, "Service Unavailable"),
    GATEWAY_TIMEOUT(504, "Gateway Timeout"),
    HTTP_VERSION_NOT_SUPPORTED(505, "HTTP Version Not Supported"),
    VARIANT_ALSO_NEGOTIATES(506, "Variant Also Negotiates"),
    INSUFFICIENT_STORAGE(507, "Insufficient Storage"),
    LOOP_DETECTED(508, "Loop Detected"),
    NOT_EXTENDED(510, "Not Extended"),
    NETWORK_AUTHENTICATION_REQUIRED(511, "Network Authentication Required");

    val clientError: Boolean = code in 400..499
    val serverError: Boolean = code in 500..599

    override fun toString(): String {
        return "$code ($description)"
    }

    companion object {
        fun fromCode(code: Int): HttpErrorCode? = entries.find { it.code == code }
    }
}

val Int.isHttpSuccess: Boolean
    get() = this in 200..299

val Int.isHttpRedirect: Boolean
    get() = this in 300..399

val Int.isHttpClientError: Boolean
    get() = this in 400..499

val Int.isHttpServerError: Boolean
    get() = this in 500..599

val Int.isHttpError: Boolean
    get() = isHttpClientError || isHttpServerError


open class ReturnErrorStatus(
    val errorCode: HttpErrorCode,
    val body: String? = null,
    val contentType: String? = null,
    exceptionMessage: String = errorCode.toString(),
) : ExchangeException(exceptionMessage) {
    companion object {
        fun onNotFound(body: String? = null, message: String = HttpErrorCode.NOT_FOUND.toString()): Nothing {
            throw ReturnErrorStatus(HttpErrorCode.NOT_FOUND, body, message)
        }

        fun onBadRequest(body: String? = null, message: String = HttpErrorCode.BAD_REQUEST.toString()): Nothing {
            throw ReturnErrorStatus(HttpErrorCode.BAD_REQUEST, body, message)
        }

        fun onUnauthorized(body: String? = null, message: String = HttpErrorCode.UNAUTHORIZED.toString()): Nothing {
            throw ReturnErrorStatus(HttpErrorCode.UNAUTHORIZED, body, message)
        }

        fun onForbidden(body: String? = null, message: String = HttpErrorCode.FORBIDDEN.toString()): Nothing {
            throw ReturnErrorStatus(HttpErrorCode.FORBIDDEN, body, message)
        }

        fun onServerError(body: String? = null, message: String = HttpErrorCode.INTERNAL_SERVER_ERROR.toString()): Nothing {
            throw ReturnErrorStatus(HttpErrorCode.INTERNAL_SERVER_ERROR, body, message)
        }
    }
}

/**
 * Throws RedirectTo exception. Return type is so it can be used like the following to let compiler know this is a terminal statement.
 *
 * throw function.redirectTo()
 */
fun KFunction<*>.throwRedirectTo(vararg parameters: Any): Nothing {
    throw RedirectTo(this, *parameters)
}

/**
 * Throws RedirectTo exception. Return type is so it can be used like the following to let compiler know this is a terminal statement.
 *
 * throw path.redirectTo()
 */
fun String.throwRedirectTo(): Nothing {
    throw RedirectTo(this)
}

class RedirectTo private constructor(
    val routeFunction: KFunction<*>?,
    val routePathOrURL: String?,
    val parameters: List<Any>,
    val redirectType: RedirectType = RedirectType.FOUND,
) : ExchangeException("Redirect to ${routeFunction ?: routePathOrURL}") {

    init {
        check(routeFunction != null || routePathOrURL != null) {"Route function and path cannot be null."}
    }

    constructor(method: KFunction<*>, vararg parameters: Any, redirectType: RedirectType = RedirectType.FOUND) : this(method, null, parameters.toList(), redirectType)

    constructor(path: String, vararg parameters: List<Any>, redirectType: RedirectType = RedirectType.FOUND) : this(null, path, parameters.toList(), redirectType)

    fun sendRedirect(exchange: Exchange) {
        val redirectUrl = if (routeFunction != null) {
            exchange.routes.getUrl(routeFunction, *parameters.toTypedArray())
        } else {
            val routePathOrURL = routePathOrURL!!
            if (routePathOrURL.isUrl) {
                routePathOrURL
            } else {
                exchange.routes.getUrl(routePathOrURL)
            }
        }

        exchange.response.sendRedirect(redirectUrl, redirectType.code)
    }

    companion object {
        const val RedirectPrefix = "redirect:"

        fun addPrefix(path: String): String {
            return if (path.startsWith(RedirectPrefix, true)) {
                path
            } else {
                RedirectPrefix + path
            }
        }

        fun hasRedirectPrefix(path: String): Boolean {
            return path.startsWith(RedirectPrefix)
        }

        fun removeRedirectPrefix(path: String): String {
            return path.removePrefix(RedirectPrefix)
        }
    }
}

enum class RedirectType(val code: Int) {
    MOVED_PERMANENTLY(301),
    FOUND(302),
    SEE_OTHER(303),
    TEMPORARY_REDIRECT(307),
    PERMANENT_REDIRECT(308),
}