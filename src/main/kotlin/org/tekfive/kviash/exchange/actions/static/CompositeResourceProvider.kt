package org.tekfive.kviash.exchange.actions.static

class CompositeResourceProvider(
    private val first: ResourceProvider,
    private val second: ResourceProvider,
    private vararg val rest: ResourceProvider,
) : ResourceProvider {

    override fun get(urlPrefix: String, resourcePath: String): ByteArray? {
        first.get(urlPrefix, resourcePath)?.let { return it }
        second.get(urlPrefix, resourcePath)?.let { return it }
        for (provider in rest) {
            provider.get(urlPrefix, resourcePath)?.let { return it }
        }
        return null
    }
}
