package org.tekfive.kviash.exchange.interceptors

import kotlin.test.Test
import kotlin.test.assertTrue

class RequestLoggingInterceptorTest {

    @Test
    fun `continues pipeline`() {
        val exchange = createTestExchange()
        var continued = false
        RequestLoggingInterceptor().intercept(exchange) { continued = true }
        assertTrue(continued)
    }

    @Test
    fun `continues pipeline even when action throws`() {
        val exchange = createTestExchange()
        var threw = false
        try {
            RequestLoggingInterceptor().intercept(exchange) { throw RuntimeException("fail") }
        } catch (_: RuntimeException) {
            threw = true
        }
        assertTrue(threw)
    }
}
