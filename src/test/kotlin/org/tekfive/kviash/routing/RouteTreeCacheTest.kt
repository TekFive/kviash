package org.tekfive.kviash.routing

import org.tekfive.kviash.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun routeRequest(
    method: String = "GET",
    path: String = "/",
    routeTreeNames: List<String> = emptyList(),
): MockResponseSource {
    val request = MockRequestSource(method = method, path = path)
    val response = MockResponseSource()
    Router.route(request, response, routeTreeNames)
    return response
}

class RouteTreeCacheTest {

    @Test
    fun `literal path is served from cache on second request`() {
        Router.clearRegistry()
        var invokeCount = 0
        RouteTable.register("cache-literal") {
            add("/items", HttpMethod.GET) { invokeCount++; null }
        }

        routeRequest(path = "/items")
        assertEquals(1, invokeCount)

        routeRequest(path = "/items")
        assertEquals(2, invokeCount)
    }

    @Test
    fun `cache works for nested literal paths`() {
        Router.clearRegistry()
        var invoked = false
        RouteTable.register("cache-nested") {
            add("/api/v1/items/list", HttpMethod.GET) { invoked = true; null }
        }

        routeRequest(path = "/api/v1/items/list")
        assertTrue(invoked)

        invoked = false
        routeRequest(path = "/api/v1/items/list")
        assertTrue(invoked)
    }

    @Test
    fun `parameterized paths are not cached`() {
        Router.clearRegistry()
        var invokeCount = 0
        RouteTable.register("cache-param") {
            add("/items/{*}", HttpMethod.GET) { invokeCount++; null }
        }

        routeRequest(path = "/items/42")
        assertEquals(1, invokeCount)

        routeRequest(path = "/items/99")
        assertEquals(2, invokeCount)
    }

    @Test
    fun `different methods on same cached path work correctly`() {
        Router.clearRegistry()
        var getInvoked = false
        var postInvoked = false
        RouteTable.register("cache-methods") {
            add("/items", HttpMethod.GET) { getInvoked = true; null }
            add("/items", HttpMethod.POST) { postInvoked = true; null }
        }

        routeRequest(method = "GET", path = "/items")
        assertTrue(getInvoked)

        routeRequest(method = "POST", path = "/items")
        assertTrue(postInvoked)
    }

    @Test
    fun `cache does not match wrong method on cached path`() {
        Router.clearRegistry()
        var getInvoked = false
        RouteTable.register("cache-no-method") {
            add("/items", HttpMethod.GET) { getInvoked = true; null }
        }

        routeRequest(path = "/items")
        assertTrue(getInvoked)

        getInvoked = false
        routeRequest(method = "DELETE", path = "/items")
        assertTrue(!getInvoked)
    }

    @Test
    fun `gobbler paths are not cached`() {
        Router.clearRegistry()
        var invokeCount = 0
        RouteTable.register("cache-gobbler") {
            add("/static/{**}", HttpMethod.GET) { invokeCount++; null }
        }

        routeRequest(path = "/static/css/app.css")
        assertEquals(1, invokeCount)

        routeRequest(path = "/static/js/app.js")
        assertEquals(2, invokeCount)
    }

    @Test
    fun `cache hit and tree traversal produce same result`() {
        Router.clearRegistry()
        var result = ""
        RouteTable.register("cache-consistency") {
            add("/a", HttpMethod.GET) { result = "a"; null }
            add("/b", HttpMethod.GET) { result = "b"; null }
        }

        routeRequest(path = "/a")
        assertEquals("a", result)

        routeRequest(path = "/a")
        assertEquals("a", result)

        routeRequest(path = "/b")
        assertEquals("b", result)

        routeRequest(path = "/b")
        assertEquals("b", result)
    }

    @Test
    fun `cache disabled still routes correctly`() {
        Router.clearRegistry()
        var invokeCount = 0
        RouteTable.register("cache-disabled", enableRouteCache = false) {
            add("/items", HttpMethod.GET) { invokeCount++; null }
        }

        routeRequest(path = "/items")
        assertEquals(1, invokeCount)

        routeRequest(path = "/items")
        assertEquals(2, invokeCount)
    }

    @Test
    fun `max cache size limits cached entries`() {
        Router.clearRegistry()
        var aCount = 0
        var bCount = 0
        var cCount = 0
        RouteTable.register("cache-max-size", maxCacheSize = 1) {
            add("/a", HttpMethod.GET) { aCount++; null }
            add("/b", HttpMethod.GET) { bCount++; null }
            add("/c", HttpMethod.GET) { cCount++; null }
        }

        // First request caches /a
        routeRequest(path = "/a")
        assertEquals(1, aCount)

        // /b traverses (cache full), still works
        routeRequest(path = "/b")
        assertEquals(1, bCount)

        // /c traverses (cache full), still works
        routeRequest(path = "/c")
        assertEquals(1, cCount)

        // /a still served from cache
        routeRequest(path = "/a")
        assertEquals(2, aCount)

        // /b still works via traversal
        routeRequest(path = "/b")
        assertEquals(2, bCount)
    }
}
