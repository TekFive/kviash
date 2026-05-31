package org.tekfive.kviash.exchange.actions

import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.exchange.ExchangeAction
import org.tekfive.kviash.exchange.actions.adapters.ForwardAdapter

class ForwardRequest(
    val adapter: ForwardAdapter,
    rootDispatchPath: String? = null,
    /**
     * Space delimited extensions that are supported are null if any extension is supported.
     */
    extensions: String? = null,
) : ExchangeAction {

    val rootDispatchPath: String = rootDispatchPath?.ifBlank { null }?.let { "/${it.removePrefix("/").removeSuffix("/").trim()}/" } ?: "/"

    val supportedExtensions = extensions?.ifBlank { null }?.split(" ")?.map { ".${it.trim().removePrefix(".")}" }?.filter { it.length > 1 }

    override fun invoke(exchange: Exchange): Any? {
        if (exchange.getRequestOrRouteAttribute(NoForward) == true) {
            return null
        }

        val forwardPath = exchange.actionResult?.toString()
        if (!forwardPath.isNullOrBlank()) {
            if (forwardPath.contains("..")) {
                return null
            }
            if (supportedExtensions.isNullOrEmpty() || supportedExtensions.any { forwardPath.endsWith(it) }) {
                val dispatchPath = if (forwardPath.startsWith(rootDispatchPath)) {
                    forwardPath
                } else {
                    "$rootDispatchPath${forwardPath.removePrefix("/")}"
                }

                adapter.forwardTo(dispatchPath, exchange)
            }
        }
        return null
    }

    companion object {
        val NoForward = "${ForwardRequest::class.simpleName}:NoForward"

        val NoForwardAttribute = NoForward to true
    }
}