package org.tekfive.kviash.exchange.actions.static

import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.exchange.interceptors.MockRequestSource
import org.tekfive.kviash.exchange.interceptors.MockResponseSource
import org.tekfive.kviash.exchange.interceptors.createTestExchange
import org.tekfive.kviash.http.HttpRequestPath
import org.tekfive.kviash.http.toPathSegments
import java.io.File
import org.tekfive.kviash.http.HttpMethod
import org.tekfive.kviash.routing.Router
import org.tekfive.kviash.routing.RouteTable
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StaticResourcesTest {

    private val staticResources = StaticResources(
        resourceProvider = ClasspathResourceProvider(useUrlPrefix = true),
    ).also { it.onRouteRegistered("/static") }

    private fun exchange(path: String, method: String = "GET"): Pair<MockResponseSource, Exchange> {
        val rs = MockResponseSource()
        val exchange = createTestExchange(
            requestSource = MockRequestSource(method = method, path = path),
            responseSource = rs,
        )
        val requestPath = HttpRequestPath(path.toPathSegments(true))
        return rs to Exchange(exchange, requestPath = requestPath)
    }

    @Test
    fun `serves classpath resource with correct content type`() {
        val (rs, ex) = exchange("/static/css/app.css")
        staticResources(ex)

        assertEquals("text/css", rs.headerValues("Content-Type").firstOrNull())
        val body = rs._outputStream.toString(Charsets.UTF_8)
        assertContains(body, "body")
    }

    @Test
    fun `serves json resource`() {
        val (rs, ex) = exchange("/static/data.json")
        staticResources(ex)

        assertEquals("application/json", rs.headerValues("Content-Type").firstOrNull())
        val body = rs._outputStream.toString(Charsets.UTF_8)
        assertContains(body, "key")
    }

    @Test
    fun `returns 404 for missing resource`() {
        val (rs, ex) = exchange("/static/nonexistent.css")
        staticResources(ex)

        assertEquals(404, rs.status)
    }

    @Test
    fun `rejects path traversal`() {
        val (rs, ex) = exchange("/static/../secret.txt")
        staticResources(ex)

        assertEquals(404, rs.status)
    }

    @Test
    fun `sets cache control header`() {
        val (rs, ex) = exchange("/static/css/app.css")
        staticResources(ex)

        val cacheControl = rs.headerValues("Cache-Control").firstOrNull()
        assertEquals("public, max-age=31536000, immutable", cacheControl)
    }

    @Test
    fun `sets content length header`() {
        val (rs, ex) = exchange("/static/css/app.css")
        staticResources(ex)

        val contentLength = rs.headerValues("Content-Length").firstOrNull()
        assertTrue(contentLength != null && contentLength.toLong() > 0)
    }

    @Test
    fun `getUrl returns URL with content hash`() {
        val url = staticResources.getUrl("css/app.css")

        assertTrue(url.startsWith("/static/css/app.css?v="))
        val hash = url.substringAfter("?v=")
        assertEquals(8, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `getUrl returns URL without hash for missing resource`() {
        val url = staticResources.getUrl("nonexistent.css")

        assertEquals("/static/nonexistent.css", url)
    }

    @Test
    fun `getUrl caches hash across calls`() {
        val url1 = staticResources.getUrl("css/app.css")
        val url2 = staticResources.getUrl("css/app.css")

        assertEquals(url1, url2)
    }

    @Test
    fun `returns 404 for empty resource path`() {
        val (rs, ex) = exchange("/static")
        staticResources(ex)

        assertEquals(404, rs.status)
    }

    // --- ResourceProvider tests ---

    @Test
    fun `constructs with ResourceProvider`() {
        val provider = MemoryResourceProvider(mapOf("hello.txt" to "hello".toByteArray()))
        val sr = StaticResources(resourceProvider = provider)
        sr.onRouteRegistered("/assets")

        val url = sr.getUrl("hello.txt")
        assertTrue(url.startsWith("/assets/hello.txt?v="))
    }

    @Test
    fun `serves resources from MemoryResourceProvider`() {
        val content = "body { color: blue; }"
        val provider = MemoryResourceProvider(mapOf("style.css" to content.toByteArray()))
        val sr = StaticResources(resourceProvider = provider).also { it.onRouteRegistered("/static") }

        val (rs, ex) = exchange("/static/style.css")
        sr(ex)

        assertEquals("text/css", rs.headerValues("Content-Type").firstOrNull())
        assertEquals(content, rs._outputStream.toString(Charsets.UTF_8))
    }

    @Test
    fun `MemoryResourceProvider returns null for missing path`() {
        val provider = MemoryResourceProvider(mapOf("a.txt" to "a".toByteArray()))
        assertNull(provider.get("b.txt", "b.txt"))
    }

    @Test
    fun `ClasspathResourceProvider loads resources`() {
        val provider = ClasspathResourceProvider(useUrlPrefix = true)
        val bytes = provider.get("/static", "css/app.css")
        assertNotNull(bytes)
        assertTrue(String(bytes).contains("body"))
    }

    @Test
    fun `ClasspathResourceProvider returns null for missing resource`() {
        val provider = ClasspathResourceProvider(useUrlPrefix = true)
        assertNull(provider.get("/static", "nonexistent.css"))
    }

    @Test
    fun `FileResourceProvider loads resources from directory`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "kviash-test-${System.nanoTime()}")
        try {
            tempDir.mkdirs()
            val file = File(tempDir, "test.txt")
            file.writeText("file content")

            val provider = FileResourceProvider(tempDir)
            val bytes = provider.get("test.txt", "test.txt")
            assertNotNull(bytes)
            assertEquals("file content", String(bytes))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `FileResourceProvider returns null for missing file`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "kviash-test-${System.nanoTime()}")
        try {
            tempDir.mkdirs()
            val provider = FileResourceProvider(tempDir)
            assertNull(provider.get("missing.txt", "missing.txt"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `FileResourceProvider rejects path traversal`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "kviash-test-${System.nanoTime()}")
        try {
            tempDir.mkdirs()
            val provider = FileResourceProvider(tempDir)
            assertNull(provider.get("../../../etc/passwd", "../../../etc/passwd"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `FileResourceProvider rejects symlink escape`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "kviash-test-${System.nanoTime()}")
        val outsideFile = File(System.getProperty("java.io.tmpdir"), "kviash-outside-${System.nanoTime()}.txt")
        try {
            tempDir.mkdirs()
            outsideFile.writeText("outside")
            Files.createSymbolicLink(File(tempDir, "link.txt").toPath(), outsideFile.toPath())

            val provider = FileResourceProvider(tempDir)
            assertNull(provider.get("link.txt", "link.txt"))
        } finally {
            tempDir.deleteRecursively()
            outsideFile.delete()
        }
    }

    @Test
    fun `ResourceProvider used as lambda`() {
        val provider = ResourceProvider { path, _ ->
            if (path == "greeting.txt") "hello world".toByteArray() else null
        }

        assertNotNull(provider.get("greeting.txt", "greeting.txt"))
        assertNull(provider.get("other.txt", "other.txt"))
    }


    @Test
    fun `typedUrl returns versioned URL for registered type`() {
        val provider = MemoryResourceProvider(mapOf("js/bootstrap.js" to "console.log('hi')".toByteArray()))
        val sr = StaticResources(
            resourceProvider = provider,
            keyedResourceDirs = setOf(KeyedResourceDir.JS),
        ).also { it.onRouteRegistered("/static") }

        val url = sr.typedUrl("JS", "bootstrap.js")
        assertNotNull(url)
        assertTrue(url.startsWith("/static/js/bootstrap.js?v="))
    }

    @Test
    fun `typedUrl returns null for unregistered type`() {
        val provider = MemoryResourceProvider(mapOf("js/bootstrap.js" to "console.log('hi')".toByteArray()))
        val sr = StaticResources(
            resourceProvider = provider,
            keyedResourceDirs = setOf(KeyedResourceDir.JS),
        ).also { it.onRouteRegistered("/static") }

        assertNull(sr.typedUrl("CSS", "style.css"))
    }

    @Test
    fun `urlTypes returns registered type names`() {
        val sr = StaticResources(
            resourceProvider = MemoryResourceProvider(emptyMap()),
            keyedResourceDirs = setOf(KeyedResourceDir.JS, KeyedResourceDir.CSS),
        ).also { it.onRouteRegistered("/static") }

        assertEquals(setOf("JS", "CSS"), sr.urlTypes())
    }

    @Test
    fun `Routes url returns typed URL via StaticResources`() {
        Router.clearRegistry()

        val provider = MemoryResourceProvider(mapOf("js/bootstrap.js" to "console.log('hi')".toByteArray()))
        val sr = StaticResources(
            resourceProvider = provider,
            keyedResourceDirs = setOf(KeyedResourceDir.JS),
        )

        var urlGen: org.tekfive.kviash.routing.Routes? = null

        RouteTable.register(name = "typed-url-plugin") {
            add("/static/{**}", HttpMethod.GET, sr)
            add("/capture", HttpMethod.GET) { ex ->
                urlGen = ex.routes
                null
            }
        }
        Router.route(
            MockRequestSource(path = "/capture"),
            MockResponseSource(),
            listOf("typed-url-plugin")
        )

        val url = urlGen!!.url("JS", "bootstrap.js", "https://app.com")
        assertTrue(url.startsWith("https://app.com/static/js/bootstrap.js?v="))
    }

    @Test
    fun `learns prefix from route registration`() {
        val provider = MemoryResourceProvider(mapOf("style.css" to "body{}".toByteArray()))
        val sr = StaticResources(resourceProvider = provider)

        sr.onRouteRegistered("/assets")

        val url = sr.getUrl("style.css")
        assertTrue(url.startsWith("/assets/style.css?v="))
    }

    @Test
    fun `onRouteRegistered can only be called once`() {
        val provider = MemoryResourceProvider(mapOf("style.css" to "body{}".toByteArray()))
        val sr = StaticResources(resourceProvider = provider)

        sr.onRouteRegistered("/first")

        val ex = kotlin.runCatching { sr.onRouteRegistered("/second") }.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `error when urlPrefix not initialized`() {
        val provider = MemoryResourceProvider(mapOf("style.css" to "body{}".toByteArray()))
        val sr = StaticResources(resourceProvider = provider)

        val ex = kotlin.runCatching { sr.getUrl("style.css") }.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(ex is UninitializedPropertyAccessException)
    }

    @Test
    fun `auto-prefix works end-to-end with RouteTable registration`() {
        Router.clearRegistry()

        val provider = MemoryResourceProvider(mapOf("style.css" to "body{}".toByteArray()))
        val sr = StaticResources(resourceProvider = provider)

        RouteTable.register(name = "auto-prefix-test") {
            with(path = "/assets", urlPlugin = sr) {
                add(HttpMethod.GET) { ex -> null }
            }
        }

        val url = sr.getUrl("style.css")
        assertTrue(url.startsWith("/assets/style.css?v="))
    }

    @Test
    fun `onRouteRegistered strips dynamic segments`() {
        val provider = MemoryResourceProvider(mapOf("style.css" to "body{}".toByteArray()))
        val sr = StaticResources(resourceProvider = provider)

        sr.onRouteRegistered("/static/{**}")

        val url = sr.getUrl("style.css")
        assertTrue(url.startsWith("/static/style.css?v="))
    }

    @Test
    fun `onRouteRegistered strips dynamic segments from multi-segment path`() {
        val provider = MemoryResourceProvider(mapOf("style.css" to "body{}".toByteArray()))
        val sr = StaticResources(resourceProvider = provider)

        sr.onRouteRegistered("/assets/v1/{**}")

        val url = sr.getUrl("style.css")
        assertTrue(url.startsWith("/assets/v1/style.css?v="))
    }

    @Test
    fun `works as ExchangeAction`() {
        val provider = MemoryResourceProvider(mapOf("css/app.css" to "body{}".toByteArray()))
        val sr = StaticResources(resourceProvider = provider).also { it.onRouteRegistered("/static") }

        val (rs, ex) = exchange("/static/css/app.css")
        val result = sr.invoke(ex)

        assertNull(result)
        assertEquals("text/css", rs.headerValues("Content-Type").firstOrNull())
    }

    @Test
    fun `add with action triggers onRouteRegistered`() {
        Router.clearRegistry()

        val provider = MemoryResourceProvider(mapOf("style.css" to "body{}".toByteArray()))
        val sr = StaticResources(resourceProvider = provider)

        RouteTable.register(name = "add-action-test") {
            add("/static/{**}", HttpMethod.GET, sr)
        }

        val url = sr.getUrl("style.css")
        assertTrue(url.startsWith("/static/style.css?v="))
    }

    // --- ETag tests ---

    @Test
    fun `etag enabled sets ETag header`() {
        val content = "body{}"
        val provider = MemoryResourceProvider(mapOf("style.css" to content.toByteArray()))
        val sr = StaticResources(resourceProvider = provider, enableETag = true).also { it.onRouteRegistered("/static") }

        val (rs, ex) = exchange("/static/style.css")
        sr(ex)

        val etag = rs.headerValues("ETag")
        assertTrue(etag.isNotEmpty())
        assertTrue(etag[0].startsWith("\"") && etag[0].endsWith("\""))
        assertEquals(content, rs._outputStream.toString(Charsets.UTF_8))
    }

    @Test
    fun `etag disabled does not set ETag header`() {
        val (rs, ex) = exchange("/static/css/app.css")
        staticResources(ex)

        assertTrue(rs.headerValues("ETag").isEmpty())
    }

    @Test
    fun `returns 304 when If-None-Match matches ETag`() {
        val content = "body{}"
        val provider = MemoryResourceProvider(mapOf("style.css" to content.toByteArray()))
        val sr = StaticResources(resourceProvider = provider, enableETag = true).also { it.onRouteRegistered("/static") }

        val etag = "\"${StaticResources.computeETag(content.toByteArray())}\""

        val rs = MockResponseSource()
        val ex = createTestExchange(
            requestSource = MockRequestSource(
                path = "/static/style.css",
                headers = listOf("Host" to listOf("localhost"), "If-None-Match" to listOf(etag))
            ),
            responseSource = rs,
        )
        val requestPath = HttpRequestPath("/static/style.css".toPathSegments(true))
        sr(Exchange(ex, requestPath = requestPath))

        assertEquals(304, rs.status)
        assertTrue(rs.committed)
        assertTrue(rs._outputStream.size() == 0)
    }

    @Test
    fun `returns body when If-None-Match does not match`() {
        val content = "body{}"
        val provider = MemoryResourceProvider(mapOf("style.css" to content.toByteArray()))
        val sr = StaticResources(resourceProvider = provider, enableETag = true).also { it.onRouteRegistered("/static") }

        val rs = MockResponseSource()
        val ex = createTestExchange(
            requestSource = MockRequestSource(
                path = "/static/style.css",
                headers = listOf("Host" to listOf("localhost"), "If-None-Match" to listOf("\"stale\""))
            ),
            responseSource = rs,
        )
        val requestPath = HttpRequestPath("/static/style.css".toPathSegments(true))
        sr(Exchange(ex, requestPath = requestPath))

        assertEquals(200, rs.status)
        assertEquals(content, rs._outputStream.toString(Charsets.UTF_8))
    }

    // --- HEAD request tests ---

    @Test
    fun `HEAD returns headers without body`() {
        val (rs, ex) = exchange("/static/css/app.css", method = "HEAD")
        staticResources(ex)

        assertEquals("text/css", rs.headerValues("Content-Type").firstOrNull())
        val contentLength = rs.headerValues("Content-Length").firstOrNull()
        assertTrue(contentLength != null && contentLength.toLong() > 0)
        assertEquals(0, rs._outputStream.size())
    }

    @Test
    fun `HEAD returns ETag without body when enabled`() {
        val content = "body{}"
        val provider = MemoryResourceProvider(mapOf("style.css" to content.toByteArray()))
        val sr = StaticResources(resourceProvider = provider, enableETag = true).also { it.onRouteRegistered("/static") }

        val (rs, ex) = exchange("/static/style.css", method = "HEAD")
        sr(ex)

        assertTrue(rs.headerValues("ETag").isNotEmpty())
        assertEquals(content.toByteArray().size.toLong(), rs.headerValues("Content-Length").firstOrNull()?.toLong())
        assertEquals(0, rs._outputStream.size())
    }
}
