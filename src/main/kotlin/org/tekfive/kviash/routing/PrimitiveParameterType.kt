package org.tekfive.kviash.routing

import java.net.URLDecoder
import kotlin.reflect.KClass

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
