package org.tekfive.kviash.exchange.actions.security

import org.tekfive.kviash.exchange.interceptors.MockRequestSource
import org.tekfive.kviash.exchange.interceptors.MockResponseSource
import org.tekfive.kviash.exchange.interceptors.createTestExchange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CorsHeadersTest {

    @Test
    fun `adds Allow-Origin for wildcard config`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(
            MockRequestSource(headers = listOf("Host" to listOf("localhost"), "Origin" to listOf("http://example.com"))),
            rs
        )
        CorsHeaders().invoke(exchange)
        assertEquals(listOf("*"), rs.headerValues("Access-Control-Allow-Origin"))
    }

    @Test
    fun `returns origin when credentials enabled with wildcard`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(
            MockRequestSource(headers = listOf("Host" to listOf("localhost"), "Origin" to listOf("http://example.com"))),
            rs
        )
        CorsHeaders(allowCredentials = true).invoke(exchange)
        assertEquals(listOf("http://example.com"), rs.headerValues("Access-Control-Allow-Origin"))
        assertEquals(listOf("true"), rs.headerValues("Access-Control-Allow-Credentials"))
    }

    @Test
    fun `allows specific origin`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(
            MockRequestSource(headers = listOf("Host" to listOf("localhost"), "Origin" to listOf("http://allowed.com"))),
            rs
        )
        CorsHeaders(allowedOrigins = setOf("http://allowed.com")).invoke(exchange)
        assertEquals(listOf("http://allowed.com"), rs.headerValues("Access-Control-Allow-Origin"))
    }

    @Test
    fun `rejects disallowed origin`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(
            MockRequestSource(headers = listOf("Host" to listOf("localhost"), "Origin" to listOf("http://evil.com"))),
            rs
        )
        CorsHeaders(allowedOrigins = setOf("http://allowed.com")).invoke(exchange)
        assertTrue(rs.headerValues("Access-Control-Allow-Origin").isEmpty())
    }

    @Test
    fun `preflight OPTIONS returns 204 with CORS headers`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(
            MockRequestSource(
                method = "OPTIONS",
                headers = listOf("Host" to listOf("localhost"), "Origin" to listOf("http://example.com"))
            ),
            rs
        )
        CorsHeaders().invoke(exchange)
        assertEquals(204, rs.status)
        assertTrue(rs.committed)
        assertTrue(rs.headerValues("Access-Control-Allow-Methods").isNotEmpty())
        assertTrue(rs.headerValues("Access-Control-Allow-Headers").isNotEmpty())
        assertEquals(listOf("3600"), rs.headerValues("Access-Control-Max-Age"))
    }

    @Test
    fun `no Origin header skips CORS`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(MockRequestSource(), rs)
        CorsHeaders().invoke(exchange)
        assertTrue(rs.headerValues("Access-Control-Allow-Origin").isEmpty())
    }

    @Test
    fun `exposed headers are set`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(
            MockRequestSource(headers = listOf("Host" to listOf("localhost"), "Origin" to listOf("http://example.com"))),
            rs
        )
        CorsHeaders(exposedHeaders = setOf("X-Custom", "X-Other")).invoke(exchange)
        val exposed = rs.headerValues("Access-Control-Expose-Headers")
        assertTrue(exposed.any { it.contains("X-Custom") })
    }

    @Test
    fun `preflight without max age omits header`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(
            MockRequestSource(
                method = "OPTIONS",
                headers = listOf("Host" to listOf("localhost"), "Origin" to listOf("http://example.com"))
            ),
            rs
        )
        CorsHeaders(maxAge = null).invoke(exchange)
        assertTrue(rs.headerValues("Access-Control-Max-Age").isEmpty())
    }
}
