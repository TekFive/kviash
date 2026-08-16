package org.tekfive.kviash.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// RouteSegment parsing
// ---------------------------------------------------------------------------

class RouteSegmentTest {

    @Test
    fun `literal segment is parsed correctly`() {
        val segment = RouteSegment(0, "users")
        assertEquals(RouteSegmentType.LITERAL, segment.type)
        assertEquals("users", segment.value)
        assertEquals(0, segment.index)
    }

    @Test
    fun `inferred expression is parsed from empty braces`() {
        val segment = RouteSegment(1, "{}")
        assertEquals(RouteSegmentType.INFERRED_EXPRESSION, segment.type)
        assertNull(segment.value)
    }

    @Test
    fun `inferred expression is parsed from braces with only whitespace`() {
        val segment = RouteSegment(0, "{  }")
        assertEquals(RouteSegmentType.INFERRED_EXPRESSION, segment.type)
    }

    @Test
    fun `wildcard expression is parsed from single asterisk in braces`() {
        val segment = RouteSegment(0, "{*}")
        assertEquals(RouteSegmentType.WILDCARD_EXPRESSION, segment.type)
        assertNull(segment.value)
    }

    @Test
    fun `gobbler expression is parsed from double asterisk in braces`() {
        val segment = RouteSegment(0, "{**}")
        assertEquals(RouteSegmentType.PATH_GOBBLER, segment.type)
        assertNull(segment.value)
    }

    @Test
    fun `explicit expression is parsed from regex in braces`() {
        val segment = RouteSegment(0, "{\\d+}")
        assertEquals(RouteSegmentType.EXPLICIT_EXPRESSION, segment.type)
        assertEquals("\\d+", segment.value)
    }

    @Test
    fun `explicit expression with named pattern`() {
        val segment = RouteSegment(0, "{[a-z]+}")
        assertEquals(RouteSegmentType.EXPLICIT_EXPRESSION, segment.type)
        assertEquals("[a-z]+", segment.value)
    }

    @Test
    fun `leading and trailing whitespace is trimmed`() {
        val segment = RouteSegment(0, "  users  ")
        assertEquals(RouteSegmentType.LITERAL, segment.type)
        assertEquals("users", segment.value)
    }

    // -----------------------------------------------------------------------
    // toString
    // -----------------------------------------------------------------------

    @Test
    fun `toString for literal`() {
        val segment = RouteSegment(0, RouteSegmentType.LITERAL, "users")
        assertEquals("users", segment.toString())
    }

    @Test
    fun `toString for inferred expression`() {
        val segment = RouteSegment(0, RouteSegmentType.INFERRED_EXPRESSION, null)
        assertEquals("{}", segment.toString())
    }

    @Test
    fun `toString for wildcard`() {
        val segment = RouteSegment(0, RouteSegmentType.WILDCARD_EXPRESSION, null)
        assertEquals("{*}", segment.toString())
    }

    @Test
    fun `toString for gobbler`() {
        val segment = RouteSegment(0, RouteSegmentType.PATH_GOBBLER, null)
        assertEquals("{**}", segment.toString())
    }

    @Test
    fun `toString for explicit expression`() {
        val segment = RouteSegment(0, RouteSegmentType.EXPLICIT_EXPRESSION, "\\d+")
        assertEquals("{\\d+}", segment.toString())
    }

    // -----------------------------------------------------------------------
    // equals and hashCode
    // -----------------------------------------------------------------------

    @Test
    fun `equal segments have same hashCode`() {
        val s1 = RouteSegment(0, "users")
        val s2 = RouteSegment(0, "users")
        assertEquals(s1, s2)
        assertEquals(s1.hashCode(), s2.hashCode())
    }

    @Test
    fun `segments with different index are not equal`() {
        val s1 = RouteSegment(0, "users")
        val s2 = RouteSegment(1, "users")
        assertFalse(s1.equals(s2))
    }

    @Test
    fun `segments with different type are not equal`() {
        val s1 = RouteSegment(0, RouteSegmentType.LITERAL, "test")
        val s2 = RouteSegment(0, RouteSegmentType.EXPLICIT_EXPRESSION, "test")
        assertFalse(s1.equals(s2))
    }
}

// ---------------------------------------------------------------------------
// RouteSegmentMatcher
// ---------------------------------------------------------------------------

class RouteSegmentMatcherTest {

    // -- LiteralRouteSegmentMatcher --

    @Test
    fun `literal matcher matches exact string`() {
        val matcher = LiteralRouteSegmentMatcher("users", false)
        assertTrue(matcher.matches("users"))
        assertFalse(matcher.matches("Users"))
        assertFalse(matcher.matches("items"))
    }

    @Test
    fun `literal matcher matches case insensitive`() {
        val matcher = LiteralRouteSegmentMatcher("Users", true)
        assertTrue(matcher.matches("users"))
        assertTrue(matcher.matches("USERS"))
        assertTrue(matcher.matches("Users"))
    }

    @Test
    fun `literal matcher is not terminal`() {
        val matcher = LiteralRouteSegmentMatcher("test", false)
        assertFalse(matcher.terminal)
    }

    @Test
    fun `literal matcher label is the literal segment`() {
        val matcher = LiteralRouteSegmentMatcher("api", false)
        assertEquals("api", matcher.label)
    }

    @Test
    fun `literal matcher equals checks ignoreCase and literalSegment`() {
        val m1 = LiteralRouteSegmentMatcher("api", true)
        val m2 = LiteralRouteSegmentMatcher("api", true)
        val m3 = LiteralRouteSegmentMatcher("api", false)
        assertEquals(m1, m2)
        assertFalse(m1.equals(m3))
    }

    @Test
    fun `case-insensitive literal matchers have consistent equality and hash codes`() {
        val lower = LiteralRouteSegmentMatcher("users", true)
        val upper = LiteralRouteSegmentMatcher("USERS", true)

        assertEquals(lower, upper)
        assertEquals(lower.hashCode(), upper.hashCode())
        assertEquals(0, lower.compareTo(upper))
    }

    @Test
    fun `string path parameters preserve plus characters`() {
        assertEquals("a+b", PrimitiveParameterType.StringType.segmentToValue("a+b"))
        assertEquals("a+b", PrimitiveParameterType.StringType.segmentToValue("a%2Bb"))
    }

    @Test
    fun `literal matcher compareTo other literal`() {
        val a = LiteralRouteSegmentMatcher("a", false)
        val b = LiteralRouteSegmentMatcher("b", false)
        assertTrue(a.compareTo(b) < 0)
        assertTrue(b.compareTo(a) > 0)
    }

    @Test
    fun `literal matcher compares before pattern matcher`() {
        val literal = LiteralRouteSegmentMatcher("api", false)
        val pattern = PatternRouteSegmentMatcher("\\d+", false)
        assertTrue(literal.compareTo(pattern) < 0)
    }

    // -- PatternRouteSegmentMatcher --

    @Test
    fun `pattern matcher matches regex`() {
        val matcher = PatternRouteSegmentMatcher("\\d+", false)
        assertTrue(matcher.matches("123"))
        assertFalse(matcher.matches("abc"))
    }

    @Test
    fun `pattern matcher with ignoreCase`() {
        val matcher = PatternRouteSegmentMatcher("[a-z]+", true)
        assertTrue(matcher.matches("ABC"))
        assertTrue(matcher.matches("abc"))
    }

    @Test
    fun `pattern matcher is not terminal`() {
        val matcher = PatternRouteSegmentMatcher(".*", false)
        assertFalse(matcher.terminal)
    }

    @Test
    fun `pattern matcher compares after literal and before gobbler`() {
        val pattern = PatternRouteSegmentMatcher("\\d+", false)
        val literal = LiteralRouteSegmentMatcher("test", false)
        val gobbler = GobblerSegmentMatcher

        assertTrue(pattern.compareTo(literal) > 0)
        assertTrue(pattern.compareTo(gobbler) < 0)
    }

    @Test
    fun `pattern matcher with same pattern has same toString`() {
        val p1 = PatternRouteSegmentMatcher("\\d+", false)
        val p2 = PatternRouteSegmentMatcher("\\d+", false)
        assertEquals(p1.toString(), p2.toString())
        assertEquals(p1.regex.pattern, p2.regex.pattern)
    }

    // -- GobblerSegmentMatcher --

    @Test
    fun `gobbler matcher matches any string`() {
        assertTrue(GobblerSegmentMatcher.matches("anything"))
        assertTrue(GobblerSegmentMatcher.matches(""))
        assertTrue(GobblerSegmentMatcher.matches("deep/nested/path"))
    }

    @Test
    fun `gobbler matcher is terminal`() {
        assertTrue(GobblerSegmentMatcher.terminal)
    }

    @Test
    fun `gobbler matcher label`() {
        assertEquals("{**}", GobblerSegmentMatcher.label)
    }

    @Test
    fun `gobbler compares as greater than literal and pattern`() {
        val literal = LiteralRouteSegmentMatcher("test", false)
        val pattern = PatternRouteSegmentMatcher("\\d+", false)

        assertTrue(GobblerSegmentMatcher.compareTo(literal) > 0)
        assertTrue(GobblerSegmentMatcher.compareTo(pattern) > 0)
    }

    @Test
    fun `gobbler compares as equal to another gobbler`() {
        assertEquals(0, GobblerSegmentMatcher.compareTo(GobblerSegmentMatcher))
    }

    // -- RootTreeSegmentMatcher --

    @Test
    fun `root matcher matches anything`() {
        assertTrue(RootTreeSegmentMatcher.matches("anything"))
        assertTrue(RootTreeSegmentMatcher.matches(""))
    }

    @Test
    fun `root matcher is not terminal`() {
        assertFalse(RootTreeSegmentMatcher.terminal)
    }
}

// ---------------------------------------------------------------------------
// RoutePath
// ---------------------------------------------------------------------------

class RoutePathTest {

    @Test
    fun `RoutePath from simple path segments`() {
        val path = RoutePath(listOf("users", "profile"))
        assertEquals("/users/profile", path.path)
        assertEquals(2, path.segments.size)
        assertEquals(RouteSegmentType.LITERAL, path.segments[0].type)
    }

    @Test
    fun `RoutePath with inferred expression`() {
        val path = RoutePath(listOf("users", "{}"))
        assertEquals(2, path.segments.size)
        assertEquals(RouteSegmentType.INFERRED_EXPRESSION, path.segments[1].type)
    }

    @Test
    fun `RoutePath validates explicit expression regex`() {
        assertFailsWith<IllegalArgumentException> {
            // Construct RoutePath directly with an invalid regex in an EXPLICIT_EXPRESSION segment
            RoutePath(
                "/users/{[invalid}",
                listOf(
                    RouteSegment(0, RouteSegmentType.LITERAL, "users"),
                    RouteSegment(1, RouteSegmentType.EXPLICIT_EXPRESSION, "[invalid"),
                )
            )
        }
    }

    @Test
    fun `RoutePath rejects gobbler not at end`() {
        assertFailsWith<IllegalArgumentException> {
            RoutePath(
                "/invalid",
                listOf(
                    RouteSegment(0, RouteSegmentType.PATH_GOBBLER, null),
                    RouteSegment(1, RouteSegmentType.LITERAL, "more"),
                )
            )
        }
    }

    @Test
    fun `RoutePath allows gobbler at end`() {
        val path = RoutePath(
            "/assets/{**}",
            listOf(
                RouteSegment(0, RouteSegmentType.LITERAL, "assets"),
                RouteSegment(1, RouteSegmentType.PATH_GOBBLER, null),
            )
        )
        assertEquals(2, path.segments.size)
    }

    @Test
    fun `RoutePath toString returns path`() {
        val path = RoutePath(listOf("api", "v1"))
        assertEquals("/api/v1", path.toString())
    }
}

// ---------------------------------------------------------------------------
// PrimitiveParameterType
// ---------------------------------------------------------------------------

class PrimitiveParameterTypeTest {

    @Test
    fun `fromClass returns correct type for String`() {
        assertEquals(PrimitiveParameterType.StringType, PrimitiveParameterType.fromClass(String::class))
    }

    @Test
    fun `fromClass returns correct type for Int`() {
        assertEquals(PrimitiveParameterType.IntType, PrimitiveParameterType.fromClass(Int::class))
    }

    @Test
    fun `fromClass returns correct type for Long`() {
        assertEquals(PrimitiveParameterType.LongType, PrimitiveParameterType.fromClass(Long::class))
    }

    @Test
    fun `fromClass returns correct type for Boolean`() {
        assertEquals(PrimitiveParameterType.BooleanType, PrimitiveParameterType.fromClass(Boolean::class))
    }

    @Test
    fun `fromClass returns correct type for Char`() {
        assertEquals(PrimitiveParameterType.CharType, PrimitiveParameterType.fromClass(Char::class))
    }

    @Test
    fun `fromClass returns correct type for Byte`() {
        assertEquals(PrimitiveParameterType.ByteType, PrimitiveParameterType.fromClass(Byte::class))
    }

    @Test
    fun `fromClass returns correct type for Short`() {
        assertEquals(PrimitiveParameterType.ShortType, PrimitiveParameterType.fromClass(Short::class))
    }

    @Test
    fun `fromClass returns correct type for Float`() {
        assertEquals(PrimitiveParameterType.FloatType, PrimitiveParameterType.fromClass(Float::class))
    }

    @Test
    fun `fromClass returns correct type for Double`() {
        assertEquals(PrimitiveParameterType.DoubleType, PrimitiveParameterType.fromClass(Double::class))
    }

    @Test
    fun `fromClass returns null for unsupported type`() {
        assertNull(PrimitiveParameterType.fromClass(List::class))
    }

    // -----------------------------------------------------------------------
    // Regex matching
    // -----------------------------------------------------------------------

    @Test
    fun `IntType regex matches valid integers`() {
        assertTrue(PrimitiveParameterType.IntType.regex.matches("42"))
        assertTrue(PrimitiveParameterType.IntType.regex.matches("-1"))
        assertTrue(PrimitiveParameterType.IntType.regex.matches("0"))
    }

    @Test
    fun `IntType regex rejects non-numeric`() {
        assertFalse(PrimitiveParameterType.IntType.regex.matches("abc"))
        assertFalse(PrimitiveParameterType.IntType.regex.matches(""))
    }

    @Test
    fun `BooleanType regex matches true and false`() {
        assertTrue(PrimitiveParameterType.BooleanType.regex.matches("true"))
        assertTrue(PrimitiveParameterType.BooleanType.regex.matches("false"))
        assertTrue(PrimitiveParameterType.BooleanType.regex.matches("TRUE"))
        assertTrue(PrimitiveParameterType.BooleanType.regex.matches("False"))
    }

    @Test
    fun `BooleanType regex rejects non-boolean strings`() {
        assertFalse(PrimitiveParameterType.BooleanType.regex.matches("yes"))
        assertFalse(PrimitiveParameterType.BooleanType.regex.matches("1"))
    }

    @Test
    fun `CharType regex matches single character`() {
        assertTrue(PrimitiveParameterType.CharType.regex.matches("a"))
        assertFalse(PrimitiveParameterType.CharType.regex.matches("ab"))
        assertFalse(PrimitiveParameterType.CharType.regex.matches(""))
    }

    @Test
    fun `LongType regex matches large numbers`() {
        assertTrue(PrimitiveParameterType.LongType.regex.matches("9999999999999"))
        assertTrue(PrimitiveParameterType.LongType.regex.matches("-123"))
    }

    @Test
    fun `FloatType regex matches decimal numbers`() {
        assertTrue(PrimitiveParameterType.FloatType.regex.matches("3"))
        assertTrue(PrimitiveParameterType.FloatType.regex.matches("3.14"))
        assertTrue(PrimitiveParameterType.FloatType.regex.matches("-2.5"))
    }

    @Test
    fun `StringType regex matches anything`() {
        assertTrue(PrimitiveParameterType.StringType.regex.matches("anything goes"))
        assertTrue(PrimitiveParameterType.StringType.regex.matches(""))
    }

    // -----------------------------------------------------------------------
    // segmentToValue coercion
    // -----------------------------------------------------------------------

    @Test
    fun `IntType segmentToValue converts valid int`() {
        assertEquals(42, PrimitiveParameterType.IntType.segmentToValue("42"))
    }

    @Test
    fun `IntType segmentToValue returns null for invalid`() {
        assertNull(PrimitiveParameterType.IntType.segmentToValue("notANumber"))
    }

    @Test
    fun `StringType segmentToValue returns plain string as-is`() {
        assertEquals("hello", PrimitiveParameterType.StringType.segmentToValue("hello"))
    }

    @Test
    fun `StringType segmentToValue decodes URL encoded spaces`() {
        assertEquals("hello world", PrimitiveParameterType.StringType.segmentToValue("hello%20world"))
    }

    @Test
    fun `StringType segmentToValue decodes URL encoded special characters`() {
        assertEquals("file (1).png", PrimitiveParameterType.StringType.segmentToValue("file%20%281%29.png"))
    }

    @Test
    fun `StringType segmentToValue decodes plus as plus`() {
        assertEquals("a+b", PrimitiveParameterType.StringType.segmentToValue("a%2Bb"))
    }

    @Test
    fun `CharType segmentToValue returns first character`() {
        assertEquals('x', PrimitiveParameterType.CharType.segmentToValue("x"))
    }

    @Test
    fun `BooleanType segmentToValue converts valid boolean`() {
        assertEquals(true, PrimitiveParameterType.BooleanType.segmentToValue("true"))
        assertEquals(false, PrimitiveParameterType.BooleanType.segmentToValue("false"))
    }

    @Test
    fun `ByteType segmentToValue converts valid byte`() {
        assertEquals(127.toByte(), PrimitiveParameterType.ByteType.segmentToValue("127"))
    }

    @Test
    fun `ShortType segmentToValue converts valid short`() {
        assertEquals(12345.toShort(), PrimitiveParameterType.ShortType.segmentToValue("12345"))
    }

    @Test
    fun `LongType segmentToValue converts valid long`() {
        assertEquals(9999999999L, PrimitiveParameterType.LongType.segmentToValue("9999999999"))
    }

    @Test
    fun `FloatType segmentToValue converts valid float`() {
        assertEquals(3.14f, PrimitiveParameterType.FloatType.segmentToValue("3.14"))
    }

    @Test
    fun `DoubleType segmentToValue converts valid double`() {
        assertEquals(2.718281828, PrimitiveParameterType.DoubleType.segmentToValue("2.718281828"))
    }

    // -----------------------------------------------------------------------
    // label
    // -----------------------------------------------------------------------

    @Test
    fun `label returns class simple name`() {
        assertEquals("Int", PrimitiveParameterType.IntType.label)
        assertEquals("String", PrimitiveParameterType.StringType.label)
        assertEquals("Boolean", PrimitiveParameterType.BooleanType.label)
    }
}
