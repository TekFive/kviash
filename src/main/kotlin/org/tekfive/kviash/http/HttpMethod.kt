package org.tekfive.kviash.http

enum class HttpMethod(val canHaveBody: Boolean) {

    GET(false),
    HEAD(false),
    POST(true),
    PUT(true),
    DELETE(false),
    CONNECT(false),
    OPTIONS(false),
    TRACE(false),
    PATCH(false),
    ;

    companion object {
        fun fromName(methodName: String?): HttpMethod? {
            return methodName?.let { method -> entries.firstOrNull { it.toString().equals(method, true) }}
        }
    }
}