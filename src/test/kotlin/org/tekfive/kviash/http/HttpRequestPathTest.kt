package org.tekfive.kviash.http

import kotlin.test.Test
import kotlin.test.assertEquals

class HttpRequestPathTest {
    @Test
    fun testToPathSegments() {
        assertEquals(emptyList(), "/".toPathSegments(true))
        assertEquals(listOf("/"), "/".toPathSegments(false))
        assertEquals(listOf("one"), "/one".toPathSegments(true))
        assertEquals(listOf("/", "one"), "/one".toPathSegments(false))
        assertEquals(listOf("one"), "/one/".toPathSegments(true))
        assertEquals(listOf("/", "one", "/"), "/one/".toPathSegments(false))
    }

    @Test
    fun testToPath() {
        assertEquals("/one", listOf("one").toPath())
        assertEquals("/one/", listOf("/", "one", "/").toPath())
    }

}