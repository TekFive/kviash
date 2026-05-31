package org.tekfive.kviash.exchange.actions.`static`

fun interface ResourceProvider {
    fun get(urlPrefix: String, resourcePath: String): ByteArray?

    fun combine(urlPrefix: String, resourcePath: String): String {
        return urlPrefix.removeSuffix("/") + "/" + resourcePath.removePrefix("/")
    }
}
