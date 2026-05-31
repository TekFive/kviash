package org.tekfive.kviash.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpRequestPathExtendedTest {

    // -----------------------------------------------------------------------
    // toPathSegments
    // -----------------------------------------------------------------------

    @Test
    fun `toPathSegments handles multi-segment path with ignoreTrailingSlash`() {
        assertEquals(listOf("a", "b", "c"), "/a/b/c".toPathSegments(true))
    }

    @Test
    fun `toPathSegments handles multi-segment path preserving slashes`() {
        assertEquals(listOf("/", "a", "/", "b", "/", "c"), "/a/b/c".toPathSegments(false))
    }

    @Test
    fun `toPathSegments handles empty string`() {
        assertEquals(emptyList(), "".toPathSegments(true))
        assertEquals(emptyList(), "".toPathSegments(false))
    }

    @Test
    fun `toPathSegments handles double slashes with ignoreTrailingSlash`() {
        // Double slashes produce empty strings which are filtered
        assertEquals(listOf("a", "b"), "/a//b".toPathSegments(true))
    }

    // -----------------------------------------------------------------------
    // toPath
    // -----------------------------------------------------------------------

    @Test
    fun `toPath with segments containing slashes joins without separator`() {
        assertEquals("/one/two", listOf("/", "one", "/", "two").toPath())
    }

    @Test
    fun `toPath with no slashes prepends slash separator`() {
        assertEquals("/a/b/c", listOf("a", "b", "c").toPath())
    }

    @Test
    fun `toPath with empty list`() {
        assertEquals("/", emptyList<String>().toPath())
    }

    // -----------------------------------------------------------------------
    // HttpRequestPath properties
    // -----------------------------------------------------------------------

    @Test
    fun `length returns number of segments`() {
        val path = HttpRequestPath(listOf("a", "b", "c"))
        assertEquals(3, path.length)
    }

    @Test
    fun `empty returns true for empty segments`() {
        val path = HttpRequestPath(emptyList())
        assertTrue(path.empty)
    }

    @Test
    fun `empty returns false for non-empty segments`() {
        val path = HttpRequestPath(listOf("one"))
        assertFalse(path.empty)
    }

    // -----------------------------------------------------------------------
    // fileName and fileExtension
    // -----------------------------------------------------------------------

    @Test
    fun `fileName returns last segment when it contains a dot`() {
        val path = HttpRequestPath(listOf("assets", "style.css"))
        assertEquals("style.css", path.fileName)
    }

    @Test
    fun `fileName returns null when last segment has no dot`() {
        val path = HttpRequestPath(listOf("api", "users"))
        assertNull(path.fileName)
    }

    @Test
    fun `fileName returns null for empty path`() {
        val path = HttpRequestPath(emptyList())
        assertNull(path.fileName)
    }

    @Test
    fun `fileExtension returns extension after last dot`() {
        val path = HttpRequestPath(listOf("dir", "archive.tar.gz"))
        assertEquals("gz", path.fileExtension)
    }

    @Test
    fun `fileExtension returns null when no fileName`() {
        val path = HttpRequestPath(listOf("api", "users"))
        assertNull(path.fileExtension)
    }

    // -----------------------------------------------------------------------
    // Indexed access
    // -----------------------------------------------------------------------

    @Test
    fun `get returns segment at index`() {
        val path = HttpRequestPath(listOf("a", "b", "c"))
        assertEquals("a", path[0])
        assertEquals("c", path[2])
    }

    @Test
    fun `get throws IndexOutOfBoundsException for invalid index`() {
        val path = HttpRequestPath(listOf("a"))
        assertFailsWith<IndexOutOfBoundsException> {
            path[5]
        }
    }

    @Test
    fun `getOrNull returns null for out of bounds`() {
        val path = HttpRequestPath(listOf("a"))
        assertNull(path.getOrNull(5))
        assertEquals("a", path.getOrNull(0))
    }

    // -----------------------------------------------------------------------
    // Typed getters
    // -----------------------------------------------------------------------

    @Test
    fun `getInt returns parsed integer`() {
        val path = HttpRequestPath(listOf("users", "42"))
        assertEquals(42, path.getInt(1))
    }

    @Test
    fun `getInt returns null for non-numeric segment`() {
        val path = HttpRequestPath(listOf("users", "abc"))
        assertNull(path.getInt(1))
    }

    @Test
    fun `getInt returns null for out of bounds index`() {
        val path = HttpRequestPath(listOf("users"))
        assertNull(path.getInt(5))
    }

    @Test
    fun `getLong returns parsed long`() {
        val path = HttpRequestPath(listOf("id", "9999999999999"))
        assertEquals(9999999999999L, path.getLong(1))
    }

    @Test
    fun `getFloat returns parsed float`() {
        val path = HttpRequestPath(listOf("value", "3.14"))
        assertEquals(3.14f, path.getFloat(1))
    }

    @Test
    fun `getDouble returns parsed double`() {
        val path = HttpRequestPath(listOf("value", "2.718281828"))
        assertEquals(2.718281828, path.getDouble(1))
    }

    @Test
    fun `getBoolean returns parsed boolean`() {
        val path = HttpRequestPath(listOf("flag", "true"))
        assertEquals(true, path.getBoolean(1))
    }

    @Test
    fun `getBoolean with default returns default when out of bounds`() {
        val path = HttpRequestPath(listOf("one"))
        assertEquals(false, path.getBoolean(5, false))
    }

    @Test
    fun `getByte returns parsed byte`() {
        val path = HttpRequestPath(listOf("b", "127"))
        assertEquals(127.toByte(), path.getByte(1))
    }

    @Test
    fun `getByte with default returns default when segment is not a byte`() {
        val path = HttpRequestPath(listOf("b", "notabyte"))
        assertEquals(0.toByte(), path.getByte(1, 0))
    }

    @Test
    fun `getShort returns parsed short`() {
        val path = HttpRequestPath(listOf("s", "12345"))
        assertEquals(12345.toShort(), path.getShort(1))
    }

    @Test
    fun `getShort with default returns default when out of bounds`() {
        val path = HttpRequestPath(listOf("s"))
        assertEquals(99.toShort(), path.getShort(5, 99))
    }

    // -----------------------------------------------------------------------
    // startsWith
    // -----------------------------------------------------------------------

    @Test
    fun `startsWith string checks path prefix`() {
        val path = HttpRequestPath(listOf("api", "v1", "users"))
        assertTrue(path.startsWith("/api"))
        assertTrue(path.startsWith("/api/v1"))
        assertFalse(path.startsWith("/web"))
    }

    @Test
    fun `startsWith segments checks segment prefix`() {
        val path = HttpRequestPath(listOf("api", "v1", "users"))
        assertTrue(path.startsWith(listOf("api", "v1")))
        assertFalse(path.startsWith(listOf("api", "v2")))
        assertFalse(path.startsWith(listOf("api", "v1", "users", "extra")))
    }

    @Test
    fun `startsWith empty segments returns true`() {
        val path = HttpRequestPath(listOf("api"))
        assertTrue(path.startsWith(emptyList()))
    }

    // -----------------------------------------------------------------------
    // pop
    // -----------------------------------------------------------------------

    @Test
    fun `pop returns head and tail`() {
        val path = HttpRequestPath(listOf("a", "b", "c"))
        val (head, tail) = path.pop()
        assertEquals("a", head)
        assertEquals(HttpRequestPath(listOf("b", "c")), tail)
    }

    @Test
    fun `pop with count splits segments`() {
        val path = HttpRequestPath(listOf("a", "b", "c", "d"))
        val (head, tail) = path.pop(2)
        assertEquals(HttpRequestPath(listOf("a", "b")), head)
        assertEquals(HttpRequestPath(listOf("c", "d")), tail)
    }

    @Test
    fun `pop on single segment path`() {
        val path = HttpRequestPath(listOf("only"))
        val (head, tail) = path.pop()
        assertEquals("only", head)
        assertTrue(tail.empty)
    }

    // -----------------------------------------------------------------------
    // subSegments
    // -----------------------------------------------------------------------

    @Test
    fun `subSegments returns range of segments`() {
        val path = HttpRequestPath(listOf("a", "b", "c", "d"))
        val sub = path.subSegments(1, 3)
        assertEquals(HttpRequestPath(listOf("b", "c")), sub)
    }

    // -----------------------------------------------------------------------
    // equals
    // -----------------------------------------------------------------------

    @Test
    fun `equals with string path`() {
        val path = HttpRequestPath(listOf("api", "users"))
        assertTrue(path.equals("/api/users"))
        assertFalse(path.equals("/api/items"))
    }

    @Test
    fun `equals with segments list`() {
        val path = HttpRequestPath(listOf("a", "b"))
        assertTrue(path.equals(listOf("a", "b")))
        assertFalse(path.equals(listOf("a", "c")))
    }

    @Test
    fun `equals with another HttpRequestPath`() {
        val path1 = HttpRequestPath(listOf("a", "b"))
        val path2 = HttpRequestPath(listOf("a", "b"))
        val path3 = HttpRequestPath(listOf("a", "c"))
        assertEquals(path1, path2)
        assertFalse(path1.equals(path3))
    }

    @Test
    fun `hashCode is consistent with equals`() {
        val path1 = HttpRequestPath(listOf("a", "b"))
        val path2 = HttpRequestPath(listOf("a", "b"))
        assertEquals(path1.hashCode(), path2.hashCode())
    }

    @Test
    fun `toString returns path string`() {
        val path = HttpRequestPath(listOf("api", "users"))
        assertEquals("/api/users", path.toString())
    }
}
