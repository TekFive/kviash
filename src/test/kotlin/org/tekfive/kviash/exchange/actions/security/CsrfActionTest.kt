package org.tekfive.kviash.exchange.actions.security

import org.tekfive.kviash.exchange.interceptors.MockRequestSource
import org.tekfive.kviash.exchange.interceptors.MockResponseSource
import org.tekfive.kviash.exchange.interceptors.createTestExchange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CsrfActionTest {

    @Test
    fun `GET requests pass through and set token attribute`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        CsrfAction().invoke(exchange)
        assertTrue(!rs.committed)
        assertNotNull(exchange.request[CsrfAction.CsrfTokenAttribute])
    }

    @Test
    fun `POST without token returns 403`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(
            MockRequestSource(method = "POST"),
            rs
        )
        CsrfAction().invoke(exchange)
        assertEquals(403, rs.status)
        assertTrue(rs.committed)
    }

    @Test
    fun `POST with valid token in header passes`() {
        val action = CsrfAction()

        // First GET to establish session token
        val getExchange = createTestExchange(MockRequestSource(method = "GET"))
        action.invoke(getExchange)
        val token = getExchange.request[CsrfAction.CsrfTokenAttribute] as String
        val session = getExchange.request.getSession(false)!!

        // POST with the token in header, same session
        val postRequestSource = object : MockRequestSource(
            method = "POST",
            headers = listOf("Host" to listOf("localhost"), "X-CSRF-Token" to listOf(token))
        ) {
            override fun getSession(createIfNotExists: Boolean) = session
        }
        val rs = MockResponseSource()
        val postExchange = createTestExchange(postRequestSource, rs)
        action.invoke(postExchange)
        assertTrue(!rs.committed)
    }

    @Test
    fun `POST with wrong token returns 403`() {
        val action = CsrfAction()

        // GET to establish session
        val getExchange = createTestExchange(MockRequestSource(method = "GET"))
        action.invoke(getExchange)
        val session = getExchange.request.getSession(false)!!

        // POST with wrong token
        val postRequestSource = object : MockRequestSource(
            method = "POST",
            headers = listOf("Host" to listOf("localhost"), "X-CSRF-Token" to listOf("wrong-token"))
        ) {
            override fun getSession(createIfNotExists: Boolean) = session
        }
        val rs = MockResponseSource()
        val postExchange = createTestExchange(postRequestSource, rs)
        action.invoke(postExchange)
        assertEquals(403, rs.status)
        assertTrue(rs.committed)
    }

    @Test
    fun `POST with token in parameter passes`() {
        val action = CsrfAction()

        val getExchange = createTestExchange(MockRequestSource(method = "GET"))
        action.invoke(getExchange)
        val token = getExchange.request[CsrfAction.CsrfTokenAttribute] as String
        val session = getExchange.request.getSession(false)!!

        val postRequestSource = object : MockRequestSource(
            method = "POST",
            parameters = listOf("_csrf" to listOf(token))
        ) {
            override fun getSession(createIfNotExists: Boolean) = session
        }
        val rs = MockResponseSource()
        val postExchange = createTestExchange(postRequestSource, rs)
        action.invoke(postExchange)
        assertTrue(!rs.committed)
    }

    @Test
    fun `HEAD and OPTIONS are safe methods by default`() {
        val action = CsrfAction()

        for (method in listOf("HEAD", "OPTIONS")) {
            val rs = MockResponseSource()
            val exchange = createTestExchange(MockRequestSource(method = method), rs)
            action.invoke(exchange)
            assertTrue(!rs.committed, "$method should be a safe method")
        }
    }

    @Test
    fun `generated tokens are unique`() {
        val t1 = CsrfAction.generateToken()
        val t2 = CsrfAction.generateToken()
        assertTrue(t1 != t2)
        assertTrue(t1.length > 20)
    }
}
