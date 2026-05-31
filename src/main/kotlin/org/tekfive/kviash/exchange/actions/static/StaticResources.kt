package org.tekfive.kviash.exchange.actions.static

import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.exchange.ExchangeAction
import org.tekfive.kviash.routing.RouteRegistrationAware
import org.tekfive.kviash.routing.UrlPlugin
import org.tekfive.kviash.http.HttpHeader
import org.tekfive.kviash.http.HttpMethod
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class StaticResources(
    private val resourceProvider: ResourceProvider,
    private val cacheControl: String? = "public, max-age=31536000, immutable",
    private val enableETag: Boolean = false,
    private val keyedResourceDirs: Set<KeyedResourceDir> = KeyedResourceDir.DEFAULTS,
) : UrlPlugin, RouteRegistrationAware, ExchangeAction {

    private val resourceDirsByKey: Map<String, KeyedResourceDir> = keyedResourceDirs.associateBy { it.key }

    private lateinit var urlPrefix: String

    private val hashCache = ConcurrentHashMap<String, String>()
    private val urlPrefixSegments: List<String>
        get() = urlPrefix.trimStart('/').split('/').filter { it.isNotEmpty() }

    private val addCacheHeader = if (cacheControl.isNullOrEmpty()) null else AddCacheHeader(cacheControl)

    override fun onRouteRegistered(route: String) {
        require(!::urlPrefix.isInitialized) { "An instance of StaticResources can only be registered in the RouteTable once."}
        urlPrefix = route.split('/')
            .filter { it.isNotEmpty() && !it.startsWith("{") }
            .joinToString("/", prefix = "/")
    }

    override fun invoke(exchange: Exchange): Any? {
        val segments = exchange.requestPath.segments
        val resourceSegments = segments.drop(urlPrefixSegments.size)

        if (resourceSegments.isEmpty()) {
            exchange.response.sendStatus(404)
            return null
        }

        if (resourceSegments.any { it == ".." }) {
            exchange.response.sendStatus(404)
            return null
        }

        val resourcePath = resourceSegments.joinToString("/")
        val bytes = resourceProvider.get(urlPrefix, resourcePath)

        if (bytes == null) {
            exchange.response.sendStatus(404)
            return null
        }

        val extension = resourcePath.substringAfterLast('.', "")
        val contentType = CONTENT_TYPES[extension] ?: "application/octet-stream"

        exchange.response.setContentType(contentType)

        if (addCacheHeader != null) {
            addCacheHeader(exchange)
        }

        if (enableETag) {
            val etag = "\"${computeETag(bytes)}\""
            exchange.response.addHeader(HttpHeader.ETag, etag)

            val ifNoneMatch = exchange.request.getFirstHeaderValue(HttpHeader.IfNoneMatch)
            if (ifNoneMatch != null && ifNoneMatch == etag) {
                exchange.response.sendStatus(304)
                return null
            }
        }

        exchange.response.setContentLength(bytes.size.toLong())
        if (exchange.request.method != HttpMethod.HEAD) {
            exchange.response.outputStream.write(bytes)
        }
        return null
    }

    override fun urlTypes(): Set<String> = resourceDirsByKey.keys

    override fun typedUrl(type: String, resource: String): String? {
        val urlType = resourceDirsByKey[type] ?: return null
        val resourcePath = "${urlType.subdir}/${resource.trimStart('/')}"
        return getUrl(resourcePath)
    }

    fun getUrl(resourcePath: String): String {
        val hash = hashCache.computeIfAbsent(resourcePath) { computeHash(it) }
        val base = "$urlPrefix/${resourcePath.trimStart('/')}"
        return if (hash.isNotEmpty()) "$base?v=$hash" else base
    }


    private fun computeHash(resourcePath: String): String {
        val bytes = resourceProvider.get(urlPrefix, resourcePath.trimStart('/')) ?: return ""
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }.take(8)
    }

    companion object {
        internal fun computeETag(body: ByteArray): String {
            val digest = MessageDigest.getInstance("MD5")
            val hash = digest.digest(body)
            return hash.joinToString("") { "%02x".format(it) }
        }

        private val CONTENT_TYPES = mapOf(
            "css" to "text/css",
            "js" to "application/javascript",
            "html" to "text/html",
            "htm" to "text/html",
            "json" to "application/json",
            "xml" to "text/xml",
            "txt" to "text/plain",
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "gif" to "image/gif",
            "svg" to "image/svg+xml",
            "ico" to "image/x-icon",
            "webp" to "image/webp",
            "avif" to "image/avif",
            "woff" to "font/woff",
            "woff2" to "font/woff2",
            "ttf" to "font/ttf",
            "otf" to "font/otf",
            "eot" to "application/vnd.ms-fontobject",
            "mp4" to "video/mp4",
            "webm" to "video/webm",
            "pdf" to "application/pdf",
            "zip" to "application/zip",
            "map" to "application/json",
        )
    }
}