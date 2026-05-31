package org.tekfive.kviash.exchange.actions.static.adapters

import io.undertow.server.handlers.resource.PathResourceManager
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UndertowResourceProviderTest {

    @Test
    fun `loads resource from base directory`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "kviash-undertow-res-${System.nanoTime()}")
        try {
            File(tempDir, "static/css").mkdirs()
            File(tempDir, "static/css/app.css").writeText("body { color: red; }")

            val resourceManager = PathResourceManager(tempDir.toPath())
            val provider = UndertowResourceProvider(resourceManager)
            val bytes = provider.get("/static", "css/app.css")

            assertNotNull(bytes)
            assertEquals("body { color: red; }", String(bytes))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `returns null for missing resource`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "kviash-undertow-res-${System.nanoTime()}")
        try {
            tempDir.mkdirs()

            val resourceManager = PathResourceManager(tempDir.toPath())
            val provider = UndertowResourceProvider(resourceManager)

            assertNull(provider.get("/static", "nonexistent.css"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `returns null for directory path`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "kviash-undertow-res-${System.nanoTime()}")
        try {
            File(tempDir, "static/css").mkdirs()

            val resourceManager = PathResourceManager(tempDir.toPath())
            val provider = UndertowResourceProvider(resourceManager)

            assertNull(provider.get("/static", "css"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `combines url prefix and resource path`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "kviash-undertow-res-${System.nanoTime()}")
        try {
            File(tempDir, "assets/js").mkdirs()
            File(tempDir, "assets/js/app.js").writeText("console.log('hello')")

            val resourceManager = PathResourceManager(tempDir.toPath())
            val provider = UndertowResourceProvider(resourceManager)
            val bytes = provider.get("/assets", "js/app.js")

            assertNotNull(bytes)
            assertEquals("console.log('hello')", String(bytes))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `reads binary content correctly`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "kviash-undertow-res-${System.nanoTime()}")
        try {
            File(tempDir, "static").mkdirs()
            val binaryContent = byteArrayOf(0x00, 0x01, 0x02, 0xFF.toByte(), 0xFE.toByte())
            File(tempDir, "static/data.bin").writeBytes(binaryContent)

            val resourceManager = PathResourceManager(tempDir.toPath())
            val provider = UndertowResourceProvider(resourceManager)
            val bytes = provider.get("/static", "data.bin")

            assertNotNull(bytes)
            assertTrue(binaryContent.contentEquals(bytes))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
