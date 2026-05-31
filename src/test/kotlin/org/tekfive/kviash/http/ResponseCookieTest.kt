package org.tekfive.kviash.http

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

class ResponseCookieTest {

    // -----------------------------------------------------------------------
    // Validation
    // -----------------------------------------------------------------------

    @Test
    fun `should reject cookie name with illegal characters`() {
        assertFailsWith<IllegalArgumentException> {
            ResponseCookie(name = "bad cookie", value = "v")
        }
    }

    @Test
    fun `should reject cookie name with semicolon`() {
        assertFailsWith<IllegalArgumentException> {
            ResponseCookie(name = "bad;name", value = "v")
        }
    }

    @Test
    fun `should reject empty cookie name`() {
        assertFailsWith<IllegalArgumentException> {
            ResponseCookie(name = "", value = "v")
        }
    }

    @Test
    fun `should reject cookie value with semicolon`() {
        assertFailsWith<IllegalArgumentException> {
            ResponseCookie(name = "ok", value = "has;semi")
        }
    }

    @Test
    fun `should reject path with semicolon`() {
        assertFailsWith<IllegalArgumentException> {
            ResponseCookie(name = "ok", value = "v", path = "/bad;path")
        }
    }

    @Test
    fun `should reject domain with semicolon`() {
        assertFailsWith<IllegalArgumentException> {
            ResponseCookie(name = "ok", value = "v", domain = "bad;domain")
        }
    }

    @Test
    fun `should accept valid cookie name characters`() {
        val cookie = ResponseCookie(name = "my_cookie-123.v2", value = "test")
        assertEquals("my_cookie-123.v2", cookie.name)
    }

    // -----------------------------------------------------------------------
    // toSetCookieHeader
    // -----------------------------------------------------------------------

    @Test
    fun `should produce minimal Set-Cookie header`() {
        val cookie = ResponseCookie(name = "id", value = "abc")
        assertEquals("id=abc", cookie.toSetCookieHeader())
    }

    @Test
    fun `should include Domain attribute`() {
        val cookie = ResponseCookie(name = "id", value = "abc", domain = "example.com")
        val header = cookie.toSetCookieHeader()
        assertTrue(header.contains("Domain=example.com"))
    }

    @Test
    fun `should include Path attribute`() {
        val cookie = ResponseCookie(name = "id", value = "abc", path = "/app")
        val header = cookie.toSetCookieHeader()
        assertTrue(header.contains("Path=/app"))
    }

    @Test
    fun `should include Max-Age in seconds`() {
        val cookie = ResponseCookie(name = "id", value = "abc", maxAge = 1.hours)
        val header = cookie.toSetCookieHeader()
        assertTrue(header.contains("Max-Age=3600"))
    }

    @Test
    fun `should include Expires formatted as HTTP date`() {
        val expires = Instant.parse("2025-06-15T10:30:00Z")
        val cookie = ResponseCookie(name = "id", value = "abc", expires = expires)
        val header = cookie.toSetCookieHeader()
        assertTrue(header.contains("Expires=Sun, 15 Jun 2025 10:30:00 GMT"))
    }

    @Test
    fun `should include Secure flag when true`() {
        val cookie = ResponseCookie(name = "id", value = "abc", secure = true)
        assertTrue(cookie.toSetCookieHeader().contains("Secure"))
    }

    @Test
    fun `should not include Secure flag when false`() {
        val cookie = ResponseCookie(name = "id", value = "abc", secure = false)
        assertTrue(!cookie.toSetCookieHeader().contains("Secure"))
    }

    @Test
    fun `should include HttpOnly flag when true`() {
        val cookie = ResponseCookie(name = "id", value = "abc", httpOnly = true)
        assertTrue(cookie.toSetCookieHeader().contains("HttpOnly"))
    }

    @Test
    fun `should include SameSite attribute`() {
        for (sameSite in SameSite.entries) {
            val cookie = ResponseCookie(name = "id", value = "abc", sameSite = sameSite)
            assertTrue(cookie.toSetCookieHeader().contains("SameSite=${sameSite.token}"))
        }
    }

    @Test
    fun `should include Partitioned flag when true`() {
        val cookie = ResponseCookie(name = "id", value = "abc", partitioned = true)
        assertTrue(cookie.toSetCookieHeader().contains("Partitioned"))
    }

    @Test
    fun `should produce full header with all attributes`() {
        val cookie = ResponseCookie(
            name = "session",
            value = "xyz",
            path = "/",
            domain = "example.com",
            maxAge = 1.days,
            expires = Instant.parse("2025-06-15T00:00:00Z"),
            secure = true,
            httpOnly = true,
            sameSite = SameSite.Strict,
            partitioned = true,
        )
        val header = cookie.toSetCookieHeader()
        assertEquals(
            "session=xyz; Domain=example.com; Path=/; Max-Age=86400; Expires=Sun, 15 Jun 2025 00:00:00 GMT; Secure; HttpOnly; SameSite=Strict; Partitioned",
            header
        )
    }

    @Test
    fun `should not include blank domain`() {
        val cookie = ResponseCookie(name = "id", value = "abc", domain = "")
        assertTrue(!cookie.toSetCookieHeader().contains("Domain"))
    }

    @Test
    fun `should not include blank path`() {
        val cookie = ResponseCookie(name = "id", value = "abc", path = "")
        assertTrue(!cookie.toSetCookieHeader().contains("Path"))
    }

    // -----------------------------------------------------------------------
    // Factory methods
    // -----------------------------------------------------------------------

    @Test
    fun `session factory creates cookie without Max-Age or Expires`() {
        val cookie = ResponseCookie.session("sid", "abc123")
        val header = cookie.toSetCookieHeader()
        assertTrue(!header.contains("Max-Age"))
        assertTrue(!header.contains("Expires"))
        assertTrue(header.contains("HttpOnly"))
        assertTrue(header.contains("SameSite=Lax"))
        assertTrue(header.contains("Path=/"))
    }

    @Test
    fun `persistent factory creates cookie with Max-Age and Expires`() {
        val fixedClock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC)
        val cookie = ResponseCookie.persistent("pref", "dark", ttl = 7.days, clock = fixedClock)
        val header = cookie.toSetCookieHeader()
        assertTrue(header.contains("Max-Age=604800"))
        assertTrue(header.contains("Expires=Wed, 08 Jan 2025 00:00:00 GMT"))
    }

    @Test
    fun `delete factory creates cookie with Max-Age zero and epoch Expires`() {
        val cookie = ResponseCookie.delete("sid")
        val header = cookie.toSetCookieHeader()
        assertTrue(header.contains("Max-Age=0"))
        assertTrue(header.contains("Expires=Thu, 01 Jan 1970 00:00:00 GMT"))
        assertEquals("", cookie.value)
    }
}

class RequestCookieTest {

    // -----------------------------------------------------------------------
    // parseHeader(String)
    // -----------------------------------------------------------------------

    @Test
    fun `should parse single cookie`() {
        val cookies = RequestCookie.parseHeader("session=abc123")
        assertEquals(1, cookies.size)
        assertEquals("session", cookies[0].name)
        assertEquals("abc123", cookies[0].value)
    }

    @Test
    fun `should parse multiple cookies`() {
        val cookies = RequestCookie.parseHeader("a=1; b=2; c=3")
        assertEquals(3, cookies.size)
        assertEquals("a", cookies[0].name)
        assertEquals("1", cookies[0].value)
        assertEquals("c", cookies[2].name)
        assertEquals("3", cookies[2].value)
    }

    @Test
    fun `should handle cookie value containing equals sign`() {
        val cookies = RequestCookie.parseHeader("token=abc=def=ghi")
        assertEquals(1, cookies.size)
        assertEquals("token", cookies[0].name)
        assertEquals("abc=def=ghi", cookies[0].value)
    }

    @Test
    fun `should return empty list for blank header`() {
        assertEquals(emptyList(), RequestCookie.parseHeader(""))
        assertEquals(emptyList(), RequestCookie.parseHeader("   "))
    }

    @Test
    fun `should skip malformed entries without equals`() {
        val cookies = RequestCookie.parseHeader("valid=yes; malformed; also=ok")
        assertEquals(2, cookies.size)
        assertEquals("valid", cookies[0].name)
        assertEquals("also", cookies[1].name)
    }

    @Test
    fun `should handle trailing semicolons`() {
        val cookies = RequestCookie.parseHeader("a=1; ")
        assertEquals(1, cookies.size)
    }

    // -----------------------------------------------------------------------
    // parseHeader(NamedMultiStringValue)
    // -----------------------------------------------------------------------

    @Test
    fun `should parse from HttpHeader with Cookie name`() {
        val header = HttpHeader("Cookie", listOf("a=1; b=2", "c=3"))
        val cookies = RequestCookie.parseHeader(header)
        assertEquals(3, cookies.size)
    }

    @Test
    fun `should reject header with non-Cookie name`() {
        val header = HttpHeader("Authorization", "Bearer token")
        assertFailsWith<IllegalArgumentException> {
            RequestCookie.parseHeader(header)
        }
    }

    // -----------------------------------------------------------------------
    // equals and hashCode
    // -----------------------------------------------------------------------

    @Test
    fun `equal cookies should have same hashCode`() {
        val c1 = RequestCookie("name", "value")
        val c2 = RequestCookie("name", "value")
        assertEquals(c1, c2)
        assertEquals(c1.hashCode(), c2.hashCode())
    }
}
