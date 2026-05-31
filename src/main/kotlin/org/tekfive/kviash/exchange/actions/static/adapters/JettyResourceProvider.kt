package org.tekfive.kviash.exchange.actions.static.adapters

import org.eclipse.jetty.util.resource.Resource
import org.tekfive.kviash.exchange.actions.static.ResourceProvider

class JettyResourceProvider(private val baseResource: Resource) : ResourceProvider {
    override fun get(urlPrefix: String, resourcePath: String): ByteArray? {
        val path = combine(urlPrefix, resourcePath).removePrefix("/")
        val resource = baseResource.resolve(path) ?: return null
        if (!resource.exists() || resource.isDirectory()) return null
        return resource.newInputStream().use { it.readBytes() }
    }
}
