package org.tekfive.kviash.exchange.actions.static

/**
 * A [ResourceProvider] that wraps another provider and falls back to serving
 * `index.html` when the requested path has no file extension. This supports
 * single-page application (SPA) client-side routing — paths like `/workflows/123`
 * serve the SPA shell, while paths like `/assets/app.js` serve the actual file.
 */
class SpaResourceProvider(private val delegate: ResourceProvider) : ResourceProvider {

    override fun get(urlPrefix: String, resourcePath: String): ByteArray? {
        val result = delegate.get(urlPrefix, resourcePath)
        if (result != null) return result

        val hasExtension = resourcePath.substringAfterLast('/').contains('.')
        if (hasExtension) return null

        return delegate.get(urlPrefix, "index.html")
    }
}
