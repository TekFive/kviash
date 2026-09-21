package org.tekfive.kviash.routing

import java.net.URLDecoder
import java.util.UUID
import kotlin.reflect.KClass

private val uuidPattern = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

internal fun repeat(str: String?, times: Int): String {
    val builder = StringBuilder()
    for (i in 0 until times) builder.append(str)
    return builder.toString()
}

/**
 * A route function parameter type that can supply a Regex if the route doesn't explicitly provide it (i.e. {}).
 */
internal enum class PrimitiveParameterType(
    val clazz: KClass<*>,
    val regex: Regex,
    val segmentToValue:(String)->Any?,
) {
    StringType(
        String::class,
        Regex(".*"),
        { URLDecoder.decode(it.replace("+", "%2B"), Charsets.UTF_8) }
    ),

    UuidType(
        UUID::class,
        uuidPattern,
        // Validate the full format even when an explicit route pattern is used.
        { if (uuidPattern.matches(it)) UUID.fromString(it) else null }
    ),

    CharType(
        Char::class,
        Regex("."),
        { it[0] }
    ),

    BooleanType(
        Boolean::class,
        Regex("(((t|T)(r|R)(u|U)(e|E))|((f|F)(a|A)(l|L)(s|S)(e|E)))"),
        { it.toBooleanStrictOrNull()}
    ),

    ByteType(
        Byte::class,
        Regex("(-?)\\d" + repeat("\\d?", 2)),
        { it.toByteOrNull() }
    ),
    ShortType(
        Short::class,
        Regex("(-?)\\d" + repeat("\\d?", 4)),
        { it.toShortOrNull() }
    ),

    IntType(
        Int::class,
        Regex("(-?)\\d" + repeat("\\d?", 9)),
        { it.toIntOrNull() }
    ),

    LongType(
        Long::class,
        Regex("(-?)\\d" + repeat("\\d?", 18)),
        { it.toLongOrNull() }
    ),

    FloatType(
        Float::class,
        Regex("(-?)\\d" + repeat("\\d?", 7) + "(\\.\\d" + repeat("\\d?", 22) + ")?"),
        { it.toFloatOrNull() }
    ),

    DoubleType(
        Double::class,
        Regex("(-?)\\d" + repeat("\\d?", 14) + "(\\.\\d" + repeat("\\d?", 45) + ")?"),
        { it.toDoubleOrNull() }
    ),
    ;

    val label: String = clazz.simpleName!!

    companion object {
        fun fromClass(clazz: KClass<*>): PrimitiveParameterType? {
            return entries.firstOrNull { it.clazz == clazz }
        }
    }
}
