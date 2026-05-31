package org.tekfive.kviash.http

import org.tekfive.kviash.KviashConfiguration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class HttpRequestParameters(
    private val parametersCallback: () -> Map<String, List<String>>,
    private val configuration: KviashConfiguration,
) {

    val parameters: Map<String, List<String>> by lazy { parametersCallback() }

    val names: List<String> by lazy { parameters.keys.toList() }

    val size: Int by lazy { parameters.size }

    operator fun contains(name: String): Boolean {
        return parameters.containsKey(name)
    }

    /**
     *
     * @param name The parameter name.
     * @return The parameter value or <code>null</code> if not present.
     */
    operator fun get(name: String): String? {
        return getValue(name)
    }

    /**
     *
     * @param name The parameter name.
     * @param defaultValue The default value to return if the given parameter is not present.
     * @return The parameter value or defaultValue if not present.
     */
    operator fun get(name: String, defaultValue: String): String {
        return parameters[name]?.let { values -> values.firstOrNull { it.isNotBlank() } } ?: defaultValue
    }

    operator fun get(name: String, maxLength: Int): String? {
        val value = getValue(name)
        return if (value != null && value.length > maxLength) {
            null
        } else {
            value
        }
    }

    /**
     *
     * @param name The parameter name.
     * @return Each parameter value as a Character for the given name. If no values for the given parameter exists an empty list is returned.
     */
    fun getCharacters(name: String): List<Char> {
        return getValues(name).mapNotNull { if (it.isNotEmpty()) it[0] else null }
    }

    /**
     *
     * @param name The parameter name
     * @return The first character of the parameter value or <status>null</status> if the given parameter doesn't exist.
     */
    fun getCharacter(name: String): Char? {
        return getCharacters(name).firstOrNull()
    }

    /**
     *
     * @param name The parameter name
     * @param defaultValue The value to return if the parameter doesn't exists.
     * @return The first character of the parameter value or the given defaultValue if the given parameter doesn't exist.
     */
    fun getCharacter(name: String, defaultValue: Char): Char {
        return getCharacter(name) ?: defaultValue
    }

    /**
     *
     * @param name The parameter name.
     * @return Each parameter value parsed into a Boolean for the given name. If no values for the given parameter exists an empty list is returned.
     */
    fun getBooleans(name: String, booleanMapper: (String) -> Boolean? = { it.toBooleanStrictOrNull() }): List<Boolean> {
        return getValues(name).mapNotNull { booleanMapper(it.trim()) }
    }

    /**
     *
     * @param name The parameter name
     * @return The parameter value parsed as a Boolean or <status>null</status> if the given parameter doesn't exist.
     */
    fun getBoolean(name: String, booleanMapper: (String) -> Boolean? = { it.toBooleanStrictOrNull() }): Boolean? {
        return getBooleans(name, booleanMapper).firstOrNull()
    }

    fun getBoolean(name: String, defaultValue: Boolean, booleanMapper: (String) -> Boolean? = { it.toBooleanStrictOrNull() }): Boolean {
        return getBoolean(name, booleanMapper) ?: defaultValue
    }

    /**
     *
     * @param name The parameter name.
     * @return Each parameter value parsed into a Byte for the given name. If no values for the given parameter exists an empty list is returned.
     */
    fun getBytes(name: String): List<Byte> {
        return getValues(name).mapNotNull { it.trim().toByteOrNull() }
    }

    /**
     *
     * @param name The parameter name
     * @return The parameter value parsed as a Byte or <status>null</status> if the given parameter doesn't exist.
     */
    fun getByte(name: String): Byte? {
        return getBytes(name).firstOrNull()
    }

    fun getByte(name: String, defaultValue: Byte): Byte {
        return getByte(name) ?: defaultValue
    }

    /**
     *
     * @param name The parameter name.
     * @return Each parameter value parsed into a Short for the given name. If no values for the given parameter exists an empty list is returned.
     */
    fun getShorts(name: String): List<Short> {
        return getValues(name).mapNotNull { it.trim().toShortOrNull() }
    }

    /**
     *
     * @param name The parameter name
     * @return The parameter value parsed as a Short or <status>null</status> if the given parameter doesn't exist.
     */
    fun getShort(name: String): Short? {
        return getShorts(name).firstOrNull()
    }

    fun getShort(name: String, defaultValue: Short): Short {
        return getShort(name) ?: defaultValue
    }

    fun getInts(name: String): List<Int> {
        return getValues(name).mapNotNull { it.trim().toIntOrNull() }
    }

    /**
     *
     * @param name The parameter name
     * @return The parameter value parsed as a Integer or <status>null</status> if the given parameter doesn't exist.
     */
    fun getInt(name: String): Int? {
        return getInts(name).firstOrNull()
    }

    fun getInt(name: String, defaultValue: Int): Int {
        return getInt(name) ?: defaultValue
    }

    fun getLongs(name: String): List<Long> {
        return getValues(name).mapNotNull { it.trim().toLongOrNull() }
    }

    fun getLong(name: String): Long? {
        return getLongs(name).firstOrNull()
    }

    fun getLong(name: String, defaultValue: Long): Long {
        return getLong(name) ?: defaultValue
    }

    fun getFloats(name: String): List<Float> {
        return getValues(name).mapNotNull { it.trim().toFloatOrNull() }
    }

    /**
     *
     * @param name The parameter name
     * @return The parameter value parsed as a Float or <status>null</status> if the given parameter doesn't exist.
     */
    fun getFloat(name: String): Float? {
        return getFloats(name).firstOrNull()
    }

    fun getFloat(name: String, defaultValue: Float): Float {
        return getFloat(name) ?: defaultValue
    }


    fun getDoubles(name: String): List<Double> {
        return getValues(name).mapNotNull { it.trim().toDoubleOrNull() }
    }

    /**
     *
     * @param name The parameter name
     * @return The parameter value parsed as a Double or <status>null</status> if the given parameter doesn't exist.
     */
    fun getDouble(name: String): Double? {
        return getDoubles(name).firstOrNull()
    }

    fun getDouble(name: String, defaultValue: Double): Double {
        return getDouble(name) ?: defaultValue
    }

    fun getHtml5FormattedDate(name: String): LocalDate? {
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE

        for (value in getValues(name)) {
            try {
                return LocalDate.parse(value, formatter)
            } catch (e: Exception) {}
        }

        return null
    }

    fun getHtml5FormattedDate(name: String, defaultDate: LocalDate): LocalDate {
        return getHtml5FormattedDate(name) ?: defaultDate
    }

    fun getEmailAddress(name: String): String? {
        val value = getValue(name) ?: return null
        return if (EMAIL_PATTERN.matches(value)) value else null
    }

    fun splitValue(name: String, delimiters: String = "\n"): List<String> {
        return get(name)?.split(delimiters)?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
    }


    fun getValue(name: String): String? {
        return parameters[name]?.let { values -> values.firstOrNull { it.isNotBlank() } }?.let { if (configuration.trimParameterValues) it.trim() else it }
    }

    /**
     *
     * @param name The parameter name.
     * @return The parameter values for the given name. If no values for the given parameter exists an empty list is returned.
     */
    fun getValues(name: String): List<String> {
        return parameters[name] ?: emptyList()
    }

    constructor(parameters: Map<String, List<String>>, configuration: org.tekfive.kviash.KviashConfiguration) : this({ parameters }, configuration)

    constructor(request: org.tekfive.kviash.http.HttpRequest) : this( { toParameterMap(request.parameters) }, request.configuration)

    fun containsContent(name: String): Boolean = parameters[name]?.let { values -> values.any { it.isNotBlank() } } ?: false

    companion object {
        private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun toParameterMap(parameters: List<org.tekfive.kviash.http.HttpRequestParameter>): Map<String, List<String>> {
            val parametersMap = mutableMapOf<String, MutableList<String>>()

            for (parameter in parameters) {
                parametersMap.getOrPut(parameter.name) { mutableListOf() }.addAll(parameter.values)
            }

            return parametersMap
        }
    }
}