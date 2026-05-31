package org.tekfive.kviash.http

import org.tekfive.kviash.KviashConfiguration
import java.io.InputStream

class HttpRequestContent(
    val type: String?,
    val length: Long?,
    internal val configuration: org.tekfive.kviash.KviashConfiguration,
    internal val inputStreamProvider:(() -> InputStream?),
) {
    val inputStream: InputStream? by lazy { inputStreamProvider() }

    val bytes: ByteArray by lazy { inputStream?.readBytes() ?: byteArrayOf() }

    val text: String
        get() = bytes.decodeToString()

    fun processBytes(bufferSize: Int = configuration.inputBufferSize, bytesProcessor: (ByteArray) -> Unit): Long {
        val inputStream = inputStream
        if (inputStream == null) {
            return 0
        }

        var totalBytesRead = 0L
        val buffer = ByteArray(bufferSize)

        while (true) {
            val read = inputStream.read(buffer, 0, buffer.size)
            if (read == -1) {
                break
            } else if (read > 0) {
                totalBytesRead += read
                if (read == buffer.size) {
                    bytesProcessor(buffer)
                } else {
                    bytesProcessor(buffer.copyOfRange(0, read))
                }
            }
        }

        return totalBytesRead
    }

    companion object {
        operator fun invoke(request: org.tekfive.kviash.http.HttpRequest): HttpRequestContent {
            return HttpRequestContent(request.contentType, request.contentLength, request.configuration) { request.inputStream }
        }
    }
}