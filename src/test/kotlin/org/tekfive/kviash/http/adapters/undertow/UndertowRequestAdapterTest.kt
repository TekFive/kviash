package org.tekfive.kviash.http.adapters.undertow

import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.form.FormData
import io.undertow.server.handlers.form.FormDataParser
import java.util.ArrayDeque
import kotlin.test.Test
import kotlin.test.assertEquals

class UndertowRequestAdapterTest {

    @Test
    fun `parameters merge query and parsed form values`() {
        val exchange = HttpServerExchange(null)
        exchange.queryParameters["query"] = ArrayDeque(listOf("one", "two"))
        exchange.queryParameters["shared"] = ArrayDeque(listOf("query-value"))
        val formData = FormData(10).apply {
            add("form", "alpha")
            add("form", "beta")
            add("shared", "form-value")
        }
        exchange.putAttachment(FormDataParser.FORM_DATA, formData)

        val parameters = UndertowRequestAdapter(exchange).parameters.toMap()

        assertEquals(listOf("one", "two"), parameters["query"])
        assertEquals(listOf("alpha", "beta"), parameters["form"])
        assertEquals(listOf("query-value", "form-value"), parameters["shared"])
    }
}
