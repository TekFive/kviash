package org.tekfive.kviash.exchange.actions.security

import org.tekfive.kviash.exchange.interceptors.MockResponseSource
import org.tekfive.kviash.exchange.interceptors.createTestExchange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SecurityHeadersActionTest {

    @Test
    fun `default instance sets X-Content-Type-Options`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        SecurityHeadersAction().invoke(exchange)
        assertEquals(listOf("nosniff"), rs.headerValues("X-Content-Type-Options"))
    }

    @Test
    fun `default instance sets X-Frame-Options to DENY`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        SecurityHeadersAction().invoke(exchange)
        assertEquals(listOf("DENY"), rs.headerValues("X-Frame-Options"))
    }

    @Test
    fun `default instance sets X-XSS-Protection to 0`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        SecurityHeadersAction().invoke(exchange)
        assertEquals(listOf("0"), rs.headerValues("X-XSS-Protection"))
    }

    @Test
    fun `default instance sets Referrer-Policy`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        SecurityHeadersAction().invoke(exchange)
        assertEquals(listOf("strict-origin-when-cross-origin"), rs.headerValues("Referrer-Policy"))
    }

    @Test
    fun `default instance does not set Content-Security-Policy`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        SecurityHeadersAction().invoke(exchange)
        assertTrue(rs.headerValues("Content-Security-Policy").isEmpty())
    }

    @Test
    fun `default instance does not set Strict-Transport-Security`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        SecurityHeadersAction().invoke(exchange)
        assertTrue(rs.headerValues("Strict-Transport-Security").isEmpty())
    }

    @Test
    fun `custom Content-Security-Policy is set`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        SecurityHeadersAction(contentSecurityPolicy = "default-src 'self'").invoke(exchange)
        assertEquals(listOf("default-src 'self'"), rs.headerValues("Content-Security-Policy"))
    }

    @Test
    fun `custom Strict-Transport-Security is set`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        SecurityHeadersAction(strictTransportSecurity = "max-age=31536000; includeSubDomains").invoke(exchange)
        assertEquals(listOf("max-age=31536000; includeSubDomains"), rs.headerValues("Strict-Transport-Security"))
    }

    @Test
    fun `custom Permissions-Policy is set`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        SecurityHeadersAction(permissionsPolicy = "camera=(), microphone=()").invoke(exchange)
        assertEquals(listOf("camera=(), microphone=()"), rs.headerValues("Permissions-Policy"))
    }

    @Test
    fun `frame options SAMEORIGIN`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        SecurityHeadersAction(frameOptions = FrameOption.SAMEORIGIN).invoke(exchange)
        assertEquals(listOf("SAMEORIGIN"), rs.headerValues("X-Frame-Options"))
    }

    @Test
    fun `referrer policy no-referrer`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        SecurityHeadersAction(referrerPolicy = ReferrerPolicy.NO_REFERRER).invoke(exchange)
        assertEquals(listOf("no-referrer"), rs.headerValues("Referrer-Policy"))
    }

    @Test
    fun `disabling contentTypeOptions omits header`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        SecurityHeadersAction(contentTypeOptions = false).invoke(exchange)
        assertTrue(rs.headerValues("X-Content-Type-Options").isEmpty())
    }

    @Test
    fun `disabling frameOptions omits header`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        SecurityHeadersAction(frameOptions = null).invoke(exchange)
        assertTrue(rs.headerValues("X-Frame-Options").isEmpty())
    }

    @Test
    fun `disabling xssProtection omits header`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        SecurityHeadersAction(xssProtection = false).invoke(exchange)
        assertTrue(rs.headerValues("X-XSS-Protection").isEmpty())
    }

    @Test
    fun `disabling referrerPolicy omits header`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        SecurityHeadersAction(referrerPolicy = null).invoke(exchange)
        assertTrue(rs.headerValues("Referrer-Policy").isEmpty())
    }

    @Test
    fun `Cross-Origin-Opener-Policy same-origin`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        SecurityHeadersAction(crossOriginOpenerPolicy = CrossOriginOpenerPolicy.SAME_ORIGIN).invoke(exchange)
        assertEquals(listOf("same-origin"), rs.headerValues("Cross-Origin-Opener-Policy"))
    }

    @Test
    fun `Cross-Origin-Resource-Policy same-origin`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        SecurityHeadersAction(crossOriginResourcePolicy = CrossOriginResourcePolicy.SAME_ORIGIN).invoke(exchange)
        assertEquals(listOf("same-origin"), rs.headerValues("Cross-Origin-Resource-Policy"))
    }

    @Test
    fun `Cross-Origin-Opener-Policy same-origin-allow-popups`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        SecurityHeadersAction(crossOriginOpenerPolicy = CrossOriginOpenerPolicy.SAME_ORIGIN_ALLOW_POPUPS).invoke(exchange)
        assertEquals(listOf("same-origin-allow-popups"), rs.headerValues("Cross-Origin-Opener-Policy"))
    }

    @Test
    fun `Cross-Origin-Resource-Policy cross-origin`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        SecurityHeadersAction(crossOriginResourcePolicy = CrossOriginResourcePolicy.CROSS_ORIGIN).invoke(exchange)
        assertEquals(listOf("cross-origin"), rs.headerValues("Cross-Origin-Resource-Policy"))
    }

    @Test
    fun `companion instance uses defaults`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        SecurityHeadersAction.instance.invoke(exchange)
        assertEquals(listOf("nosniff"), rs.headerValues("X-Content-Type-Options"))
        assertEquals(listOf("DENY"), rs.headerValues("X-Frame-Options"))
    }

    @Test
    fun `all headers disabled produces no security headers`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        SecurityHeadersAction(
            contentTypeOptions = false,
            frameOptions = null,
            xssProtection = false,
            referrerPolicy = null,
        ).invoke(exchange)
        assertTrue(rs.headerValues("X-Content-Type-Options").isEmpty())
        assertTrue(rs.headerValues("X-Frame-Options").isEmpty())
        assertTrue(rs.headerValues("X-XSS-Protection").isEmpty())
        assertTrue(rs.headerValues("Referrer-Policy").isEmpty())
    }
}
