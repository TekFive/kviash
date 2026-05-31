package org.tekfive.kviash.exchange

import org.slf4j.Logger
import org.slf4j.LoggerFactory

interface ExchangeErrorLogger {
    fun warn(message: String, exchange: Exchange)

    fun error(exception: Exception, exchange: Exchange) {
        error(exception.message ?: exception.toString(), exchange, exception)
    }

    fun error(message: String, exchange: Exchange)

    fun error(message: String, exchange: Exchange, exception: Exception)
}

object DefaultExchangeErrorLogger : ExchangeErrorLogger {
    private val log: Logger = LoggerFactory.getLogger(Exchange::class.java)

    override fun warn(message: String, exchange: Exchange) {
        log.warn(message)
    }

    override fun error(message: String, exchange: Exchange) {
        log.error(message)
    }

    override fun error(message: String, exchange: Exchange, exception: Exception) {
        log.error(message, exception)
    }
}