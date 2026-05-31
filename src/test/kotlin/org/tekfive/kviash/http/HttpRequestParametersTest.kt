package org.tekfive.kviash.http

import org.tekfive.kviash.DefaultKviashConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HttpRequestParametersTest {

    private fun params(vararg pairs: Pair<String, String>): HttpRequestParameters {
        val map = pairs.associate { (k, v) -> k to listOf(v) }
        return HttpRequestParameters(map, DefaultKviashConfiguration)
    }

    @Test
    fun `getEmailAddress returns email for valid address`() {
        val p = params("email" to "user@example.com")
        assertEquals("user@example.com", p.getEmailAddress("email"))
    }

    @Test
    fun `getEmailAddress returns null for missing parameter`() {
        val p = params()
        assertNull(p.getEmailAddress("email"))
    }

    @Test
    fun `getEmailAddress returns null for blank value`() {
        val p = params("email" to "   ")
        assertNull(p.getEmailAddress("email"))
    }

    @Test
    fun `getEmailAddress returns null for value without at sign`() {
        val p = params("email" to "userexample.com")
        assertNull(p.getEmailAddress("email"))
    }

    @Test
    fun `getEmailAddress returns null for value without domain dot`() {
        val p = params("email" to "user@examplecom")
        assertNull(p.getEmailAddress("email"))
    }

    @Test
    fun `getEmailAddress returns null for value with spaces`() {
        val p = params("email" to "user @example.com")
        assertNull(p.getEmailAddress("email"))
    }

    @Test
    fun `getEmailAddress accepts plus addressing`() {
        val p = params("email" to "user+tag@example.com")
        assertEquals("user+tag@example.com", p.getEmailAddress("email"))
    }

    @Test
    fun `getEmailAddress accepts subdomains`() {
        val p = params("email" to "user@mail.example.co.uk")
        assertEquals("user@mail.example.co.uk", p.getEmailAddress("email"))
    }

    @Test
    fun `getEmailAddress accepts dots in local part`() {
        val p = params("email" to "first.last@example.com")
        assertEquals("first.last@example.com", p.getEmailAddress("email"))
    }

    @Test
    fun `getEmailAddress returns null for multiple at signs`() {
        val p = params("email" to "user@@example.com")
        assertNull(p.getEmailAddress("email"))
    }

    @Test
    fun `getEmailAddress returns null for at sign only`() {
        val p = params("email" to "@")
        assertNull(p.getEmailAddress("email"))
    }
}
