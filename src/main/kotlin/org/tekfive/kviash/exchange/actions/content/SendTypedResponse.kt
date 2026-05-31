package org.tekfive.kviash.exchange.actions.content

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.exchange.ExchangeAction
import org.tekfive.kviash.utils.IoUtils
import java.io.InputStream
import java.io.Reader
import java.nio.charset.Charset

open class SendActionResult(val contentType: String, val charset: Charset = Charsets.UTF_8) : ExchangeAction {
    val log: Logger = LoggerFactory.getLogger(SendActionResult::class.java)

    open fun convertToString(value: Any): String {
        return value.toString()
    }

    override fun invoke(exchange: Exchange): Any? {
        if (exchange.response.committed) {
            return null
        }

        val value = exchange.actionResult
        if (value != null) {
            val response = exchange.response
            if (!response.committed) {
                response.setContentType(contentType)
                if (value is InputStream) {
                    IoUtils.copy(value, response.outputStream, exchange.configuration.outputBufferSize)
                } else if (value is ByteArray) {
                    response.setContentLength(value.size.toLong())
                    response.outputStream.write(value)
                } else if (value is Reader) {
                    IoUtils.copy(value, response.outputWriter)
                } else {
                    val responseContent = convertToString(value)
                    val responseBytes = responseContent.toByteArray(charset)

                    response.setContentLength(responseBytes.size.toLong())
                    response.outputStream.write(responseBytes)
                }
            }
        }

        return null
    }
}
