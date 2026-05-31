package org.tekfive.kviash.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpMethodTest {

    @Test
    fun `fromName returns correct method for exact match`() {
        assertEquals(HttpMethod.GET, HttpMethod.fromName("GET"))
        assertEquals(HttpMethod.POST, HttpMethod.fromName("POST"))
        assertEquals(HttpMethod.PUT, HttpMethod.fromName("PUT"))
        assertEquals(HttpMethod.DELETE, HttpMethod.fromName("DELETE"))
        assertEquals(HttpMethod.PATCH, HttpMethod.fromName("PATCH"))
        assertEquals(HttpMethod.HEAD, HttpMethod.fromName("HEAD"))
        assertEquals(HttpMethod.OPTIONS, HttpMethod.fromName("OPTIONS"))
        assertEquals(HttpMethod.TRACE, HttpMethod.fromName("TRACE"))
        assertEquals(HttpMethod.CONNECT, HttpMethod.fromName("CONNECT"))
    }

    @Test
    fun `fromName is case insensitive`() {
        assertEquals(HttpMethod.GET, HttpMethod.fromName("get"))
        assertEquals(HttpMethod.POST, HttpMethod.fromName("Post"))
    }

    @Test
    fun `fromName returns null for unknown method`() {
        assertNull(HttpMethod.fromName("BOGUS"))
    }

    @Test
    fun `fromName returns null for null input`() {
        assertNull(HttpMethod.fromName(null))
    }

    @Test
    fun `all standard methods are defined`() {
        val methods = HttpMethod.entries
        assertTrue(methods.size >= 9, "Should have at least 9 HTTP methods")
    }
}

class HttpStatusTest {

    @Test
    fun `fromCode returns well-known statuses`() {
        assertEquals(200, HttpStatus.fromCode(200)?.code)
        assertEquals("OK", HttpStatus.fromCode(200)?.reason)
        assertEquals(404, HttpStatus.fromCode(404)?.code)
        assertEquals("Not Found", HttpStatus.fromCode(404)?.reason)
        assertEquals(500, HttpStatus.fromCode(500)?.code)
    }

    @Test
    fun `fromCode returns null for unknown code`() {
        assertNull(HttpStatus.fromCode(999))
    }

    @Test
    fun `companion constants have correct codes`() {
        assertEquals(100, HttpStatus.Continue.code)
        assertEquals(200, HttpStatus.Ok.code)
        assertEquals(201, HttpStatus.Created.code)
        assertEquals(204, HttpStatus.NoContent.code)
        assertEquals(301, HttpStatus.MovedPermanently.code)
        assertEquals(302, HttpStatus.Found.code)
        assertEquals(303, HttpStatus.SeeOther.code)
        assertEquals(307, HttpStatus.TemporaryRedirect.code)
        assertEquals(400, HttpStatus.BadRequest.code)
        assertEquals(401, HttpStatus.Unauthorized.code)
        assertEquals(403, HttpStatus.Forbidden.code)
        assertEquals(404, HttpStatus.NotFound.code)
        assertEquals(405, HttpStatus.MethodNotAllowed.code)
        assertEquals(418, HttpStatus.ImATeapot.code)
        assertEquals(500, HttpStatus.InternalServerError.code)
        assertEquals(502, HttpStatus.BadGateway.code)
        assertEquals(503, HttpStatus.ServiceUnavailable.code)
    }

    @Test
    fun `values returns all defined statuses`() {
        val values = HttpStatus.values()
        assertTrue(values.size > 40, "Should have many status codes defined")
    }
}

class HttpHeaderTest {

    @Test
    fun `header has name and values`() {
        val header = HttpHeader("Content-Type", "text/html")
        assertEquals("Content-Type", header.name)
        assertEquals("text/html", header.firstValue)
    }

    @Test
    fun `header with multiple values`() {
        val header = HttpHeader("Accept", listOf("text/html", "application/json"))
        assertEquals(2, header.values.size)
        assertEquals("text/html", header.firstValue)
    }

    @Test
    fun `delimitedValue joins values with comma`() {
        val header = HttpHeader("Accept", listOf("text/html", "application/json"))
        assertEquals("text/html, application/json", header.delimitedValue)
    }

    @Test
    fun `hasValue checks case insensitive`() {
        val header = HttpHeader("Accept", listOf("TEXT/HTML"))
        assertTrue(header.hasValue("text/html", ignoreCase = true))
    }

    @Test
    fun `plus combines values from two headers`() {
        val h1 = HttpHeader("Accept", "text/html")
        val h2 = HttpHeader("Accept", "application/json")
        val combined = h1 + h2
        assertEquals(2, combined.values.size)
    }

    @Test
    fun `toHttpHeader extension creates header`() {
        val header = "Content-Type".toHttpHeader("text/html")
        assertEquals("Content-Type", header.name)
        assertEquals("text/html", header.firstValue)
    }

    @Test
    fun `findByName locates header case insensitively`() {
        val headers = listOf(
            HttpHeader("Content-Type", "text/html"),
            HttpHeader("Accept", "application/json"),
        )
        val found = headers.findByName("content-type")
        assertEquals("text/html", found?.firstValue)
    }

    @Test
    fun `anyByName returns true when header exists`() {
        val headers = listOf(HttpHeader("X-Custom", "value"))
        assertTrue(headers.anyByName("x-custom"))
    }
}

class NamedMultiStringValueTest {

    @Test
    fun `firstValue returns first value or null`() {
        val param = HttpRequestParameter("key", listOf("a", "b"))
        assertEquals("a", param.firstValue)

        val empty = HttpRequestParameter("key", emptyList())
        assertNull(empty.firstValue)
    }

    @Test
    fun `equals checks name and values`() {
        val p1 = HttpRequestParameter("key", listOf("v1"))
        val p2 = HttpRequestParameter("key", listOf("v1"))
        assertEquals(p1, p2)
    }

    @Test
    fun `hashCode consistent with equals`() {
        val p1 = HttpRequestParameter("key", listOf("v1"))
        val p2 = HttpRequestParameter("key", listOf("v1"))
        assertEquals(p1.hashCode(), p2.hashCode())
    }

    @Test
    fun `findByName for parameters`() {
        val params = listOf(
            HttpRequestParameter("name", "alice"),
            HttpRequestParameter("age", "30"),
        )
        assertEquals("alice", params.findByName("name")?.firstValue)
        assertNull(params.findByName("missing"))
    }
}
