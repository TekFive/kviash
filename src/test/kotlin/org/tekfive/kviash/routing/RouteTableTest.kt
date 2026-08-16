package org.tekfive.kviash.routing

import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.exchange.ExchangeAction
import org.tekfive.kviash.exchange.HttpErrorCode
import org.tekfive.kviash.exchange.interceptors.PipelineInterceptor
import org.tekfive.kviash.http.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Test stub implementations
// ---------------------------------------------------------------------------

class MockRequestSource(
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

class MockResponseSource : HttpResponseSource {
    var _status: Int = 200
    private val _headers = mutableListOf<HttpHeader>()
    private var _committed = false
    private val _outputStream = ByteArrayOutputStream()

    override val status: Int get() = _status
    override val headers: List<HttpHeader> get() = _headers.toList()
    override val committed: Boolean get() = _committed
    override val outputStream: OutputStream get() = _outputStream
    override val outputWriter: Writer get() = OutputStreamWriter(_outputStream)

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

    val bodyText: String get() = _outputStream.toString(Charsets.UTF_8)
}

// ---------------------------------------------------------------------------
// Test controllers
// ---------------------------------------------------------------------------

class TestController {
    fun get(): String = "root"
    fun getOne(): String = "one"
    fun getTwo(): String = "two"
    fun getThree(): String = "three"
    fun postOne(): String = "postOne"
    fun putOne(): String = "putOne"
    fun deleteOne(): String = "deleteOne"
    fun getNotFound(): String = "notFound"
    fun getById(id: Int): String = "id:$id"
    fun getByName(name: String): String = "name:$name"
    fun getByIdAndName(id: Int, name: String): String = "id:$id,name:$name"
    fun handleAll(): String = "handleAll"
}

object SingularParamTestHelper {
    var preFunctionInvoked = false
    var postFunctionInvoked = false

    fun preCheck(request: HttpRequest) { preFunctionInvoked = true }
    fun postCheck(request: HttpRequest) { postFunctionInvoked = true }
}

// ---------------------------------------------------------------------------
// Helper to route a request and return the response source + exchange result
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

class RouteTableTest {

    // -- Registration --

    @Test
    fun testRegister() {
        Router.clearRegistry()
        RouteTable.register("test") {
            add(TestController::get)
            with("/one/") {
                add(TestController::getOne)
                add("/two", TestController::getTwo)
            }
        }

        assertEquals(1, Router.routeTreesByName.size)
        assertEquals("test", Router.routeTreesByName[0].first)
    }

    @Test
    fun testDuplicateRegistrationFails() {
        Router.clearRegistry()
        RouteTable.register("dup") {
            add(TestController::get)
        }
        assertFailsWith<IllegalArgumentException> {
            RouteTable.register("dup") {
                add(TestController::getOne)
            }
        }
    }

    @Test
    fun testRouteWithNoRegistrationsFails() {
        Router.clearRegistry()
        assertFailsWith<IllegalStateException> {
            routeRequest(path = "/")
        }
    }

    // -- Basic GET routing --

    @Test
    fun testRootRoute() {
        Router.clearRegistry()
        RouteTable.register("basic") {
            add(TestController::get)
        }
        val response = routeRequest(path = "/")
        assertEquals(200, response.status)
    }

    @Test
    fun testNestedRoute() {
        Router.clearRegistry()
        RouteTable.register("nested") {
            add(TestController::get)
            with("/one") {
                add(TestController::getOne)
                add("/two", TestController::getTwo)
                with("/two") {
                    add("/three", TestController::getThree)
                }
            }
        }

        assertEquals(200, routeRequest(path = "/one").status)
        assertEquals(200, routeRequest(path = "/one/two").status)
        assertEquals(200, routeRequest(path = "/one/two/three").status)
    }

    // -- HTTP method routing --

    @Test
    fun testMethodInference() {
        Router.clearRegistry()
        RouteTable.register("methods") {
            with("/one") {
                add(TestController::getOne)
                add(TestController::postOne)
                add(TestController::putOne)
                add(TestController::deleteOne)
            }
        }

        assertEquals(200, routeRequest(method = "GET", path = "/one").status)
        assertEquals(200, routeRequest(method = "POST", path = "/one").status)
        assertEquals(200, routeRequest(method = "PUT", path = "/one").status)
        assertEquals(200, routeRequest(method = "DELETE", path = "/one").status)
    }

    @Test
    fun testExplicitMethodBinding() {
        Router.clearRegistry()
        RouteTable.register("explicit-method") {
            add("/endpoint", HttpMethod.POST, TestController::getOne)
        }

        // POST should match
        assertEquals(200, routeRequest(method = "POST", path = "/endpoint").status)
    }

    @Test
    fun testUnknownHttpMethodReturns404() {
        Router.clearRegistry()
        RouteTable.register("unknown-method") {
            add(TestController::get)
        }
        val response = routeRequest(method = "BOGUS", path = "/")
        assertEquals(404, response.status)
    }

    // -- Trailing slash handling --

    @Test
    fun testIgnoreTrailingSlash() {
        Router.clearRegistry()
        RouteTable.register("trailing", ignoreTrailingSlash = true) {
            with("/one") {
                add(TestController::getOne)
            }
        }

        assertEquals(200, routeRequest(path = "/one").status)
        assertEquals(200, routeRequest(path = "/one/").status)
    }

    @Test
    fun testPreserveTrailingSlash() {
        Router.clearRegistry()
        RouteTable.register("no-trailing", ignoreTrailingSlash = false) {
            add("/one", TestController::getOne)
            add("/two/", TestController::getTwo)
        }

        // Exact match
        assertEquals(200, routeRequest(path = "/one").status)
        assertEquals(200, routeRequest(path = "/two/").status)
    }

    // -- Unmatched routes --

    @Test
    fun testNoMatchReturns404() {
        Router.clearRegistry()
        RouteTable.register("no-match") {
            with("/exists") {
                add(TestController::getOne)
            }
        }

        val response = routeRequest(path = "/nonexistent")
        assertEquals(404, response.status)
    }

    // -- Not-found handlers --

    @Test
    fun testRootNotFoundHandler() {
        Router.clearRegistry()
        var handlerInvoked = false
        RouteTable.register("nf-root") {
            notFound { exchange -> handlerInvoked = true; null }
            with("/exists") {
                add(TestController::getOne)
            }
        }

        val response = routeRequest(path = "/nonexistent")
        assertEquals(404, response.status)
        assertTrue(handlerInvoked, "Root not-found handler should be invoked")
    }

    @Test
    fun testNotFoundHandlerDoesNotAffectMatchedRoutes() {
        Router.clearRegistry()
        var handlerInvoked = false
        RouteTable.register("nf-no-affect") {
            notFound { exchange -> handlerInvoked = true; null }
            with("/exists") {
                add(TestController::getOne)
            }
        }

        val response = routeRequest(path = "/exists")
        assertEquals(200, response.status)
        assertFalse(handlerInvoked, "Not-found handler should not be invoked for matched routes")
    }

    @Test
    fun testNestedNotFoundHandler() {
        Router.clearRegistry()
        var rootInvoked = false
        var nestedInvoked = false
        RouteTable.register("nf-nested") {
            notFound { exchange -> rootInvoked = true; null }
            with("/api") {
                notFound { exchange -> nestedInvoked = true; null }
                add(TestController::getOne)
            }
        }

        // Request under /api should use the nested handler
        routeRequest(path = "/api/nonexistent")
        assertFalse(rootInvoked, "Root not-found handler should not be invoked")
        assertTrue(nestedInvoked, "Nested not-found handler should be invoked")
    }

    @Test
    fun testNotFoundFallsBackToParentHandler() {
        Router.clearRegistry()
        var rootInvoked = false
        RouteTable.register("nf-fallback") {
            notFound { exchange -> rootInvoked = true; null }
            with("/api") {
                add(TestController::getOne)
            }
        }

        // /other is not under /api, so root handler should fire
        routeRequest(path = "/other/stuff")
        assertTrue(rootInvoked, "Root not-found handler should be invoked as fallback")
    }

    @Test
    fun testNotFoundWithFunction() {
        Router.clearRegistry()
        RouteTable.register("nf-function") {
            notFound(TestController::getNotFound)
            with("/exists") {
                add(TestController::getOne)
            }
        }

        val response = routeRequest(path = "/nonexistent")
        assertEquals(404, response.status)
    }

    @Test
    fun testNoNotFoundHandlerReturns404() {
        Router.clearRegistry()
        RouteTable.register("nf-none") {
            with("/exists") {
                add(TestController::getOne)
            }
        }

        val response = routeRequest(path = "/nonexistent")
        assertEquals(404, response.status)
    }

    // -- Parameterized routes --

    @Test
    fun testIntPathParameter() {
        Router.clearRegistry()
        RouteTable.register("param-int") {
            add("/items/{}", TestController::getById)
        }

        assertEquals(200, routeRequest(path = "/items/42").status)
    }

    @Test
    fun testStringPathParameter() {
        Router.clearRegistry()
        RouteTable.register("param-string") {
            add("/users/{}", TestController::getByName)
        }

        assertEquals(200, routeRequest(path = "/users/alice").status)
    }

    @Test
    fun testMultiplePathParameters() {
        Router.clearRegistry()
        RouteTable.register("param-multi") {
            add("/items/{}/{}", TestController::getByIdAndName)
        }

        assertEquals(200, routeRequest(path = "/items/42/widget").status)
    }

    // -- Wildcard and gobbler routes --

    @Test
    fun testWildcardSegment() {
        Router.clearRegistry()
        RouteTable.register("wildcard") {
            add("/files/{*}", TestController::getByName)
        }

        assertEquals(200, routeRequest(path = "/files/anything").status)
    }

    @Test
    fun testGobblerSegment() {
        Router.clearRegistry()
        RouteTable.register("gobbler") {
            add("/assets/{**}", TestController::get)
        }

        assertEquals(200, routeRequest(path = "/assets/css/style.css").status)
        assertEquals(200, routeRequest(path = "/assets/js/deep/nested/file.js").status)
    }

    // -- Explicit regex route --

    @Test
    fun testExplicitRegexSegment() {
        Router.clearRegistry()
        RouteTable.register("regex") {
            add("/items/{\\d+}", TestController::getById)
        }

        assertEquals(200, routeRequest(path = "/items/123").status)
    }

    // -- ExchangeAction-based routes --

    @Test
    fun testLambdaAction() {
        Router.clearRegistry()
        var invoked = false
        RouteTable.register("lambda") {
            add(HttpMethod.GET) { exchange ->
                invoked = true
                null
            }
        }

        routeRequest(path = "/")
        assertTrue(invoked, "Lambda action should be invoked")
    }

    @Test
    fun testActionWithExplicitPath() {
        Router.clearRegistry()
        var invoked = false
        RouteTable.register("lambda-path") {
            add("/custom", HttpMethod.GET) { exchange ->
                invoked = true
                null
            }
        }

        routeRequest(path = "/custom")
        assertTrue(invoked, "Lambda action at /custom should be invoked")
    }

    @Test
    fun testActionWithMultipleMethods() {
        Router.clearRegistry()
        var invokeCount = 0
        RouteTable.register("multi-method") {
            add(setOf(HttpMethod.GET, HttpMethod.POST)) { exchange ->
                invokeCount++
                null
            }
        }

        routeRequest(method = "GET", path = "/")
        routeRequest(method = "POST", path = "/")
        assertEquals(2, invokeCount)
    }

    // -- Multiple route trees --

    @Test
    fun testMultipleNamedRouteTrees() {
        Router.clearRegistry()
        var apiInvoked = false
        var webInvoked = false

        RouteTable.register("api") {
            add("/api/data", HttpMethod.GET) { apiInvoked = true; null }
        }
        RouteTable.register("web") {
            add("/web/page", HttpMethod.GET) { webInvoked = true; null }
        }

        // Route using specific tree names
        routeRequest(path = "/api/data", routeTreeNames = listOf("api"))
        assertTrue(apiInvoked)

        routeRequest(path = "/web/page", routeTreeNames = listOf("web"))
        assertTrue(webInvoked)
    }

    @Test
    fun testInvalidRouteTreeNameFails() {
        Router.clearRegistry()
        RouteTable.register("real") {
            add(TestController::get)
        }

        assertFailsWith<IllegalArgumentException> {
            routeRequest(path = "/", routeTreeNames = listOf("nonexistent"))
        }
    }

    @Test
    fun testAllTreesSearchedWhenNoNamesSpecified() {
        Router.clearRegistry()
        var firstInvoked = false
        var secondInvoked = false

        RouteTable.register("first") {
            add("/a", HttpMethod.GET) { firstInvoked = true; null }
        }
        RouteTable.register("second") {
            add("/b", HttpMethod.GET) { secondInvoked = true; null }
        }

        routeRequest(path = "/a")
        assertTrue(firstInvoked)

        routeRequest(path = "/b")
        assertTrue(secondInvoked)
    }

    // -- Scoped routes with `with` --

    @Test
    fun testDeeplyNestedWith() {
        Router.clearRegistry()
        var invoked = false
        RouteTable.register("deep-with") {
            with("/a") {
                with("/b") {
                    with("/c") {
                        add(HttpMethod.GET) { invoked = true; null }
                    }
                }
            }
        }

        routeRequest(path = "/a/b/c")
        assertTrue(invoked, "Deeply nested with() route should match")
    }

    @Test
    fun testMultipleRoutesInSameScope() {
        Router.clearRegistry()
        var fn1 = false
        var fn2 = false
        RouteTable.register("same-scope") {
            with("/api") {
                add("/users", HttpMethod.GET) { fn1 = true; null }
                add("/items", HttpMethod.GET) { fn2 = true; null }
            }
        }

        routeRequest(path = "/api/users")
        assertTrue(fn1)

        routeRequest(path = "/api/items")
        assertTrue(fn2)
    }

    @Test
    fun testWithScopeIsRemovedAfterCaughtException() {
        Router.clearRegistry()
        var invoked = false

        RouteTable.register("scope-cleanup") {
            try {
                with("/bad") {
                    throw IllegalStateException("registration failed")
                }
            } catch (e: IllegalStateException) {
                // Continue registering routes after a failed nested scope.
            }
            add("/after", HttpMethod.GET) { invoked = true; null }
        }

        assertEquals(200, routeRequest(path = "/after").status)
        assertTrue(invoked)
        assertEquals(404, routeRequest(path = "/bad/after").status)
    }

    @Test
    fun testRouteAttributesMergeWithMoreSpecificValuesWinning() {
        Router.clearRegistry()
        val captured = mutableMapOf<String, Any?>()

        RouteTable.register(
            "route-attributes",
            routeAttributes = listOf("shared" to "root", "rootOnly" to "root")
        ) {
            with(routeAttributes = listOf("shared" to "scope", "scopeOnly" to "scope")) {
                add("/attrs", HttpMethod.GET, "shared" to "route", "routeOnly" to "route") { exchange ->
                    captured["shared"] = exchange.getRouteAttribute("shared")
                    captured["rootOnly"] = exchange.getRouteAttribute("rootOnly")
                    captured["scopeOnly"] = exchange.getRouteAttribute("scopeOnly")
                    captured["routeOnly"] = exchange.getRouteAttribute("routeOnly")
                    null
                }
            }
        }

        assertEquals(200, routeRequest(path = "/attrs").status)
        assertEquals("route", captured["shared"])
        assertEquals("root", captured["rootOnly"])
        assertEquals("scope", captured["scopeOnly"])
        assertEquals("route", captured["routeOnly"])
    }

    // -- Duplicate route detection --

    @Test
    fun testDuplicateRouteThrows() {
        Router.clearRegistry()
        assertFailsWith<IllegalStateException> {
            RouteTable.register("dup-route") {
                add("/same", HttpMethod.GET) { null }
                add("/same", HttpMethod.GET) { null }
            }
        }
    }

    @Test
    fun `case-insensitive duplicate route throws`() {
        Router.clearRegistry()
        assertFailsWith<IllegalStateException> {
            RouteTable.register("case-duplicate") {
                add("/Users", HttpMethod.GET) { null }
                add("/users", HttpMethod.GET) { null }
            }
        }
    }

    // -- Case sensitivity --

    @Test
    fun testCaseInsensitiveRoutingDefault() {
        Router.clearRegistry()
        var invoked = false
        RouteTable.register("case-insensitive") {
            add("/Hello/World", HttpMethod.GET) { invoked = true; null }
        }

        routeRequest(path = "/hello/world")
        assertTrue(invoked, "Case-insensitive routing should match")
    }

    @Test
    fun `interceptor exception sets internal server error`() {
        Router.clearRegistry()
        val interceptor = object : PipelineInterceptor {
            override fun intercept(exchange: Exchange, continuePipeline: (Exchange) -> Unit) {
                throw IllegalStateException("interceptor failed")
            }
        }
        RouteTable.register("interceptor-error", interceptor = interceptor) {
            add("/failure", HttpMethod.GET) { null }
        }

        assertEquals(500, routeRequest(path = "/failure").status)
    }

    // -- Multi-function add convenience methods --

    @Test
    fun testAddTwoFunctions() {
        Router.clearRegistry()
        RouteTable.register("two-fn") {
            with("/items") {
                add(TestController::getOne, TestController::postOne)
            }
        }

        assertEquals(200, routeRequest(method = "GET", path = "/items").status)
        assertEquals(200, routeRequest(method = "POST", path = "/items").status)
    }

    @Test
    fun testAddThreeFunctions() {
        Router.clearRegistry()
        RouteTable.register("three-fn") {
            with("/items") {
                add(TestController::getOne, TestController::postOne, TestController::putOne)
            }
        }

        assertEquals(200, routeRequest(method = "GET", path = "/items").status)
        assertEquals(200, routeRequest(method = "POST", path = "/items").status)
        assertEquals(200, routeRequest(method = "PUT", path = "/items").status)
    }

    @Test
    fun testAddFourFunctions() {
        Router.clearRegistry()
        RouteTable.register("four-fn") {
            with("/items") {
                add(TestController::getOne, TestController::postOne, TestController::putOne, TestController::deleteOne)
            }
        }

        assertEquals(200, routeRequest(method = "GET", path = "/items").status)
        assertEquals(200, routeRequest(method = "POST", path = "/items").status)
        assertEquals(200, routeRequest(method = "PUT", path = "/items").status)
        assertEquals(200, routeRequest(method = "DELETE", path = "/items").status)
    }

    // -- Route path with explicit path + multi-function --

    @Test
    fun testAddTwoFunctionsWithPath() {
        Router.clearRegistry()
        RouteTable.register("two-fn-path") {
            add("/items", TestController::getOne, TestController::postOne)
        }

        assertEquals(200, routeRequest(method = "GET", path = "/items").status)
        assertEquals(200, routeRequest(method = "POST", path = "/items").status)
    }

    // -- getBestMethods inference --

    @Test
    fun testGetBestMethodsInference() {
        assertEquals(setOf(HttpMethod.GET), RouteTable.getBestMethods("getUsers"))
        assertEquals(setOf(HttpMethod.POST), RouteTable.getBestMethods("postUser"))
        assertEquals(setOf(HttpMethod.PUT), RouteTable.getBestMethods("putUser"))
        assertEquals(setOf(HttpMethod.DELETE), RouteTable.getBestMethods("deleteUser"))
        assertEquals(setOf(HttpMethod.PATCH), RouteTable.getBestMethods("patchUser"))
        // Unknown prefix returns all methods
        assertEquals(HttpMethod.values().toSet(), RouteTable.getBestMethods("handleUser"))
    }

    // -- handleAll routes to all methods --

    @Test
    fun testFunctionWithNoMethodPrefixMatchesAllMethods() {
        Router.clearRegistry()
        RouteTable.register("all-methods") {
            add("/handle", TestController::handleAll)
        }

        assertEquals(200, routeRequest(method = "GET", path = "/handle").status)
        assertEquals(200, routeRequest(method = "POST", path = "/handle").status)
        assertEquals(200, routeRequest(method = "PUT", path = "/handle").status)
        assertEquals(200, routeRequest(method = "DELETE", path = "/handle").status)
    }

    // -- Inferred path parameter with trailing segments --

    @Test
    fun testInferredParameterWithTrailingLiteralSegment() {
        Router.clearRegistry()
        RouteTable.register("param-trailing") {
            add("/reports", TestController::getOne)
            add("/reports/{}/view", TestController::getById)
        }

        // Inferred {} should match numeric segment based on function parameter type (Int)
        assertEquals(200, routeRequest(path = "/reports/42/view").status)
    }

    // -- Gobbler must be last segment --

    @Test
    fun testGobblerNotLastSegmentThrows() {
        Router.clearRegistry()
        assertFailsWith<IllegalArgumentException> {
            RouteTable.register("gobbler-mid") {
                add("/assets/{**}/more", TestController::get)
            }
        }
    }

    // -- Route configuration errors --

    @Test
    fun testInferredExpressionWithoutMatchingParameterThrows() {
        Router.clearRegistry()
        assertFailsWith<RoutesConfigurationException> {
            RouteTable.register("bad-inferred") {
                // get() has no path parameters, but route has {} needing one
                add("/items/{}", TestController::get)
            }
        }
    }

    @Test
    fun testTooManyPathParametersThrows() {
        Router.clearRegistry()
        assertFailsWith<RoutesConfigurationException> {
            RouteTable.register("too-many-params") {
                // getById takes 1 Int parameter, but route has 2 inferred segments
                add("/items/{}/{}", TestController::getById)
            }
        }
    }

    // -- Singular parameter support --

    @Test
    fun testSingularPreFunctionRunsAsPreAction() {
        Router.clearRegistry()
        SingularParamTestHelper.preFunctionInvoked = false

        RouteTable.register("singular-prefn") {
            with(preFunction = SingularParamTestHelper::preCheck) {
                add(HttpMethod.GET) { null }
            }
        }

        routeRequest(path = "/")
        assertTrue(SingularParamTestHelper.preFunctionInvoked, "Singular preFunction should run as a pre-action")
    }

    @Test
    fun testSingularPreActionRunsAsPreAction() {
        Router.clearRegistry()
        var preActionInvoked = false

        RouteTable.register("singular-preact") {
            with(preAction = ExchangeAction { preActionInvoked = true; null }) {
                add(HttpMethod.GET) { null }
            }
        }

        routeRequest(path = "/")
        assertTrue(preActionInvoked, "Singular preAction should run as a pre-action")
    }

    @Test
    fun testSingularPostFunctionRunsAsPostAction() {
        Router.clearRegistry()
        SingularParamTestHelper.postFunctionInvoked = false

        RouteTable.register("singular-postfn") {
            with(postFunction = SingularParamTestHelper::postCheck) {
                add(HttpMethod.GET) { null }
            }
        }

        routeRequest(path = "/")
        assertTrue(SingularParamTestHelper.postFunctionInvoked, "Singular postFunction should run as a post-action")
    }

    @Test
    fun testSingularPostActionRunsAsPostAction() {
        Router.clearRegistry()
        var postActionInvoked = false

        RouteTable.register("singular-postact") {
            with(postAction = ExchangeAction { postActionInvoked = true; null }) {
                add(HttpMethod.GET) { null }
            }
        }

        routeRequest(path = "/")
        assertTrue(postActionInvoked, "Singular postAction should run as a post-action")
    }

    @Test
    fun testSingularAndPluralMergeTogether() {
        Router.clearRegistry()
        val invocationOrder = mutableListOf<String>()

        RouteTable.register("singular-plural-merge") {
            with(
                preAction = ExchangeAction { invocationOrder.add("singular"); null },
                preActions = listOf(ExchangeAction { invocationOrder.add("plural"); null })
            ) {
                add(HttpMethod.GET) { null }
            }
        }

        routeRequest(path = "/")
        assertEquals(listOf("singular", "plural"), invocationOrder, "Singular should be prepended to plural list")
    }

    @Test
    fun testSingularParamsInRegisterFlowThrough() {
        Router.clearRegistry()
        var preActionInvoked = false

        RouteTable.register("singular-register", preAction = ExchangeAction { preActionInvoked = true; null }) {
            add(HttpMethod.GET) { null }
        }

        routeRequest(path = "/")
        assertTrue(preActionInvoked, "Singular preAction on register() should flow through to with()")
    }

    @Test
    fun testNestedScopesWithSingularParamsAccumulate() {
        Router.clearRegistry()
        val invocationOrder = mutableListOf<String>()

        RouteTable.register("singular-nested") {
            with(preAction = ExchangeAction { invocationOrder.add("outer"); null }) {
                with(preAction = ExchangeAction { invocationOrder.add("inner"); null }) {
                    add(HttpMethod.GET) { null }
                }
            }
        }

        routeRequest(path = "/")
        assertEquals(listOf("outer", "inner"), invocationOrder, "Nested singular preActions should accumulate (outer before inner)")
    }
}
