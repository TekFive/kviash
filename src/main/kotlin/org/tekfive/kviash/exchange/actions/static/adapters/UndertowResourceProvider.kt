package org.tekfive.kviash.exchange.actions.static.adapters

import io.undertow.server.handlers.resource.ResourceManager
import org.tekfive.kviash.exchange.actions.static.ResourceProvider

class UndertowResourceProvider(private val resourceManager: ResourceManager) : ResourceProvider {
    override fun get(urlPrefix: String, resourcePath: String): ByteArray? {
        val path = combine(urlPrefix, resourcePath).removePrefix("/")
        val resource = resourceManager.getResource(path) ?: return null
        if (resource.isDirectory) return null
        return resource.url?.openStream()?.use { it.readBytes() }
    }
}
