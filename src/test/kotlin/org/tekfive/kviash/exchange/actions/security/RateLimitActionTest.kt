package org.tekfive.kviash.exchange.actions.security

import org.tekfive.kviash.exchange.interceptors.MockResponseSource
import org.tekfive.kviash.exchange.interceptors.createTestExchange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RateLimitActionTest {

    @Test
    fun `allows requests under limit`() {
        val action = RateLimitAction(maxRequests = 5, clientKeyExtractor = { "client1" })
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        action.invoke(exchange)
        assertTrue(!rs.committed)
        assertEquals(listOf("5"), rs.headerValues("X-RateLimit-Limit"))
        assertEquals(listOf("4"), rs.headerValues("X-RateLimit-Remaining"))
    }

    @Test
    fun `blocks requests over limit with 429`() {
        val action = RateLimitAction(maxRequests = 2, clientKeyExtractor = { "client1" })

        // Use up the limit
        repeat(2) {
            action.invoke(createTestExchange())
        }

        // Third request should be blocked
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        action.invoke(exchange)
        assertEquals(429, rs.status)
        assertTrue(rs.committed)
        assertTrue(rs.headerValues("Retry-After").isNotEmpty())
    }

    @Test
    fun `remaining decrements per request`() {
        val action = RateLimitAction(maxRequests = 3, clientKeyExtractor = { "c" })

        val rs1 = MockResponseSource()
        action.invoke(createTestExchange(responseSource = rs1))
        assertEquals(listOf("2"), rs1.headerValues("X-RateLimit-Remaining"))

        val rs2 = MockResponseSource()
        action.invoke(createTestExchange(responseSource = rs2))
        assertEquals(listOf("1"), rs2.headerValues("X-RateLimit-Remaining"))

        val rs3 = MockResponseSource()
        action.invoke(createTestExchange(responseSource = rs3))
        assertEquals(listOf("0"), rs3.headerValues("X-RateLimit-Remaining"))
    }

    @Test
    fun `different clients have independent limits`() {
        var clientKey = "a"
        val action = RateLimitAction(maxRequests = 1, clientKeyExtractor = { clientKey })

        action.invoke(createTestExchange())

        clientKey = "b"
        val rs = MockResponseSource()
        action.invoke(createTestExchange(responseSource = rs))
        assertTrue(!rs.committed)
    }

    @Test
    fun `window resets after expiry`() {
        val action = RateLimitAction(maxRequests = 1, windowMillis = 1, clientKeyExtractor = { "c" })
        action.invoke(createTestExchange())

        Thread.sleep(5)

        val rs = MockResponseSource()
        action.invoke(createTestExchange(responseSource = rs))
        assertTrue(!rs.committed)
    }
}
