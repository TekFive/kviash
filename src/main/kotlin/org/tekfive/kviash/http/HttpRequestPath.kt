package org.tekfive.kviash.http

private val slashLookaroundRegex = Regex("(?=/)|(?<=/)")

fun String.toPathSegments(ignoreTrailingSlash: Boolean): List<String> {
    if (this.isEmpty()) return emptyList()

    return if (ignoreTrailingSlash) {
        // Standard split: removes delimiters and filters out empty strings
        // Result: "/one/" -> ["one"]
        this.split("/").filter { it.isNotEmpty() }
    } else {
        // Special case for root-only when preserving slashes
        if (this == "/") return listOf("/")

        // Preserve slashes using lookarounds
        // Result: "/one/" -> ["/", "one", "/"]
        this.split(slashLookaroundRegex).filter { it.isNotEmpty() }
    }
}


fun List<String>.toPath(): String {
    return if (contains("/")) {
        joinToString("")
    } else {
        "/${joinToString("/")}"
    }
}

class HttpRequestPath(
    val segments: List<String>,
    val path: String = segments.toPath()
) {

    val length: Int
        get() = segments.size

    val empty: Boolean
        get() = segments.isEmpty()

    /**
     * @return The file name of the path or <status>null</status> if this path does not contain a file name at the end as defined by a segment with at least one period '.'.
     */
    val fileName: String?
        get() {
            val lastSegment = segments.lastOrNull()
            return if ((lastSegment == null) || !lastSegment.contains('.')) {
                null
            } else {
                lastSegment
            }
        }

    /**
     * @return The file name extension or null if the path does contain a file name.
     */
    val fileExtension: String?
        get() {
            val fileName = fileName
            return if (fileName == null) {
                null
            } else {
                val lastPeriod = fileName.lastIndexOf('.')
                fileName.substring(lastPeriod + 1)
            }
        }

    /**
     *
     * @param index
     * @return The segment at the given index.
     */
    @Throws(IndexOutOfBoundsException::class)
    operator fun get(index: Int): String {
        return segments[index]
    }

    fun getOrNull(index: Int): String? {
        return segments.getOrNull(index)
    }

    fun getBoolean(index: Int): Boolean? {
        return segments.getOrNull(index)?.toBoolean()
    }

    fun getBoolean(index: Int, defaultValue: Boolean): Boolean {
        val value = getBoolean(index)
        return value ?: defaultValue
    }

    fun getByte(index: Int): Byte? {
        return segments.getOrNull(index)?.toByteOrNull()
    }

    fun getByte(index: Int, defaultValue: Byte): Byte {
        val value = segments.getOrNull(index)?.toByteOrNull()
        return value ?: defaultValue
    }

    fun getShort(index: Int): Short? {
        return segments.getOrNull(index)?.toShortOrNull()
    }

    fun getShort(index: Int, defaultValue: Short): Short {
        val value = getShort(index)
        return value ?: defaultValue
    }

    /**
     *
     * @param index
     * @return The parsed int segment at the given index
     */
    fun getInt(index: Int): Int? = segments.getOrNull(index)?.toIntOrNull()

    /**
     *
     * @param index
     * @return The parsed long segment at the given index
     */
    fun getLong(index: Int): Long? = segments.getOrNull(index)?.toLongOrNull()

    /**
     *
     * @param index
     * @return The parsed float segment at the given index
     */
    fun getFloat(index: Int): Float? = segments.getOrNull(index)?.toFloatOrNull()

    /**
     *
     * @param index
     * @return The parsed double segment at the given index
     */
    fun getDouble(index: Int): Double? = segments.getOrNull(index)?.toDoubleOrNull()

    /**
     *
     * @param pathPrefixCandidate
     * @return True if this request path starts with the given path, false otherwise.
     */
    fun startsWith(pathPrefixCandidate: String): Boolean {
        return path.startsWith(pathPrefixCandidate)
    }

    /**
     *
     * @param segments
     * @return True if this request path starts with the given path segments, false otherwise.
     */
    fun startsWith(segments: List<String>): Boolean {
        return if (segments.size > this.segments.size) {
            false
        } else {
            for (i in segments.indices) {
                if (segments[i] != this.segments[i]) {
                    return false
                }
            }
            true
        }
    }

    /**
     *
     * Returns a new RequestPath with the given number of segments removed from the start of the path. The RequestPath
     * this method is called is unaltered.
     *
     * <status>requestPath.pop(2)</status> performed on:
     *
     * <status>/one/two/three</status>
     *
     * results in:
     *
     * <status>/three</status>
     *
     * @param numberSegments The number of segments to pop from this path.
     * @return A new RequestPath with the given number of segments removed from the head of the path.
     * @throws IndexOutOfBoundsException If numberSegments < 0 or numberSegments >= [.size].
     */
    @Throws(IndexOutOfBoundsException::class)
    fun pop(numberSegments: Int): Pair<HttpRequestPath, HttpRequestPath> {
        val head = segments.subList(0, numberSegments)
        val tail = segments.subList(numberSegments, segments.size)
        return HttpRequestPath(head) to HttpRequestPath(tail)
    }

    /**
     * Same as <status>pop(1)</status>.
     *
     * @see .pop
     * @return A new RequestPath with the 1 segment removed from the head of the path.
     * @throws IndexOutOfBoundsException if [.size] == 0.
     */
    @Throws(IndexOutOfBoundsException::class)
    fun pop(): Pair<String, HttpRequestPath> {
        return segments[0] to HttpRequestPath(segments.subList(1, segments.size))
    }


    /**
     *
     * @param index
     * @return A new RequestPath from segment 0 to the given index.
     * @throws IndexOutOfBoundsException If index < 0 or index >= [.size].
     */
    @Throws(IndexOutOfBoundsException::class)
    fun subSegments(fromIndex: Int, toIndex: Int): HttpRequestPath {
        val segments = segments.subList(fromIndex, toIndex)
        return HttpRequestPath(segments)
    }

    /**
     *
     * @param path
     * @return True if the segments of this request path match the segments of the given path, false otherwise.
     */
    fun equals(path: String): Boolean {
        return this.path == path
    }

    /**
     *
     * @param segments
     * @return True if the segments of this request path match the given segments, false otherwise.
     */
    fun equals(segments: List<String>): Boolean {
        return this.segments == segments
    }

    override fun equals(other: Any?): Boolean {
        return other is HttpRequestPath && other.segments == segments
    }

    override fun hashCode(): Int {
        return segments.hashCode()
    }

    override fun toString(): String {
        return path
    }
}