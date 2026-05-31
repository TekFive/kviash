package org.tekfive.kviash.exchange

import org.tekfive.jfk.FromJsonObject
import org.tekfive.jfk.JsonArray
import org.tekfive.jfk.JsonObject
import org.tekfive.kviash.exchange.actions.content.SendJfkResponse
import org.tekfive.kviash.http.HttpRequestContent
import org.tekfive.kviash.routing.MockRequestSource
import org.tekfive.kviash.routing.MockResponseSource
import org.tekfive.kviash.routing.RouteTable
import org.tekfive.kviash.routing.RoutesConfigurationException
import org.tekfive.kviash.routing.Router
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

object JsonBodyController {
    var lastJsonObject: JsonObject? = null
    var lastJsonArray: JsonArray? = null
    var lastTypedBody: TestBody? = null
    var postInvoked = false
    var nullableInvoked = false
    var optionalInvoked = false

    fun reset() {
        lastJsonObject = null
        lastJsonArray = null
        lastTypedBody = null
        postInvoked = false
        nullableInvoked = false
        optionalInvoked = false
    }

    fun postObject(body: JsonObject): JsonObject {
        postInvoked = true
        lastJsonObject = body
        return body
    }

    fun postArray(body: JsonArray): JsonObject {
        postInvoked = true
        lastJsonArray = body
        return JsonObject()
    }

    fun postTyped(body: TestBody): JsonObject {
        postInvoked = true
        lastTypedBody = body
        return JsonObject(mapOf("name" to body.name, "count" to body.count))
    }

    fun postNullableObject(body: JsonObject?): JsonObject {
        nullableInvoked = true
        lastJsonObject = body
        return JsonObject()
    }

    fun postNullableArray(body: JsonArray?): JsonObject {
        nullableInvoked = true
        lastJsonArray = body
        return JsonObject()
    }

    fun postOptionalObject(body: JsonObject = JsonObject()): JsonObject {
        optionalInvoked = true
        lastJsonObject = body
        return body
    }

    fun postTwoRequiredBodies(a: JsonObject, b: JsonArray): JsonObject {
        return a
    }

    fun postRequiredAndNullable(a: JsonObject, b: JsonArray?): JsonObject {
        return a
    }

    fun postRequiredAndOptional(a: JsonObject, b: JsonArray = JsonArray()): JsonObject {
        return a
    }

    fun postRequiredJsonAndContent(body: JsonObject, content: HttpRequestContent): JsonObject {
        return body
    }
}

class TestBody(
    val name: String,
    val count: Int,
) {
    companion object : FromJsonObject<TestBody>
}

private fun routeRequest(
    method: String = "GET",
    path: String = "/",
    body: String? = null,
): MockResponseSource {
    val inputStream = body?.let { ByteArrayInputStream(it.toByteArray()) }
    val request = MockRequestSource(method = method, path = path, inputStream = inputStream)
    val response = MockResponseSource()
    Router.route(request, response)
    return response
}

class JfkParameterInjectionTest {

    @Test
    fun `JsonObject parameter receives parsed body`() {
        Router.clearRegistry()
        JsonBodyController.reset()
        RouteTable.register("json-obj", postAction = SendJfkResponse) {
            add("/items", JsonBodyController::postObject)
        }

        val response = routeRequest(method = "POST", path = "/items", body = """{"name":"test","count":42}""")
        assertEquals(200, response.status)
        assertEquals(true, JsonBodyController.postInvoked)
        assertEquals("test", JsonBodyController.lastJsonObject?.string("name"))
        assertEquals(42, JsonBodyController.lastJsonObject?.get("count")?.int)
    }

    @Test
    fun `JsonArray parameter receives parsed body`() {
        Router.clearRegistry()
        JsonBodyController.reset()
        RouteTable.register("json-arr", postAction = SendJfkResponse) {
            add("/items", JsonBodyController::postArray)
        }

        val response = routeRequest(method = "POST", path = "/items", body = """[1,2,3]""")
        assertEquals(200, response.status)
        assertEquals(true, JsonBodyController.postInvoked)
        assertEquals(3, JsonBodyController.lastJsonArray?.items?.size)
    }

    @Test
    fun `FromJsonObject parameter receives parsed body`() {
        Router.clearRegistry()
        JsonBodyController.reset()
        RouteTable.register("json-from-object", postAction = SendJfkResponse) {
            add("/items", JsonBodyController::postTyped)
        }

        val response = routeRequest(method = "POST", path = "/items", body = """{"name":"test","count":42}""")
        assertEquals(200, response.status)
        assertEquals(true, JsonBodyController.postInvoked)
        assertEquals("test", JsonBodyController.lastTypedBody?.name)
        assertEquals(42, JsonBodyController.lastTypedBody?.count)
    }

    @Test
    fun `required JsonObject returns 400 on invalid JSON`() {
        Router.clearRegistry()
        JsonBodyController.reset()
        RouteTable.register("json-obj-bad", postAction = SendJfkResponse) {
            add("/items", JsonBodyController::postObject)
        }

        val response = routeRequest(method = "POST", path = "/items", body = "not json")
        assertEquals(400, response.status)
    }

    @Test
    fun `required JsonObject returns 400 on empty body`() {
        Router.clearRegistry()
        JsonBodyController.reset()
        RouteTable.register("json-obj-empty", postAction = SendJfkResponse) {
            add("/items", JsonBodyController::postObject)
        }

        val response = routeRequest(method = "POST", path = "/items", body = null)
        assertEquals(400, response.status)
    }

    @Test
    fun `required JsonObject returns 400 when body is a JSON array`() {
        Router.clearRegistry()
        JsonBodyController.reset()
        RouteTable.register("json-obj-arr", postAction = SendJfkResponse) {
            add("/items", JsonBodyController::postObject)
        }

        val response = routeRequest(method = "POST", path = "/items", body = "[1,2]")
        assertEquals(400, response.status)
    }

    @Test
    fun `nullable JsonObject receives null on invalid JSON`() {
        Router.clearRegistry()
        JsonBodyController.reset()
        RouteTable.register("json-obj-nullable", postAction = SendJfkResponse) {
            add("/items", JsonBodyController::postNullableObject)
        }

        val response = routeRequest(method = "POST", path = "/items", body = "not json")
        assertEquals(200, response.status)
        assertEquals(true, JsonBodyController.nullableInvoked)
        assertEquals(null, JsonBodyController.lastJsonObject)
    }

    @Test
    fun `nullable JsonArray receives null on invalid JSON`() {
        Router.clearRegistry()
        JsonBodyController.reset()
        RouteTable.register("json-arr-nullable", postAction = SendJfkResponse) {
            add("/items", JsonBodyController::postNullableArray)
        }

        val response = routeRequest(method = "POST", path = "/items", body = "not json")
        assertEquals(200, response.status)
        assertEquals(true, JsonBodyController.nullableInvoked)
        assertEquals(null, JsonBodyController.lastJsonArray)
    }

    @Test
    fun `optional JsonObject uses default on invalid JSON`() {
        Router.clearRegistry()
        JsonBodyController.reset()
        RouteTable.register("json-obj-optional", postAction = SendJfkResponse) {
            add("/items", JsonBodyController::postOptionalObject)
        }

        val response = routeRequest(method = "POST", path = "/items", body = "not json")
        assertEquals(200, response.status)
        assertEquals(true, JsonBodyController.optionalInvoked)
        assertEquals(0, JsonBodyController.lastJsonObject?.entries?.size)
    }

    @Test
    fun `two required body content parameters throws at registration`() {
        Router.clearRegistry()
        assertFailsWith<RoutesConfigurationException> {
            RouteTable.register("json-two-required", postAction = SendJfkResponse) {
                add("/items", JsonBodyController::postTwoRequiredBodies)
            }
        }
    }

    @Test
    fun `required JsonObject and required HttpRequestContent throws at registration`() {
        Router.clearRegistry()
        assertFailsWith<RoutesConfigurationException> {
            RouteTable.register("json-and-content", postAction = SendJfkResponse) {
                add("/items", JsonBodyController::postRequiredJsonAndContent)
            }
        }
    }

    @Test
    fun `required body plus nullable body is allowed`() {
        Router.clearRegistry()
        RouteTable.register("json-required-nullable", postAction = SendJfkResponse) {
            add("/items", JsonBodyController::postRequiredAndNullable)
        }
    }

    @Test
    fun `required body plus optional body is allowed`() {
        Router.clearRegistry()
        RouteTable.register("json-required-optional", postAction = SendJfkResponse) {
            add("/items", JsonBodyController::postRequiredAndOptional)
        }
    }
}
