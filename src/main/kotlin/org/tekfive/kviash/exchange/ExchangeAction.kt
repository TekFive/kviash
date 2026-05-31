package org.tekfive.kviash.exchange

fun interface ExchangeAction {
    operator fun invoke(exchange: Exchange): Any?
}