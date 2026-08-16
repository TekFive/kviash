package org.tekfive.kviash.routing

import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.exchange.ExchangeAction
import org.tekfive.kviash.http.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Mock implementations
// ---------------------------------------------------------------------------

private class UrlGenMockRequestSource(
    override val method: String = "GET",
    override val path: String = "/",
    override val queryString: String? = null,
    override val urlProtocol: String = "http",
    override val httpProtocol: String = "HTTP/1.1",
    override val port: Int = 80,
    override val headers: List<Pair<String, List<String>>> = listOf("Host" to listOf("localhost")),
    override val parameters: List<Pair<String, List<String>>> = emptyList(),
    override val clientIp: String = "127.0.0.1",
    override val inputStream: InputStream? = null,
) : HttpRequestSource {
    private val attributes = mutableMapOf<String, Any?>()
    override fun getAttribute(name: String): Any? = attributes[name]
    override fun setAttribute(name: String, value: Any?) { attributes[name] = value }
    override fun getSession(createIfNotExists: Boolean): HttpSession? = null
}

private class UrlGenMockResponseSource : HttpResponseSource {
    var _status: Int = 200
    private val _headers = mutableListOf<HttpHeader>()
    private var _committed = false
    private val _outputStream = ByteArrayOutputStream()
    private val _outputWriter: Writer by lazy { OutputStreamWriter(_outputStream) }

    override val status: Int get() = _status
    override val headers: List<HttpHeader> get() = _headers.toList()
    override val committed: Boolean get() = _committed
    override val outputStream: OutputStream get() = _outputStream
    override val outputWriter: Writer get() = _outputWriter

    override fun addCookie(cookie: ResponseCookie) {}
    override fun addHeader(header: HttpHeader) { _headers.add(header) }
    override fun setStatus(status: Int) { _status = status }
    override fun setHeader(header: HttpHeader) {
        _headers.removeAll { it.name.equals(header.name, true) }
        _headers.add(header)
    }
    override fun getHeaderValues(name: String): List<String> {
        return _headers.filter { it.name.equals(name, true) }.flatMap { it.values }
    }
    override fun commit() { _committed = true }
    override fun createdBufferedResponse(outputBuffer: OutputStream): HttpResponseSource = this
}

// ---------------------------------------------------------------------------
// Test controller
// ---------------------------------------------------------------------------

private class TypedUrlAction(
    private val types: Map<String, (String) -> String>,
) : ExchangeAction, UrlPlugin {
    override fun invoke(exchange: Exchange): Any? = null
    override fun urlTypes(): Set<String> = types.keys
    override fun typedUrl(type: String, resource: String): String? {
        val fn = types[type] ?: return null
        return fn(resource)
    }
}

class UrlGenController {
    fun getHome(): String = "home"
    fun getUsers(): String = "users"
    fun getUserById(id: Int): String = "user:$id"
    fun getUserPosts(id: Int, postId: Int): String = "user:$id,post:$postId"
    fun getSearch(): String = "search"
    fun getAdmin(): String = "system"
    fun getWild(name: String): String = "wild:$name"
}

// ---------------------------------------------------------------------------
// Helper: capture Routes from an exchange during routing
// ---------------------------------------------------------------------------

private fun captureRoutes(
    requestPath: String,
    routeTreeNames: List<String>,
    headers: List<Pair<String, List<String>>> = listOf("Host" to listOf("localhost")),
): Routes? {
    var urlGen: Routes? = null
    Router.route(
        UrlGenMockRequestSource(path = requestPath, headers = headers),
        UrlGenMockResponseSource(),
        routeTreeNames
    )
    // Can't capture from normal route — we need an action that captures.
    // Instead, the caller registers a capturing action.
    return urlGen
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

class RoutesTest {

    // -----------------------------------------------------------------------
    // getRoutePath — same controller instance for registration and lookup
    // -----------------------------------------------------------------------

    @Test
    fun `getRoutePath returns path for root-level function`() {
        Router.clearRegistry()

        val ctrl = UrlGenController()
        var urlGen: Routes? = null

        RouteTable.register(name = "test") {
            add(ctrl::getHome)
            add("/capture", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }

        Router.route(
            UrlGenMockRequestSource(path = "/capture"),
            UrlGenMockResponseSource(),
            listOf("test")
        )

        // getHome has no explicit path, registered at root with GET method
        // tree routePath for the root node is ""
        val path = urlGen!!.getRoutePath(ctrl::getHome)
        assertEquals("", path)
    }

    @Test
    fun `getRoutePath returns path for scoped function`() {
        Router.clearRegistry()

        val ctrl = UrlGenController()
        var urlGen: Routes? = null

        RouteTable.register(name = "test") {
            with(path = "/users") {
                add(ctrl::getUsers)
            }
            add("/capture", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }

        Router.route(
            UrlGenMockRequestSource(path = "/capture"),
            UrlGenMockResponseSource(),
            listOf("test")
        )

        val path = urlGen!!.getRoutePath(ctrl::getUsers)
        assertEquals("/users", path)
    }

    @Test
    fun `getRoutePath returns path for deeply nested function`() {
        Router.clearRegistry()

        val ctrl = UrlGenController()
        var urlGen: Routes? = null

        RouteTable.register(name = "test") {
            with(path = "/api") {
                with(path = "/v1") {
                    with(path = "/users") {
                        add(ctrl::getUsers)
                    }
                }
            }
            add("/capture", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }

        Router.route(
            UrlGenMockRequestSource(path = "/capture"),
            UrlGenMockResponseSource(),
            listOf("test")
        )

        val path = urlGen!!.getRoutePath(ctrl::getUsers)
        assertEquals("/api/v1/users", path)
    }

    @Test
    fun `getRoutePath with explicit route path`() {
        Router.clearRegistry()

        val ctrl = UrlGenController()
        var urlGen: Routes? = null

        RouteTable.register(name = "test") {
            add("/search", ctrl::getSearch)
            add("/capture", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }

        Router.route(
            UrlGenMockRequestSource(path = "/capture"),
            UrlGenMockResponseSource(),
            listOf("test")
        )

        val path = urlGen!!.getRoutePath(ctrl::getSearch)
        assertEquals("/search", path)
    }

    @Test
    fun `getRoutePath throws for unmapped function`() {
        Router.clearRegistry()

        val ctrl = UrlGenController()
        val unmappedCtrl = UrlGenController()
        var urlGen: Routes? = null

        RouteTable.register(name = "test") {
            add(ctrl::getHome)
            add("/capture", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }

        Router.route(
            UrlGenMockRequestSource(path = "/capture"),
            UrlGenMockResponseSource(),
            listOf("test")
        )

        // unmappedCtrl::getUsers was never registered
        assertFailsWith<RouteFunctionNotMappedException> {
            urlGen!!.getRoutePath(unmappedCtrl::getUsers)
        }
    }

    @Test
    fun `getRoutePath finds function across multiple registered trees`() {
        Router.clearRegistry()

        val ctrl = UrlGenController()
        var urlGen: Routes? = null

        RouteTable.register(name = "tree-a") {
            add(ctrl::getHome)
        }
        RouteTable.register(name = "tree-b") {
            add("/admin", ctrl::getAdmin)
        }
        RouteTable.register(name = "tree-cap") {
            add("/cap", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }

        Router.route(
            UrlGenMockRequestSource(path = "/cap"),
            UrlGenMockResponseSource(),
            listOf("tree-cap")
        )

        // getAdmin is in tree-b, not tree-cap
        val path = urlGen!!.getRoutePath(ctrl::getAdmin)
        assertEquals("/admin", path)
    }

    @Test
    fun `getRoutePath checks current tree root first`() {
        Router.clearRegistry()

        val ctrl = UrlGenController()
        var urlGen: Routes? = null

        // Register getHome in primary tree and capture URL generator from same tree
        RouteTable.register(name = "primary") {
            add(ctrl::getHome)
            add("/cap", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }

        // Also register getHome in secondary tree at different path
        RouteTable.register(name = "secondary") {
            add("/other-home", ctrl::getHome)
        }

        // Route through primary tree
        Router.route(
            UrlGenMockRequestSource(path = "/cap"),
            UrlGenMockResponseSource(),
            listOf("primary")
        )

        // Should find getHome from primary tree (root of current pipeline)
        val path = urlGen!!.getRoutePath(ctrl::getHome)
        assertEquals("", path)
    }

    // -----------------------------------------------------------------------
    // getRootUrl
    // -----------------------------------------------------------------------

    @Test
    fun `getRootUrl returns protocol and host from default configuration`() {
        Router.clearRegistry()

        var urlGen: Routes? = null
        RouteTable.register(name = "root-url") {
            add("/test", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }
        Router.route(
            UrlGenMockRequestSource(
                path = "/test",
                headers = listOf("Host" to listOf("example.com")),
            ),
            UrlGenMockResponseSource(),
            listOf("root-url")
        )

        val rootUrl = urlGen!!.getRootUrl()
        assertTrue(rootUrl.contains("example.com"))
    }

    // -----------------------------------------------------------------------
    // getUrl with string path (no path values)
    // -----------------------------------------------------------------------

    @Test
    fun `getUrl with simple path prepends root url`() {
        Router.clearRegistry()

        var urlGen: Routes? = null
        RouteTable.register(name = "url-path") {
            add("/test", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }
        Router.route(
            UrlGenMockRequestSource(
                path = "/test",
                headers = listOf("Host" to listOf("myhost.com")),
            ),
            UrlGenMockResponseSource(),
            listOf("url-path")
        )

        val url = urlGen!!.getUrl("/users")
        assertTrue(url.endsWith("/users"))
        assertTrue(url.contains("myhost.com"))
    }

    @Test
    fun `getUrl with explicit root url`() {
        Router.clearRegistry()

        var urlGen: Routes? = null
        RouteTable.register(name = "url-root") {
            add("/test", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }
        Router.route(
            UrlGenMockRequestSource(path = "/test"),
            UrlGenMockResponseSource(),
            listOf("url-root")
        )

        val url = urlGen!!.getUrl("/users", "https://custom.example.com")
        assertEquals("https://custom.example.com/users", url)
    }

    // -----------------------------------------------------------------------
    // getUrl with path value substitution
    // -----------------------------------------------------------------------

    @Test
    fun `getUrl replaces single placeholder`() {
        Router.clearRegistry()

        var urlGen: Routes? = null
        RouteTable.register(name = "url-sub") {
            add("/test", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }
        Router.route(
            UrlGenMockRequestSource(path = "/test"),
            UrlGenMockResponseSource(),
            listOf("url-sub")
        )

        val url = urlGen!!.getUrl("/users/{id}", "https://app.com", 42)
        assertEquals("https://app.com/users/42", url)
    }

    @Test
    fun `getUrl replaces multiple placeholders`() {
        Router.clearRegistry()

        var urlGen: Routes? = null
        RouteTable.register(name = "url-multi") {
            add("/test", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }
        Router.route(
            UrlGenMockRequestSource(path = "/test"),
            UrlGenMockResponseSource(),
            listOf("url-multi")
        )

        val url = urlGen!!.getUrl("/users/{id}/posts/{postId}", "https://app.com", 7, 99)
        assertEquals("https://app.com/users/7/posts/99", url)
    }

    @Test
    fun `getUrl with more values than placeholders ignores extras`() {
        Router.clearRegistry()

        var urlGen: Routes? = null
        RouteTable.register(name = "url-extra") {
            add("/test", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }
        Router.route(
            UrlGenMockRequestSource(path = "/test"),
            UrlGenMockResponseSource(),
            listOf("url-extra")
        )

        val url = urlGen!!.getUrl("/users/{id}", "https://app.com", 42, "extra-ignored")
        assertEquals("https://app.com/users/42", url)
    }

    @Test
    fun `getUrl with no placeholders and path values leaves path unchanged`() {
        Router.clearRegistry()

        var urlGen: Routes? = null
        RouteTable.register(name = "url-nop") {
            add("/test", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }
        Router.route(
            UrlGenMockRequestSource(path = "/test"),
            UrlGenMockResponseSource(),
            listOf("url-nop")
        )

        val url = urlGen!!.getUrl("/items/list", "https://app.com", "unused-value")
        assertEquals("https://app.com/items/list", url)
    }

    @Test
    fun `getUrl with no path values and no placeholders`() {
        Router.clearRegistry()

        var urlGen: Routes? = null
        RouteTable.register(name = "url-novals") {
            add("/test", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }
        Router.route(
            UrlGenMockRequestSource(path = "/test"),
            UrlGenMockResponseSource(),
            listOf("url-novals")
        )

        val url = urlGen!!.getUrl("/items", "https://app.com")
        assertEquals("https://app.com/items", url)
    }

    @Test
    fun `getUrl replaces inferred expression style placeholders`() {
        Router.clearRegistry()

        var urlGen: Routes? = null
        RouteTable.register(name = "url-inferred") {
            add("/test", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }
        Router.route(
            UrlGenMockRequestSource(path = "/test"),
            UrlGenMockResponseSource(),
            listOf("url-inferred")
        )

        // {} style placeholders also start with { and end with }
        val url = urlGen!!.getUrl("/users/{}", "https://app.com", 123)
        assertEquals("https://app.com/users/123", url)
    }

    @Test
    fun `getUrl replaces wildcard style placeholders`() {
        Router.clearRegistry()

        var urlGen: Routes? = null
        RouteTable.register(name = "url-wild") {
            add("/test", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }
        Router.route(
            UrlGenMockRequestSource(path = "/test"),
            UrlGenMockResponseSource(),
            listOf("url-wild")
        )

        val url = urlGen!!.getUrl("/files/{*}", "https://app.com", "readme.txt")
        assertEquals("https://app.com/files/readme.txt", url)
    }

    @Test
    fun `getUrl percent-encodes path parameter values`() {
        Router.clearRegistry()
        var urlGen: Routes? = null
        RouteTable.register(name = "url-encoding") {
            add("/capture", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }
        Router.route(
            UrlGenMockRequestSource(path = "/capture"),
            UrlGenMockResponseSource(),
            listOf("url-encoding"),
        )

        val url = urlGen!!.getUrl("/users/{}", "https://app.com", "A+B /?#")
        assertEquals("https://app.com/users/A%2BB%20%2F%3F%23", url)
    }

    @Test
    fun `getUrl preserves separators for gobbler values while encoding segments`() {
        Router.clearRegistry()
        var urlGen: Routes? = null
        RouteTable.register(name = "url-gobbler") {
            add("/capture", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }
        Router.route(
            UrlGenMockRequestSource(path = "/capture"),
            UrlGenMockResponseSource(),
            listOf("url-gobbler"),
        )

        val url = urlGen!!.getUrl("/files/{**}", "https://app.com", "a folder/file+name.txt")
        assertEquals("https://app.com/files/a%20folder/file%2Bname.txt", url)
    }

    // -----------------------------------------------------------------------
    // getUrl with KFunction
    // -----------------------------------------------------------------------

    @Test
    fun `getUrl with function resolves and prepends root url`() {
        Router.clearRegistry()

        val ctrl = UrlGenController()
        var urlGen: Routes? = null

        RouteTable.register(name = "url-func") {
            add("/search", ctrl::getSearch)
            add("/capture", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }
        Router.route(
            UrlGenMockRequestSource(
                path = "/capture",
                headers = listOf("Host" to listOf("myhost.com")),
            ),
            UrlGenMockResponseSource(),
            listOf("url-func")
        )

        val url = urlGen!!.getUrl(ctrl::getSearch)
        assertTrue(url.endsWith("/search"))
        assertTrue(url.contains("myhost.com"))
    }

    @Test
    fun `getUrl with function and custom root url`() {
        Router.clearRegistry()

        val ctrl = UrlGenController()
        var urlGen: Routes? = null

        RouteTable.register(name = "url-func-root") {
            add("/admin", ctrl::getAdmin)
            add("/capture", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }
        Router.route(
            UrlGenMockRequestSource(path = "/capture"),
            UrlGenMockResponseSource(),
            listOf("url-func-root")
        )

        val url = urlGen!!.getUrl(ctrl::getAdmin, "https://custom.com")
        assertEquals("https://custom.com/admin", url)
    }

    // -----------------------------------------------------------------------
    // getRefererUrl
    // -----------------------------------------------------------------------

    @Test
    fun `getRefererUrl returns referer header when present`() {
        Router.clearRegistry()

        val ctrl = UrlGenController()
        var urlGen: Routes? = null

        RouteTable.register(name = "referer-test") {
            add(ctrl::getHome)
            add("/capture", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }
        Router.route(
            UrlGenMockRequestSource(
                path = "/capture",
                headers = listOf(
                    "Host" to listOf("localhost"),
                    "Referer" to listOf("https://example.com/previous-page"),
                ),
            ),
            UrlGenMockResponseSource(),
            listOf("referer-test")
        )

        val url = urlGen!!.getRefererUrl(ctrl::getHome)
        assertEquals("https://example.com/previous-page", url)
    }

    @Test
    fun `getRefererUrl falls back to default function when no referer`() {
        Router.clearRegistry()

        val ctrl = UrlGenController()
        var urlGen: Routes? = null

        RouteTable.register(name = "no-referer") {
            add(ctrl::getHome)
            add("/capture", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }
        Router.route(
            UrlGenMockRequestSource(
                path = "/capture",
                headers = listOf("Host" to listOf("localhost")),
            ),
            UrlGenMockResponseSource(),
            listOf("no-referer")
        )

        val url = urlGen!!.getRefererUrl(ctrl::getHome)
        // Falls back to getUrl(ctrl::getHome, exchange.request)
        // getHome is registered at root, so route path is ""
        assertTrue(url.contains("localhost"))
    }

    @Test
    fun `getRefererUrl falls back when referer is blank`() {
        Router.clearRegistry()

        val ctrl = UrlGenController()
        var urlGen: Routes? = null

        RouteTable.register(name = "blank-referer") {
            add(ctrl::getHome)
            add("/capture", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }
        Router.route(
            UrlGenMockRequestSource(
                path = "/capture",
                headers = listOf(
                    "Host" to listOf("localhost"),
                    "Referer" to listOf("   "),
                ),
            ),
            UrlGenMockResponseSource(),
            listOf("blank-referer")
        )

        val url = urlGen!!.getRefererUrl(ctrl::getHome)
        // Blank referer should fall back to default function URL
        assertTrue(url.contains("localhost"))
    }

    @Test
    fun `getRefererUrl falls back when referer is empty`() {
        Router.clearRegistry()

        val ctrl = UrlGenController()
        var urlGen: Routes? = null

        RouteTable.register(name = "empty-referer") {
            add(ctrl::getHome)
            add("/capture", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }
        Router.route(
            UrlGenMockRequestSource(
                path = "/capture",
                headers = listOf(
                    "Host" to listOf("localhost"),
                    "Referer" to listOf(""),
                ),
            ),
            UrlGenMockResponseSource(),
            listOf("empty-referer")
        )

        val url = urlGen!!.getRefererUrl(ctrl::getHome)
        assertTrue(url.contains("localhost"))
    }

    // -----------------------------------------------------------------------
    // toRoutePath extension function
    // -----------------------------------------------------------------------

    @Test
    fun `toRoutePath throws when no thread local context`() {
        val ctrl = UrlGenController()
        assertFailsWith<ThreadLocalContextException> {
            ctrl::getHome.toRoutePath()
        }
    }

    // -----------------------------------------------------------------------
    // getUrl with function resolves path values using explicit expression paths
    // -----------------------------------------------------------------------

    @Test
    fun `getUrl with explicit expression path replaces placeholders`() {
        Router.clearRegistry()

        var urlGen: Routes? = null
        RouteTable.register(name = "explicit") {
            add("/capture", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }
        Router.route(
            UrlGenMockRequestSource(path = "/capture"),
            UrlGenMockResponseSource(),
            listOf("explicit")
        )

        // Using explicit path string with placeholders
        val url = urlGen!!.getUrl("/orders/{orderId}/items/{itemId}", "https://shop.com", "A123", "X456")
        assertEquals("https://shop.com/orders/A123/items/X456", url)
    }

    @Test
    fun `getUrl with path containing mixed literal and placeholder segments`() {
        Router.clearRegistry()

        var urlGen: Routes? = null
        RouteTable.register(name = "mixed") {
            add("/capture", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }
        Router.route(
            UrlGenMockRequestSource(path = "/capture"),
            UrlGenMockResponseSource(),
            listOf("mixed")
        )

        val url = urlGen!!.getUrl("/api/v1/users/{id}/profile", "https://app.com", 42)
        assertEquals("https://app.com/api/v1/users/42/profile", url)
    }

    // -----------------------------------------------------------------------
    // getUrl with empty/root paths
    // -----------------------------------------------------------------------

    @Test
    fun `getUrl with root path`() {
        Router.clearRegistry()

        var urlGen: Routes? = null
        RouteTable.register(name = "url-root-path") {
            add("/capture", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }
        Router.route(
            UrlGenMockRequestSource(path = "/capture"),
            UrlGenMockResponseSource(),
            listOf("url-root-path")
        )

        val url = urlGen!!.getUrl("/", "https://app.com")
        assertEquals("https://app.com/", url)
    }

    @Test
    fun `getUrl with function found in root returns rootUrl`() {
        Router.clearRegistry()

        val ctrl = UrlGenController()
        var urlGen: Routes? = null

        RouteTable.register(name = "root-func") {
            add(ctrl::getHome)
            add("/capture", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }
        Router.route(
            UrlGenMockRequestSource(
                path = "/capture",
                headers = listOf("Host" to listOf("example.com")),
            ),
            UrlGenMockResponseSource(),
            listOf("root-func")
        )

        // getHome at root has routePath = ""
        val url = urlGen!!.getUrl(ctrl::getHome, "https://example.com")
        assertEquals("https://example.com", url)
    }

    // -----------------------------------------------------------------------
    // Typed URL methods
    // -----------------------------------------------------------------------

    @Test
    fun `url returns typed URL from action in route tree`() {
        Router.clearRegistry()

        var urlGen: Routes? = null
        val pluginAction = TypedUrlAction(mapOf(
            "CSS" to { resource -> "/assets/css/$resource?v=abc123" }
        ))

        RouteTable.register(name = "typed-url") {
            add("/assets/{**}", HttpMethod.GET, pluginAction)
            add("/capture", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }
        Router.route(
            UrlGenMockRequestSource(path = "/capture"),
            UrlGenMockResponseSource(),
            listOf("typed-url")
        )

        val url = urlGen!!.url("CSS", "style.css", "https://app.com")
        assertEquals("https://app.com/assets/css/style.css?v=abc123", url)
    }

    @Test
    fun `url finds typed URL from other registered tree`() {
        Router.clearRegistry()

        var urlGen: Routes? = null
        val pluginAction = TypedUrlAction(mapOf(
            "IMG" to { resource -> "/cdn/$resource?t=999" }
        ))

        RouteTable.register(name = "other-tree") {
            add("/cdn/{**}", HttpMethod.GET, pluginAction)
        }

        RouteTable.register(name = "capture-tree") {
            add("/capture", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }
        Router.route(
            UrlGenMockRequestSource(path = "/capture"),
            UrlGenMockResponseSource(),
            listOf("capture-tree")
        )

        val url = urlGen!!.url("IMG", "image.png", "https://app.com")
        assertEquals("https://app.com/cdn/image.png?t=999", url)
    }

    @Test
    fun `url throws for unregistered type`() {
        Router.clearRegistry()

        var urlGen: Routes? = null

        RouteTable.register(name = "no-typed-url") {
            add("/capture", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }
        Router.route(
            UrlGenMockRequestSource(path = "/capture"),
            UrlGenMockResponseSource(),
            listOf("no-typed-url")
        )

        assertFailsWith<IllegalArgumentException> {
            urlGen!!.url("JS", "app.js", "https://app.com")
        }
    }
}
