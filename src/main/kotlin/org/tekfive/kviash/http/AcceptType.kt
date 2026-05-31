package org.tekfive.kviash.http

/**
 * Common HTTP content type constants for use with route accept type registration.
 */
object AcceptType {

    // Text
    const val TEXT_PLAIN = "text/plain"
    const val TEXT_HTML = "text/html"
    const val TEXT_CSS = "text/css"
    const val TEXT_CSV = "text/csv"
    const val TEXT_XML = "text/xml"
    const val TEXT_JAVASCRIPT = "text/javascript"
    const val TEXT_MARKDOWN = "text/markdown"
    const val TEXT_EVENT_STREAM = "text/event-stream"

    // Application
    const val APPLICATION_JSON = "application/json"
    const val APPLICATION_XML = "application/xml"
    const val APPLICATION_PDF = "application/pdf"
    const val APPLICATION_ZIP = "application/zip"
    const val APPLICATION_GZIP = "application/gzip"
    const val APPLICATION_JAVASCRIPT = "application/javascript"
    const val APPLICATION_OCTET_STREAM = "application/octet-stream"
    const val APPLICATION_FORM_URLENCODED = "application/x-www-form-urlencoded"
    const val APPLICATION_YAML = "application/yaml"
    const val APPLICATION_GRAPHQL = "application/graphql+json"

    // Multipart
    const val MULTIPART_FORM_DATA = "multipart/form-data"

    // Image
    const val IMAGE_PNG = "image/png"
    const val IMAGE_JPEG = "image/jpeg"
    const val IMAGE_GIF = "image/gif"
    const val IMAGE_WEBP = "image/webp"
    const val IMAGE_SVG = "image/svg+xml"
    const val IMAGE_ICON = "image/x-icon"

    // Font
    const val FONT_WOFF = "font/woff"
    const val FONT_WOFF2 = "font/woff2"

    // Wildcard
    const val ANY = "*/*"

    /**
     * Parses the Accept header values into a list of media types, stripping quality parameters.
     * For example, `"text/html;q=0.9, application/json"` becomes `["text/html", "application/json"]`.
     */
    fun parse(headerValues: List<String>): List<String> {
        return headerValues
            .flatMap { it.split(",") }
            .map { it.substringBefore(";").trim() }
    }

    /**
     * Returns `true` if [acceptedType] matches [routeType].
     *
     * Supports exact matching and wildcard matching.
     */
    fun matches(acceptedType: String, routeType: String): Boolean {
        if (acceptedType == ANY) return true
        if (acceptedType.equals(routeType, ignoreCase = true)) return true

        val slashIdx = acceptedType.indexOf('/')
        if (slashIdx > 0 && acceptedType.endsWith("/*")) {
            val acceptedPrimary = acceptedType.substring(0, slashIdx)
            val routeSlashIdx = routeType.indexOf('/')
            if (routeSlashIdx > 0) {
                val routePrimary = routeType.substring(0, routeSlashIdx)
                return acceptedPrimary.equals(routePrimary, ignoreCase = true)
            }
        }

        return false
    }
}
