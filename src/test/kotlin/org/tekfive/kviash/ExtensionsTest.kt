package org.tekfive.kviash

import kotlin.test.Test
import kotlin.test.assertEquals

class EscapeHtmlTest {

    @Test
    fun `escapes ampersand`() {
        assertEquals("foo &amp; bar", "foo & bar".escapeHtml)
    }

    @Test
    fun `escapes less than`() {
        assertEquals("&lt;div&gt;", "<div>".escapeHtml)
    }

    @Test
    fun `escapes double quotes`() {
        assertEquals("a=&quot;b&quot;", "a=\"b\"".escapeHtml)
    }

    @Test
    fun `escapes single quotes`() {
        assertEquals("it&#x27;s", "it's".escapeHtml)
    }

    @Test
    fun `escapes all special characters together`() {
        assertEquals(
            "&lt;script&gt;alert(&#x27;x&amp;y&quot;z&#x27;)&lt;/script&gt;",
            "<script>alert('x&y\"z')</script>".escapeHtml
        )
    }

    @Test
    fun `returns empty string unchanged`() {
        assertEquals("", "".escapeHtml)
    }

    @Test
    fun `returns plain text unchanged`() {
        assertEquals("Hello World 123", "Hello World 123".escapeHtml)
    }
}
