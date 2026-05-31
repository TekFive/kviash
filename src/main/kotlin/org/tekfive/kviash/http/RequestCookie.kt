package org.tekfive.kviash.http

class RequestCookie(
    val name: String,
    val value: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RequestCookie

        if (name != other.name) return false
        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }

    companion object {
        fun parseHeader(header: org.tekfive.kviash.http.NamedMultiStringValue): List<RequestCookie> {
            require(header.name.equals("Cookie", true)) { "Cookie header cannot have name: ${header.name}" }
            return header.values.map { parseHeader(it) }.flatten()
        }

        fun parseHeader(headerValue: String): List<RequestCookie> {
            if (headerValue.isBlank()) {
                return emptyList()
            }

            val cookies = mutableListOf<RequestCookie>()

            for (cookieValue in headerValue.split(";").mapNotNull { it.trim().ifEmpty { null }}) {
                val parts = cookieValue.split("=", limit = 2)
                if (parts.size == 2) {
                    cookies.add(RequestCookie(parts[0], parts[1]))
                }
            }
            return cookies
        }
    }
}