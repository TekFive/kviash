package org.tekfive.kviash.exchange

import org.tekfive.kviash.http.HttpRequest
import org.tekfive.kviash.routing.ExpressionParameterCoercionException
import org.tekfive.kviash.routing.MockRequestSource
import org.tekfive.kviash.routing.MockResponseSource
import org.tekfive.kviash.routing.RouteTable
import org.tekfive.kviash.routing.Router
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class UuidParameterController {
    var receivedId: UUID? = null
    var receivedName: String? = null
    var receivedRequest: HttpRequest? = null
    var receivedRevision: Int? = null

    fun getItem(id: UUID) {
        receivedId = id
    }

    fun getRevision(name: String, request: HttpRequest, id: UUID, revision: Int) {
        receivedName = name
        receivedRequest = request
        receivedId = id
        receivedRevision = revision
    }
}

class UuidParameterInjectionTest {
    private val controller = UuidParameterController()
    private val canonicalId = "550e8400-e29b-41d4-a716-446655440000"
    private val invalidIds = listOf(
        "not-a-uuid",
        "1-1-1-1-1",
        "550e8400e29b41d4a716446655440000",
        "550e8400-e29b-41d4-a716-44665544000g",
        "550e8400-e29b-41d4-a716-4466554400000",
        "+50e8400-e29b-41d4-a716-446655440000",
    )

    @BeforeTest
    fun setUp() = Router.clearRegistry()

    @AfterTest
    fun tearDown() = Router.clearRegistry()

    @Test
    fun `inferred UUID parameter receives parsed UUID values`() {
        RouteTable.register {
            add("/items/{}", controller::getItem)
        }

        val validIds = listOf(
            canonicalId,
            canonicalId.uppercase(),
            "00000000-0000-0000-0000-000000000000",
            "ffffffff-ffff-ffff-ffff-ffffffffffff",
        )
        for (id in validIds) {
            controller.receivedId = null
            val response = MockResponseSource()
            Router.route(MockRequestSource(path = "/items/$id"), response)

            assertEquals(200, response.status, id)
            assertEquals(UUID.fromString(id), controller.receivedId, id)
        }
    }

    @Test
    fun `inferred UUID route rejects malformed segments`() {
        RouteTable.register {
            add("/items/{}", controller::getItem)
        }

        for (id in invalidIds) {
            val response = MockResponseSource()
            Router.route(MockRequestSource(path = "/items/$id"), response)

            assertEquals(404, response.status, id)
            assertNull(controller.receivedId, id)
        }
    }

    @Test
    fun `UUID parameter works alongside other path and injected parameters`() {
        RouteTable.register {
            add("/items/{}/{}/{}/view", controller::getRevision)
        }
        val request = MockRequestSource(path = "/items/report/$canonicalId/42/view")
        val response = MockResponseSource()
        Router.route(request, response)

        assertEquals(200, response.status)
        assertEquals("report", controller.receivedName)
        assertEquals(UUID.fromString(canonicalId), controller.receivedId)
        assertEquals(42, controller.receivedRevision)
        assertEquals(request.path, controller.receivedRequest?.path)
    }

    @Test
    fun `explicit pattern converts valid UUID segments`() {
        RouteTable.register {
            add("/items/{.*}", controller::getItem)
        }
        val response = MockResponseSource()
        Router.route(MockRequestSource(path = "/items/$canonicalId"), response)

        assertEquals(200, response.status)
        assertEquals(UUID.fromString(canonicalId), controller.receivedId)
    }

    @Test
    fun `explicit pattern reports standard coercion error for invalid UUIDs`() {
        RouteTable.register {
            add("/items/{.*}", controller::getItem)
        }

        for (id in invalidIds) {
            val request = MockRequestSource(path = "/items/$id")
            Router.route(request, MockResponseSource())

            val exchange = assertIs<Exchange>(request.getAttribute("exchange"))
            val error = assertIs<ExpressionParameterCoercionException>(exchange.exceptions.single())
            assertEquals(id, error.pathSegment)
            assertNull(controller.receivedId, id)
        }
    }
}
