package org.tekfive.kviash

import org.tekfive.ack.configuration.AckRegistry
import org.tekfive.ack.sources.MapSource
import org.tekfive.kviash.exchange.ExchangeErrorLogger
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigurationExtendedTest {

    // -----------------------------------------------------------------------
    // DefaultKviashConfiguration
    // -----------------------------------------------------------------------

    @Test
    fun `default configuration has expected buffer sizes`() {
        AckRegistry.clear()
        assertEquals(8192, BasicKviashConfiguration().inputBufferSize)
        assertEquals(8192, BasicKviashConfiguration().outputBufferSize)
    }

    @Test
    fun `default configuration trims parameter values`() {
        AckRegistry.clear()
        assertTrue(BasicKviashConfiguration().trimParameterValues)
    }

    @Test
    fun `default configuration ignores route path case`() {
        AckRegistry.clear()
        assertTrue(BasicKviashConfiguration().ignoreRoutePathCase)
    }

    @Test
    fun `default configuration instant returns current time`() {
        val before = Instant.now()
        val instant = DefaultKviashConfiguration.instant()
        val after = Instant.now()
        assertTrue(!instant.isBefore(before))
        assertTrue(!instant.isAfter(after))
    }

    // -----------------------------------------------------------------------
    // StackableConfiguration via ConfigurationOverride
    // -----------------------------------------------------------------------

    @Test
    fun `override inputBufferSize only`() {
        val config = StackableConfiguration(
            ConfigurationOverride(inputBufferSize = 1024),
            DefaultKviashConfiguration
        )
        assertEquals(1024, config.inputBufferSize)
        assertEquals(DefaultKviashConfiguration.outputBufferSize, config.outputBufferSize)
        assertEquals(DefaultKviashConfiguration.trimParameterValues, config.trimParameterValues)
        assertEquals(DefaultKviashConfiguration.ignoreRoutePathCase, config.ignoreRoutePathCase)
    }

    @Test
    fun `override outputBufferSize only`() {
        val config = StackableConfiguration(
            ConfigurationOverride(outputBufferSize = 4096),
            DefaultKviashConfiguration
        )
        assertEquals(4096, config.outputBufferSize)
        assertEquals(DefaultKviashConfiguration.inputBufferSize, config.inputBufferSize)
    }

    @Test
    fun `override trimParameterValues`() {
        val config = StackableConfiguration(
            ConfigurationOverride(trimParameterValues = false),
            DefaultKviashConfiguration
        )
        assertEquals(false, config.trimParameterValues)
    }

    @Test
    fun `override ignoreRoutePathCase`() {
        val config = StackableConfiguration(
            ConfigurationOverride(ignoreRoutePathCase = false),
            DefaultKviashConfiguration
        )
        assertEquals(false, config.ignoreRoutePathCase)
    }

    @Test
    fun `override instant handler`() {
        val fixedInstant = Instant.parse("2025-06-15T10:00:00Z")
        val config = StackableConfiguration(
            ConfigurationOverride(instant = { fixedInstant }),
            DefaultKviashConfiguration
        )
        assertEquals(fixedInstant, config.instant())
    }

    @Test
    fun `override exchangeErrorLogger`() {
        val customLogger = object : ExchangeErrorLogger {
            override fun warn(message: String, exchange: org.tekfive.kviash.exchange.Exchange) {}
            override fun error(message: String, exchange: org.tekfive.kviash.exchange.Exchange) {}
            override fun error(message: String, exchange: org.tekfive.kviash.exchange.Exchange, exception: Exception) {}
        }
        val config = StackableConfiguration(
            ConfigurationOverride(exchangeErrorLogger = customLogger),
            DefaultKviashConfiguration
        )
        assertEquals(customLogger, config.exchangeErrorLogger)
    }

    @Test
    fun `null override falls back to base configuration`() {
        val config = StackableConfiguration(
            ConfigurationOverride(), // all nulls
            DefaultKviashConfiguration
        )
        assertEquals(DefaultKviashConfiguration.inputBufferSize, config.inputBufferSize)
        assertEquals(DefaultKviashConfiguration.outputBufferSize, config.outputBufferSize)
        assertEquals(DefaultKviashConfiguration.trimParameterValues, config.trimParameterValues)
        assertEquals(DefaultKviashConfiguration.ignoreRoutePathCase, config.ignoreRoutePathCase)
    }

    @Test
    fun `multiple overrides stack correctly`() {
        val first = StackableConfiguration(
            ConfigurationOverride(inputBufferSize = 1024, outputBufferSize = 2048),
            DefaultKviashConfiguration
        )
        val second = StackableConfiguration(
            ConfigurationOverride(inputBufferSize = 512),
            first
        )
        assertEquals(512, second.inputBufferSize)
        assertEquals(2048, second.outputBufferSize)
        assertEquals(DefaultKviashConfiguration.trimParameterValues, second.trimParameterValues)
    }

    @Test
    fun `secondary constructor from base configuration`() {
        val config = StackableConfiguration(
            DefaultKviashConfiguration,
            inputBufferSize = 999,
            outputBufferSize = 888,
        )
        assertEquals(999, config.inputBufferSize)
        assertEquals(888, config.outputBufferSize)
        assertEquals(DefaultKviashConfiguration.trimParameterValues, config.trimParameterValues)
    }

    // -----------------------------------------------------------------------
    // ACK Property Integration
    // -----------------------------------------------------------------------

    @Test
    fun `ACK properties override default buffer sizes`() {
        AckRegistry.clear()
        AckRegistry.addSource(MapSource(mapOf(
            "KVSH_INPUT_BUFFER_SIZE" to "16384",
            "KVSH_OUTPUT_BUFFER_SIZE" to "4096",
        )))
        val config = BasicKviashConfiguration()
        assertEquals(16384, config.inputBufferSize)
        assertEquals(4096, config.outputBufferSize)
        AckRegistry.clear()
    }

    @Test
    fun `ACK properties override trim and case settings`() {
        AckRegistry.clear()
        AckRegistry.addSource(MapSource(mapOf(
            "KVSH_TRIM_PARAMETER_VALUES" to "false",
            "KVSH_IGNORE_ROUTE_PATH_CASE" to "false",
        )))
        val config = BasicKviashConfiguration()
        assertEquals(false, config.trimParameterValues)
        assertEquals(false, config.ignoreRoutePathCase)
        AckRegistry.clear()
    }

    @Test
    fun `ACK properties use defaults when no source configured`() {
        AckRegistry.clear()
        val config = BasicKviashConfiguration()
        assertEquals(8192, config.inputBufferSize)
        assertEquals(8192, config.outputBufferSize)
        assertEquals(true, config.trimParameterValues)
        assertEquals(true, config.ignoreRoutePathCase)
    }

    @Test
    fun `ACK properties parse trusted proxy cidrs`() {
        AckRegistry.clear()
        AckRegistry.addSource(MapSource(mapOf(
            "KVSH_TRUSTED_PROXY_CIDRS" to "10.0.0.0/8, 192.168.1.10",
        )))
        val config = BasicKviashConfiguration()
        assertEquals(listOf("10.0.0.0/8", "192.168.1.10"), config.trustedProxyCidrs)
        AckRegistry.clear()
    }
}
