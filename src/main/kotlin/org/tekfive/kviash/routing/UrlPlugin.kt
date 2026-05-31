package org.tekfive.kviash.routing

interface UrlPlugin {
    fun urlTypes(): Set<String>
    fun typedUrl(type: String, resource: String): String?
}
