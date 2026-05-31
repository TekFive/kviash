package org.tekfive.kviash.exchange

fun interface CustomParameterProvider<T : Any> {
    fun get(exchange: Exchange): T?
}