package org.tekfive.kviash.exchange.actions

import org.tekfive.kviash.exchange.interceptors.MockRequestSource
import org.tekfive.kviash.exchange.interceptors.MockResponseSource
import org.tekfive.kviash.http.HttpMethod
import org.tekfive.kviash.routing.RouteTable
import org.tekfive.kviash.routing.Router
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreflightResponderTest {

    private fun routeOptions(
        path: String,
        routeTreeNames: List<String> = emptyList(),
    ): MockResponseSource {
        val request = MockRequestSource(method = "OPTIONS", path = path)
        val response = MockResponseSource()
        Router.route(request, response, routeTreeNames)
        return response
    }

    @Test
    fun `returns 204 with Allow header listing registered methods`() {
        Router.clearRegistry()
        RouteTable.register("preflight-basic") {
            add("/{**}", HttpMethod.OPTIONS, PreflightResponder)
            with("/items") {
                add("/", HttpMethod.GET) { null }
                add("/", HttpMethod.POST) { null }
            }
        }

        val response = routeOptions("/items")

        assertEquals(204, response.status)
        assertTrue(response.committed)

        val allow = response.headerValues("Allow").firstOrNull() ?: ""
        val methods = allow.split(", ").toSet()
        assertContains(methods, "GET")
        assertContains(methods, "POST")
        assertContains(methods, "OPTIONS")
    }

    @Test
    fun `returns Allow header for parameterized path`() {
        Router.clearRegistry()
        RouteTable.register("preflight-param") {
            add("/items/{*}", HttpMethod.GET) { null }
            add("/items/{*}", HttpMethod.PUT) { null }
            add("/items/{**}", HttpMethod.OPTIONS, PreflightResponder)
        }

        val response = routeOptions("/items/42")

        assertEquals(204, response.status)
        val allow = response.headerValues("Allow").firstOrNull() ?: ""
        val methods = allow.split(", ").toSet()
        assertContains(methods, "GET")
        assertContains(methods, "PUT")
        assertContains(methods, "OPTIONS")
    }

    @Test
    fun `returns 204 even for path with no other methods`() {
        Router.clearRegistry()
        RouteTable.register("preflight-empty") {
            add("/{**}", HttpMethod.OPTIONS, PreflightResponder)
        }

        val response = routeOptions("/nonexistent")

        assertEquals(204, response.status)
        assertTrue(response.committed)
    }

    @Test
    fun `includes all registered methods at path`() {
        Router.clearRegistry()
        RouteTable.register("preflight-all") {
            add("/{**}", HttpMethod.OPTIONS, PreflightResponder)
            with("/resource") {
                add("/", HttpMethod.GET) { null }
                add("/", HttpMethod.POST) { null }
                add("/", HttpMethod.PUT) { null }
                add("/", HttpMethod.DELETE) { null }
            }
        }

        val response = routeOptions("/resource")

        assertEquals(204, response.status)
        val allow = response.headerValues("Allow").firstOrNull() ?: ""
        val methods = allow.split(", ").toSet()
        assertContains(methods, "GET")
        assertContains(methods, "POST")
        assertContains(methods, "PUT")
        assertContains(methods, "DELETE")
        assertContains(methods, "OPTIONS")
    }

    @Test
    fun `collects methods across multiple route trees`() {
        Router.clearRegistry()
        RouteTable.register("preflight-cross-tree-a") {
            add("/{**}", HttpMethod.OPTIONS, PreflightResponder)
            add("/shared", HttpMethod.GET) { null }
        }
        RouteTable.register("preflight-cross-tree-b") {
            add("/shared", HttpMethod.POST) { null }
        }

        val response = routeOptions("/shared", routeTreeNames = listOf("preflight-cross-tree-a"))

        assertEquals(204, response.status)
        val allow = response.headerValues("Allow").firstOrNull() ?: ""
        val methods = allow.split(", ").toSet()
        assertContains(methods, "GET")
        assertContains(methods, "POST")
        assertContains(methods, "OPTIONS")
    }
}
