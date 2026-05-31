package org.tekfive.kviash.exchange

import java.util.Collections
import kotlin.reflect.KClass

class CustomParameterRegistry {
    private val providers: MutableMap<KClass<*>, CustomParameterProvider<*>> = Collections.synchronizedMap<KClass<*>, CustomParameterProvider<*>>(mutableMapOf())

    fun <T : Any> registerProvider(clazz: KClass<T>, provider: CustomParameterProvider<T>) {
        providers[clazz] = provider
    }

    fun isRegistered(clazz: KClass<*>): Boolean {
        return providers.contains(clazz)
    }

    fun clearRegistrations() {
        providers.clear()
    }

    fun getParameter(clazz: KClass<*>, exchange: Exchange): Any? {
        val provider = providers[clazz]
        return if (provider == null) {
            null
        } else {
            provider.get(exchange)
        }
    }
}