package org.tekfive.kviash.exchange.actions.`static`

class MemoryResourceProvider(
    private val resources: Map<String, ByteArray>,
) : ResourceProvider {

    override fun get(urlPrefix: String, resourcePath: String): ByteArray? {
        val normalized = resourcePath.trimStart('/')
        return resources[normalized]
    }
}
