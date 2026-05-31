package org.tekfive.kviash.http

import java.math.BigInteger
import java.net.InetAddress

data class ForwardedRequestInfo(
    val clientIp: String,
    val host: String?,
    val urlProtocol: String,
    val port: Int,
)

class ForwardedHeaderResolver(
    trustedProxies: List<String>,
) {
    private val trustedRanges: List<IpRange> = trustedProxies.mapNotNull { IpRange.parse(it) }

    fun resolve(request: HttpRequest): ForwardedRequestInfo {
        val directClientIp = request.directClientIp
        val directAddress = parseIpAddress(directClientIp)
        val trustedDirectPeer = directAddress != null && trustedRanges.any { it.contains(directAddress) }

        if (!trustedDirectPeer) {
            return ForwardedRequestInfo(
                clientIp = directClientIp,
                host = request.directHost,
                urlProtocol = request.directUrlProtocol,
                port = request.directPort,
            )
        }

        val forwardedElements = forwardedHeaderElements(request)
        val forwardedClientIp = resolveForwardedClientIp(forwardedElements, request.forwardedFor)
        val forwardedHost = (firstForwardedValue(forwardedElements, "host") ?: firstHeaderListValue(request.forwardedHost))
            ?.let(::sanitizeHost)
        val forwardedProtocol = (firstForwardedValue(forwardedElements, "proto") ?: firstHeaderListValue(request.forwardedUrlProtocol))
            ?.let(::sanitizeProtocol)
        val forwardedPort = firstHeaderListValue(request.forwardedPortValue)
            ?.toIntOrNull()
            ?.takeIf { it in 1..65535 }
        val hostPort = forwardedHost?.let(::parseAuthority)?.port ?: request.directHost?.let(::parseAuthority)?.port
        val urlProtocol = forwardedProtocol ?: request.directUrlProtocol

        return ForwardedRequestInfo(
            clientIp = forwardedClientIp ?: directClientIp,
            host = forwardedHost ?: request.directHost,
            urlProtocol = urlProtocol,
            port = forwardedPort ?: hostPort ?: defaultPort(urlProtocol) ?: request.directPort,
        )
    }

    private fun resolveForwardedClientIp(forwardedElements: List<Map<String, String>>, xForwardedFor: String?): String? {
        val forwardedForValues = forwardedElements.mapNotNull { it["for"] }.ifEmpty {
            xForwardedFor
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        }

        val validForwardedAddresses = forwardedForValues.mapNotNull(::parseForwardedAddress)
        if (validForwardedAddresses.isEmpty()) return null

        for (address in validForwardedAddresses.asReversed()) {
            if (trustedRanges.none { it.contains(address) }) {
                return address.hostAddress
            }
        }

        return validForwardedAddresses.first().hostAddress
    }

    private fun forwardedHeaderElements(request: HttpRequest): List<Map<String, String>> {
        return request.headers
            .findByName(HttpHeader.Forwarded)
            ?.values
            ?.flatMap { it.split(",") }
            ?.map { element ->
                element.split(";")
                    .map { it.trim() }
                    .mapNotNull { parameter ->
                        val name = parameter.substringBefore("=", "").trim().lowercase()
                        val value = parameter.substringAfter("=", "").trim().trim('"')
                        if (name.isBlank() || value.isBlank()) null else name to value
                    }
                    .toMap()
            }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
    }

    private fun firstForwardedValue(elements: List<Map<String, String>>, name: String): String? {
        return elements.firstNotNullOfOrNull { it[name]?.takeIf(String::isNotBlank) }
    }

    private fun firstHeaderListValue(value: String?): String? {
        return value
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
}

fun parseAuthority(value: String): ForwardedAuthority {
    val trimmed = value.trim()
    if (trimmed.startsWith("[")) {
        val end = trimmed.indexOf(']')
        if (end > 0) {
            val host = trimmed.substring(1, end)
            val port = trimmed.substring(end + 1).removePrefix(":").toIntOrNull()?.takeIf { it in 1..65535 }
            return ForwardedAuthority(host, port)
        }
    }

    val colonCount = trimmed.count { it == ':' }
    if (colonCount == 1) {
        val host = trimmed.substringBefore(":")
        val port = trimmed.substringAfter(":").toIntOrNull()?.takeIf { it in 1..65535 }
        return ForwardedAuthority(host, port)
    }

    return ForwardedAuthority(trimmed, null)
}

data class ForwardedAuthority(
    val host: String,
    val port: Int?,
)

fun parseForwardedAddress(raw: String): InetAddress? {
    var value = raw.trim().trim('"')
    if (value.equals("unknown", ignoreCase = true)) return null

    if (value.startsWith("[")) {
        val end = value.indexOf(']')
        if (end <= 1) return null
        value = value.substring(1, end)
    } else {
        val colonCount = value.count { it == ':' }
        if (colonCount == 1) {
            value = value.substringBefore(":")
        }
    }

    return parseIpAddress(value)
}

fun parseIpAddress(value: String): InetAddress? {
    val trimmed = value.trim().trim('[', ']')
    if (trimmed.isBlank()) return null
    if (!trimmed.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == '.' || it == ':' }) return null
    return runCatching { InetAddress.getByName(trimmed) }.getOrNull()
}

private fun defaultPort(protocol: String): Int? {
    return when (protocol.lowercase()) {
        "http" -> 80
        "https" -> 443
        else -> null
    }
}

private fun sanitizeProtocol(value: String): String? {
    return value.trim().lowercase().takeIf { it == "http" || it == "https" }
}

private fun sanitizeHost(value: String): String? {
    val trimmed = value.trim().trim('"')
    if (trimmed.isBlank()) return null
    if (trimmed.any { it <= ' ' || it == '/' || it == '\\' }) return null
    return trimmed
}

private data class IpRange(
    val address: BigInteger,
    val prefixLength: Int,
    val byteCount: Int,
) {
    fun contains(candidate: InetAddress): Boolean {
        val candidateBytes = candidate.address
        if (candidateBytes.size != byteCount) return false
        val candidateInt = BigInteger(1, candidateBytes)
        val shift = byteCount * 8 - prefixLength
        return address.shiftRight(shift) == candidateInt.shiftRight(shift)
    }

    companion object {
        fun parse(value: String): IpRange? {
            val trimmed = value.trim()
            if (trimmed.isBlank()) return null

            val addressText = trimmed.substringBefore("/").trim().trim('[', ']')
            val address = parseIpAddress(addressText) ?: return null
            val bitCount = address.address.size * 8
            val prefixLength = if ("/" in trimmed) {
                trimmed.substringAfter("/").toIntOrNull() ?: return null
            } else {
                bitCount
            }
            if (prefixLength !in 0..bitCount) return null

            return IpRange(BigInteger(1, address.address), prefixLength, address.address.size)
        }
    }
}
