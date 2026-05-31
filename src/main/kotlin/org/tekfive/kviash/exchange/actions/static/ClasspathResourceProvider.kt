package org.tekfive.kviash.exchange.actions.`static`

class ClasspathResourceProvider(
    private val useUrlPrefix: Boolean = true,
    private val classLoader: ClassLoader = Thread.currentThread().contextClassLoader,
) : ResourceProvider {

    override fun get(urlPrefix: String, resourcePath: String): ByteArray? {
        val classpathPath = if (useUrlPrefix) {
            combine(urlPrefix, resourcePath).removePrefix("/")
        } else {
            resourcePath
        }

        val resource = classLoader.getResource(classpathPath) ?: return null
        return resource.openStream().use { it.readBytes() }
    }
}
