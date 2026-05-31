package org.tekfive.kviash.exchange.actions.security

import org.tekfive.kviash.exchange.interceptors.MockRequestSource
import org.tekfive.kviash.exchange.interceptors.MockResponseSource
import org.tekfive.kviash.exchange.interceptors.createTestExchange
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BasicAuthActionTest {

    private val action = BasicAuthAction(realm = "Test") { user, pass ->
        user == "system" && pass == "secret"
    }

    private fun basicHeader(user: String, pass: String): String {
        return "Basic " + Base64.getEncoder().encodeToString("$user:$pass".toByteArray())
    }

    @Test
    fun `valid credentials continue pipeline`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(
            MockRequestSource(headers = listOf("Host" to listOf("localhost"), "Authorization" to listOf(basicHeader("system", "secret")))),
            rs
        )
        action.invoke(exchange)
        assertTrue(!rs.committed)
        assertEquals("system", exchange.request[BasicAuthAction.AuthenticatedUserAttribute])
    }

    @Test
    fun `invalid credentials return 401`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(
            MockRequestSource(headers = listOf("Host" to listOf("localhost"), "Authorization" to listOf(basicHeader("system", "wrong")))),
            rs
        )
        action.invoke(exchange)
        assertEquals(401, rs.status)
        assertTrue(rs.committed)
        assertTrue(rs.headerValues("WWW-Authenticate").any { it.contains("Test") })
    }

    @Test
    fun `missing Authorization header returns 401`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(MockRequestSource(), rs)
        action.invoke(exchange)
        assertEquals(401, rs.status)
        assertTrue(rs.committed)
    }

    @Test
    fun `non-Basic auth scheme returns 401`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(
            MockRequestSource(headers = listOf("Host" to listOf("localhost"), "Authorization" to listOf("Bearer token123"))),
            rs
        )
        action.invoke(exchange)
        assertEquals(401, rs.status)
        assertTrue(rs.committed)
    }

    @Test
    fun `malformed base64 returns 401`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(
            MockRequestSource(headers = listOf("Host" to listOf("localhost"), "Authorization" to listOf("Basic !!!not-base64!!!"))),
            rs
        )
        action.invoke(exchange)
        assertEquals(401, rs.status)
        assertTrue(rs.committed)
    }

    @Test
    fun `decodeCredentials handles colon in password`() {
        val creds = BasicAuthAction.decodeCredentials(
            Base64.getEncoder().encodeToString("user:pass:with:colons".toByteArray())
        )
        assertEquals("user" to "pass:with:colons", creds)
    }

    @Test
    fun `decodeCredentials returns null for no colon`() {
        val creds = BasicAuthAction.decodeCredentials(
            Base64.getEncoder().encodeToString("nocolon".toByteArray())
        )
        assertNull(creds)
    }
}
