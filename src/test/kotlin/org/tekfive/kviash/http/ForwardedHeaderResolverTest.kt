package org.tekfive.kviash.http

import org.tekfive.kviash.DefaultKviashConfiguration
import org.tekfive.kviash.StackableConfiguration
import org.tekfive.kviash.exchange.interceptors.MockRequestSource
import kotlin.test.Test
import kotlin.test.assertEquals

class ForwardedHeaderResolverTest {

    @Test
    fun `uses direct request values when peer is not trusted`() {
        val request = request(
            trustedProxyCidrs = listOf("10.0.0.10"),
            directClientIp = "198.51.100.20",
            directProtocol = "http",
            directPort = 8080,
            headers = listOf(
                "Host" to listOf("internal.local:8080"),
                "X-Forwarded-For" to listOf("203.0.113.7"),
                "X-Forwarded-Host" to listOf("clinic.example"),
                "X-Forwarded-Proto" to listOf("https"),
                "X-Forwarded-Port" to listOf("443"),
            ),
        )

        assertEquals("198.51.100.20", request.clientIp)
        assertEquals("internal.local:8080", request.host)
        assertEquals("http", request.urlProtocol)
        assertEquals(8080, request.port)
        assertEquals("http://internal.local:8080/test", request.url.toString())
    }

    @Test
    fun `uses x forwarded values when peer is trusted`() {
        val request = request(
            trustedProxyCidrs = listOf("10.0.0.0/8"),
            directClientIp = "10.0.0.10",
            directProtocol = "http",
            directPort = 8080,
            headers = listOf(
                "Host" to listOf("internal.local:8080"),
                "X-Forwarded-For" to listOf("203.0.113.7, 10.0.0.20"),
                "X-Forwarded-Host" to listOf("clinic.example"),
                "X-Forwarded-Proto" to listOf("https"),
                "X-Forwarded-Port" to listOf("443"),
            ),
        )

        assertEquals("203.0.113.7", request.clientIp)
        assertEquals("clinic.example", request.host)
        assertEquals("https", request.urlProtocol)
        assertEquals(443, request.port)
        assertEquals("https://clinic.example/test", request.url.toString())
    }

    @Test
    fun `uses rfc forwarded values before x forwarded values`() {
        val request = request(
            trustedProxyCidrs = listOf("10.0.0.10"),
            directClientIp = "10.0.0.10",
            directProtocol = "http",
            directPort = 8080,
            headers = listOf(
                "Host" to listOf("internal.local:8080"),
                "Forwarded" to listOf("for=\"[2001:db8::1]\";proto=https;host=clinic.example:8443"),
                "X-Forwarded-For" to listOf("203.0.113.7"),
                "X-Forwarded-Host" to listOf("other.example"),
                "X-Forwarded-Proto" to listOf("http"),
            ),
        )

        assertEquals("2001:db8:0:0:0:0:0:1", request.clientIp)
        assertEquals("clinic.example:8443", request.host)
        assertEquals("https", request.urlProtocol)
        assertEquals(8443, request.port)
        assertEquals("https://clinic.example:8443/test", request.url.toString())
    }

    private fun request(
        trustedProxyCidrs: List<String>,
        directClientIp: String,
        directProtocol: String,
        directPort: Int,
        headers: List<Pair<String, List<String>>>,
    ): HttpRequest {
        val configuration = StackableConfiguration(
            DefaultKviashConfiguration,
            trustedProxyCidrs = trustedProxyCidrs,
        )
        return HttpRequest(
            MockRequestSource(
                clientIp = directClientIp,
                urlProtocol = directProtocol,
                port = directPort,
                headers = headers,
            ),
            configuration,
        )
    }
}
