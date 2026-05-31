package org.tekfive.kviash.exchange.interceptors

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MaintenanceModeInterceptorTest {

    @Test
    fun `disabled mode continues pipeline`() {
        var continued = false
        MaintenanceModeInterceptor(enabled = { false }).intercept(createTestExchange()) { continued = true }
        assertTrue(continued)
    }

    @Test
    fun `enabled mode returns 503`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        var continued = false
        MaintenanceModeInterceptor(enabled = { true }).intercept(exchange) { continued = true }
        assertTrue(!continued)
        assertEquals(503, rs.status)
        assertEquals("Service temporarily unavailable for maintenance.", rs.bodyText)
    }

    @Test
    fun `custom message is returned`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        MaintenanceModeInterceptor(enabled = { true }, message = "Be right back!").intercept(exchange) { }
        assertEquals("Be right back!", rs.bodyText)
    }

    @Test
    fun `sets content type`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        MaintenanceModeInterceptor(enabled = { true }, contentType = "text/html").intercept(exchange) { }
        assertEquals(listOf("text/html"), rs.headerValues("Content-Type"))
    }

    @Test
    fun `sets Retry-After when configured`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        MaintenanceModeInterceptor(enabled = { true }, retryAfterSeconds = 300).intercept(exchange) { }
        assertEquals(listOf("300"), rs.headerValues("Retry-After"))
    }

    @Test
    fun `no Retry-After when not configured`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        MaintenanceModeInterceptor(enabled = { true }).intercept(exchange) { }
        assertTrue(rs.headerValues("Retry-After").isEmpty())
    }

    @Test
    fun `enabled flag is evaluated per request`() {
        var maintenance = false
        val interceptor = MaintenanceModeInterceptor(enabled = { maintenance })

        var continued = false
        interceptor.intercept(createTestExchange()) { continued = true }
        assertTrue(continued)

        maintenance = true
        val rs = MockResponseSource()
        continued = false
        interceptor.intercept(createTestExchange(responseSource = rs)) { continued = true }
        assertTrue(!continued)
        assertEquals(503, rs.status)
    }
}
