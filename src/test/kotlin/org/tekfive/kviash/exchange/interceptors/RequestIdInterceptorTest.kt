package org.tekfive.kviash.exchange.interceptors

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RequestIdInterceptorTest {

    @Test
    fun `generates request ID when none present`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        RequestIdInterceptor().intercept(exchange) { }
        val id = exchange.request[RequestIdInterceptor.RequestIdAttribute] as String
        assertNotNull(id)
        assertTrue(id.isNotBlank())
        assertEquals(listOf(id), rs.headerValues("X-Request-ID"))
    }

    @Test
    fun `propagates existing request ID`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(
            MockRequestSource(headers = listOf("Host" to listOf("localhost"), "X-Request-ID" to listOf("abc-123"))),
            rs
        )
        RequestIdInterceptor().intercept(exchange) { }
        assertEquals("abc-123", exchange.request[RequestIdInterceptor.RequestIdAttribute])
        assertEquals(listOf("abc-123"), rs.headerValues("X-Request-ID"))
    }

    @Test
    fun `does not include in response when disabled`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        RequestIdInterceptor(includeInResponse = false).intercept(exchange) { }
        assertNotNull(exchange.request[RequestIdInterceptor.RequestIdAttribute])
        assertTrue(rs.headerValues("X-Request-ID").isEmpty())
    }

    @Test
    fun `custom header name`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(
            MockRequestSource(headers = listOf("Host" to listOf("localhost"), "X-Trace-ID" to listOf("trace-1"))),
            rs
        )
        RequestIdInterceptor(headerName = "X-Trace-ID").intercept(exchange) { }
        assertEquals("trace-1", exchange.request[RequestIdInterceptor.RequestIdAttribute])
        assertEquals(listOf("trace-1"), rs.headerValues("X-Trace-ID"))
    }

    @Test
    fun `continues pipeline`() {
        var continued = false
        RequestIdInterceptor().intercept(createTestExchange()) { continued = true }
        assertTrue(continued)
    }
}
