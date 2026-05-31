package org.tekfive.kviash.routing

import org.tekfive.kviash.http.AcceptType
import org.tekfive.kviash.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun routeRequest(
    method: String = "GET",
    path: String = "/",
    accept: String? = null,
    routeTreeNames: List<String> = emptyList(),
): MockResponseSource {
    val headers = mutableListOf<Pair<String, List<String>>>("Host" to listOf("localhost"))
    if (accept != null) {
        headers.add("Accept" to listOf(accept))
    }
    val request = MockRequestSource(method = method, path = path, headers = headers)
    val response = MockResponseSource()
    Router.route(request, response, routeTreeNames)
    return response
}

class AcceptTypeRoutingTest {

    @Test
    fun `routes to typed pipeline when Accept header matches`() {
        Router.clearRegistry()
        var jsonInvoked = false
        var htmlInvoked = false
        RouteTable.register("accept-basic") {
            add("/items", HttpMethod.GET, acceptTypes = setOf(AcceptType.APPLICATION_JSON)) { jsonInvoked = true; null }
            add("/items", HttpMethod.GET, acceptTypes = setOf(AcceptType.TEXT_HTML)) { htmlInvoked = true; null }
        }

        routeRequest(path = "/items", accept = "application/json")
        assertTrue(jsonInvoked)

        jsonInvoked = false
        routeRequest(path = "/items", accept = "text/html")
        assertTrue(htmlInvoked)
    }

    @Test
    fun `falls back to untyped pipeline when no Accept match`() {
        Router.clearRegistry()
        var fallbackInvoked = false
        RouteTable.register("accept-fallback") {
            add("/items", HttpMethod.GET, acceptTypes = setOf(AcceptType.APPLICATION_JSON)) { null }
            add("/items", HttpMethod.GET) { fallbackInvoked = true; null }
        }

        routeRequest(path = "/items", accept = "text/xml")
        assertTrue(fallbackInvoked)
    }

    @Test
    fun `falls back to untyped pipeline when no Accept header`() {
        Router.clearRegistry()
        var fallbackInvoked = false
        RouteTable.register("accept-no-header") {
            add("/items", HttpMethod.GET, acceptTypes = setOf(AcceptType.APPLICATION_JSON)) { null }
            add("/items", HttpMethod.GET) { fallbackInvoked = true; null }
        }

        routeRequest(path = "/items")
        assertTrue(fallbackInvoked)
    }

    @Test
    fun `wildcard Accept matches typed pipeline`() {
        Router.clearRegistry()
        var jsonInvoked = false
        RouteTable.register("accept-wildcard") {
            add("/items", HttpMethod.GET, acceptTypes = setOf(AcceptType.APPLICATION_JSON)) { jsonInvoked = true; null }
        }

        routeRequest(path = "/items", accept = "*/*")
        assertTrue(jsonInvoked)
    }

    @Test
    fun `type wildcard matches subtype`() {
        Router.clearRegistry()
        var jsonInvoked = false
        RouteTable.register("accept-type-wildcard") {
            add("/items", HttpMethod.GET, acceptTypes = setOf(AcceptType.APPLICATION_JSON)) { jsonInvoked = true; null }
        }

        routeRequest(path = "/items", accept = "application/*")
        assertTrue(jsonInvoked)
    }

    @Test
    fun `quality parameters are stripped during matching`() {
        Router.clearRegistry()
        var jsonInvoked = false
        RouteTable.register("accept-quality") {
            add("/items", HttpMethod.GET, acceptTypes = setOf(AcceptType.APPLICATION_JSON)) { jsonInvoked = true; null }
        }

        routeRequest(path = "/items", accept = "application/json;q=0.9")
        assertTrue(jsonInvoked)
    }

    @Test
    fun `multiple Accept values with first match wins`() {
        Router.clearRegistry()
        var htmlInvoked = false
        RouteTable.register("accept-multi-value") {
            add("/items", HttpMethod.GET, acceptTypes = setOf(AcceptType.APPLICATION_JSON)) { null }
            add("/items", HttpMethod.GET, acceptTypes = setOf(AcceptType.TEXT_HTML)) { htmlInvoked = true; null }
        }

        routeRequest(path = "/items", accept = "text/html, application/json")
        assertTrue(htmlInvoked)
    }

    @Test
    fun `duplicate untyped routes throw`() {
        Router.clearRegistry()
        assertFailsWith<IllegalStateException> {
            RouteTable.register("accept-dup-untyped") {
                add("/items", HttpMethod.GET) { null }
                add("/items", HttpMethod.GET) { null }
            }
        }
    }

    @Test
    fun `duplicate typed routes with overlapping accept types throw`() {
        Router.clearRegistry()
        assertFailsWith<IllegalStateException> {
            RouteTable.register("accept-dup-typed") {
                add("/items", HttpMethod.GET, acceptTypes = setOf(AcceptType.APPLICATION_JSON)) { null }
                add("/items", HttpMethod.GET, acceptTypes = setOf(AcceptType.APPLICATION_JSON)) { null }
            }
        }
    }

    @Test
    fun `typed and untyped at same path and method is allowed`() {
        Router.clearRegistry()
        var jsonInvoked = false
        var fallbackInvoked = false
        RouteTable.register("accept-mixed") {
            add("/items", HttpMethod.GET, acceptTypes = setOf(AcceptType.APPLICATION_JSON)) { jsonInvoked = true; null }
            add("/items", HttpMethod.GET) { fallbackInvoked = true; null }
        }

        routeRequest(path = "/items", accept = "application/json")
        assertTrue(jsonInvoked)

        routeRequest(path = "/items", accept = "text/html")
        assertTrue(fallbackInvoked)
    }

    @Test
    fun `route with multiple accept types matches any`() {
        Router.clearRegistry()
        var invoked = false
        RouteTable.register("accept-multi-types") {
            add("/items", HttpMethod.GET, acceptTypes = setOf(AcceptType.APPLICATION_JSON, AcceptType.APPLICATION_XML)) { invoked = true; null }
        }

        routeRequest(path = "/items", accept = "application/xml")
        assertTrue(invoked)
    }

    @Test
    fun `function-based route with acceptTypes`() {
        Router.clearRegistry()
        RouteTable.register("accept-function") {
            add(HttpMethod.GET, TestController::getOne, acceptTypes = setOf(AcceptType.APPLICATION_JSON))
            add(HttpMethod.GET, TestController::getTwo, acceptTypes = setOf(AcceptType.TEXT_HTML))
        }

        assertEquals(200, routeRequest(path = "/", accept = "application/json").status)
        assertEquals(200, routeRequest(path = "/", accept = "text/html").status)
    }
}

class AcceptTypeParsingTest {

    @Test
    fun `parse simple accept header`() {
        val types = AcceptType.parse(listOf("application/json"))
        assertEquals(listOf("application/json"), types)
    }

    @Test
    fun `parse comma-separated accept header`() {
        val types = AcceptType.parse(listOf("text/html, application/json"))
        assertEquals(listOf("text/html", "application/json"), types)
    }

    @Test
    fun `parse strips quality parameters`() {
        val types = AcceptType.parse(listOf("text/html;q=0.9, application/json;q=1.0"))
        assertEquals(listOf("text/html", "application/json"), types)
    }

    @Test
    fun `parse empty list`() {
        val types = AcceptType.parse(emptyList())
        assertTrue(types.isEmpty())
    }

    @Test
    fun `matches exact type`() {
        assertTrue(AcceptType.matches("application/json", "application/json"))
    }

    @Test
    fun `matches any wildcard`() {
        assertTrue(AcceptType.matches("*/*", "application/json"))
    }

    @Test
    fun `matches type wildcard`() {
        assertTrue(AcceptType.matches("text/*", "text/html"))
    }

    @Test
    fun `does not match different types`() {
        assertTrue(!AcceptType.matches("text/html", "application/json"))
    }

    @Test
    fun `matching is case insensitive`() {
        assertTrue(AcceptType.matches("Application/JSON", "application/json"))
    }
}
