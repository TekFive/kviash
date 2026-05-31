package org.tekfive.kviash

import java.net.URI

val String.escapeHtml: String
    get() = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#x27;")

val String.isUrl: Boolean
    get() {
        return try {
            URI(this).toURL()
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }
