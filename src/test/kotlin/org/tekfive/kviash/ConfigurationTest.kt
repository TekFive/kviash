package org.tekfive.kviash

import kotlin.test.Test
import org.tekfive.kviash.exchange.DefaultExchangeErrorLogger
import org.tekfive.kviash.exchange.ExchangeErrorLogger
import org.tekfive.kviash.http.HttpRequest
import java.time.Instant
import kotlin.test.assertEquals

class ConfigurationTest {
    @Test
    fun testStackableConfiguration() {
        val baseConfiguration = object : KviashConfiguration {
            override val inputBufferSize: Int = 0

            override val outputBufferSize: Int = 0

            override val trimParameterValues: Boolean = false

            override val ignoreRoutePathCase: Boolean = false

            override val trustedProxyCidrs: List<String> = emptyList()

            override val exchangeErrorLogger: ExchangeErrorLogger = DefaultExchangeErrorLogger

            override fun instant(): Instant {
                return Instant.ofEpochMilli(0)
            }

            override fun getRootUrl(request: HttpRequest): String {
                return ""
            }
        }

        var stackableConfiguration = StackableConfiguration(ConfigurationOverride(
            inputBufferSize = 1
        ), baseConfiguration)
        assertEquals(1, stackableConfiguration.inputBufferSize)
        assertEquals(0, stackableConfiguration.outputBufferSize)
    }
}
