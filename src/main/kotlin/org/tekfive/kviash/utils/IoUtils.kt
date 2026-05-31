package org.tekfive.kviash.utils

import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.io.Writer

object IoUtils {

    fun copy(input: InputStream, output: OutputStream, bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
        }
    }

    fun copy(input: Reader, output: Writer) {
        val buffer = CharArray(DEFAULT_BUFFER_SIZE)
        var charsRead: Int
        while (input.read(buffer).also { charsRead = it } != -1) {
            output.write(buffer, 0, charsRead)
        }
    }
}
