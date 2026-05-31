package org.tekfive.kviash.exchange.actions.static.adapters

import org.tekfive.kviash.exchange.actions.static.ResourceProvider
import jakarta.servlet.ServletContext

class JakartaResourceProvider(private val servletContext: ServletContext) : ResourceProvider {
    override fun get(urlPrefix: String, resourcePath: String): ByteArray? {
        val path = combine(urlPrefix, resourcePath)
        return servletContext.getResourceAsStream(path)?.readBytes()
    }
}