package org.tekfive.kviash.http

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.toJavaDuration

class ResponseCookie (
    val name: String,
    val value: String,
    val path: String? = null,
    val domain: String? = null,
    val maxAge: Duration? = null,
    val expires: Instant? = null,
    val secure: Boolean? = null,
    val httpOnly: Boolean? = null,
    val sameSite: SameSite? = null,
    val partitioned: Boolean? = null
) {
    init {
        require(isToken(name)) { "Cookie name contains illegal characters: $name" }
        require(!value.contains(';')) { "Cookie value must not contain ';' (encode if needed)" }
        path?.let { require(!it.contains(';')) { "Path must not contain ';'" } }
        domain?.let { require(!it.contains(';')) { "Domain must not contain ';'" } }
    }

    fun toSetCookieHeader(): String {
        val parts = mutableListOf<String>()

        parts += "${name}=${value}"

        if (!domain.isNullOrBlank()) {
            parts += "Domain=$domain"
        }

        if (!path.isNullOrBlank()) {
            parts += "Path=$path"
        }

        if (maxAge != null) {
            parts += "Max-Age=${maxAge.inWholeSeconds}"
        }

        if (expires != null) {
            parts += "Expires=${httpDate(expires)}"
        }

        if (secure != null && secure) {
            parts += "Secure"
        }

        if (httpOnly != null && httpOnly) {
            parts += "HttpOnly"
        }

        if (sameSite != null) {
            parts += "SameSite=${sameSite.token}"
        }

        if (partitioned != null && partitioned) {
            parts += "Partitioned"
        }

        return parts.joinToString(separator = "; ")
    }

    companion object {
        private val httpDateFmt: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)
                .withZone(ZoneOffset.UTC)

        private fun httpDate(instant: Instant): String = httpDateFmt.format(instant)

        private fun isToken(s: String): Boolean {
            // RFC 6265 token-ish: visible ASCII excluding separators/CTLs
            // Simple, pragmatic check (letters, digits, and these symbols)
            val allowed = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!#$%&'*+-.^_`|~"
            return s.isNotEmpty() && s.all { it in allowed }
        }

        /** Create a session cookie (no Max-Age/Expires). */
        fun session(
            name: String,
            value: String,
            path: String? = "/",
            domain: String? = null,
            secure: Boolean = false,
            httpOnly: Boolean = true,
            sameSite: SameSite? = SameSite.Lax,
            partitioned: Boolean = false
        ): ResponseCookie =
            ResponseCookie(
                name = name,
                value = value,
                path = path,
                domain = domain,
                secure = secure,
                httpOnly = httpOnly,
                sameSite = sameSite,
                partitioned = partitioned
            )

        /** Create a persistent cookie that expires in [ttl]. */
        fun persistent(
            name: String,
            value: String,
            ttl: Duration,
            path: String? = "/",
            domain: String? = null,
            secure: Boolean = false,
            httpOnly: Boolean = true,
            sameSite: SameSite? = SameSite.Lax,
            partitioned: Boolean = false,
            clock: Clock = Clock.systemUTC()
        ): ResponseCookie {
            val maxAge = ttl
            val expires = clock.instant().plus(maxAge.toJavaDuration())
            return ResponseCookie(
                name = name,
                value = value,
                path = path,
                domain = domain,
                maxAge = maxAge,
                expires = expires,
                secure = secure,
                httpOnly = httpOnly,
                sameSite = sameSite,
                partitioned = partitioned
            )
        }

        /** Create a deletion cookie (Max-Age=0; Expires in the past). */
        fun delete(
            name: String,
            path: String? = "/",
            domain: String? = null,
            secure: Boolean = false,
            httpOnly: Boolean = true,
            sameSite: SameSite? = SameSite.Lax
        ): ResponseCookie =
            ResponseCookie(
                name = name,
                value = "",
                path = path,
                domain = domain,
                maxAge = Duration.Companion.ZERO,
                expires = Instant.EPOCH, // 1970-01-01, universally "in the past"
                secure = secure,
                httpOnly = httpOnly,
                sameSite = sameSite
            )
    }
}

enum class SameSite(internal val token: String) {
    Lax("Lax"),
    Strict("Strict"),
    None("None");
}