package org.tekfive.kviash.exchange.actions.static

import org.tekfive.kviash.exchange.interceptors.MockResponseSource
import org.tekfive.kviash.exchange.interceptors.createTestExchange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AddCacheHeaderTest {

    @Test
    fun `sets Cache-Control header`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        AddCacheHeader("max-age=3600").invoke(exchange)
        assertEquals(listOf("max-age=3600"), rs.headerValues("Cache-Control"))
    }

    @Test
    fun `defaults to no-cache`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        AddCacheHeader().invoke(exchange)
        assertEquals(listOf("no-cache"), rs.headerValues("Cache-Control"))
    }

    @Test
    fun `does not commit the response`() {
        val rs = MockResponseSource()
        val exchange = createTestExchange(responseSource = rs)
        AddCacheHeader().invoke(exchange)
        assertTrue(!rs.committed)
    }
}
