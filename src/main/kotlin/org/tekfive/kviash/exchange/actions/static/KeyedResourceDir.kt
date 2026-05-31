package org.tekfive.kviash.exchange.actions.static

data class KeyedResourceDir(val key: String, val subdir: String) {
    companion object {
        val JS = KeyedResourceDir("JS", "js")
        val CSS = KeyedResourceDir("CSS", "css")
        val IMG = KeyedResourceDir("IMG", "img")
        val FONT = KeyedResourceDir("FONT", "fonts")

        val DEFAULTS = setOf(JS, CSS, IMG, FONT)
    }
}
