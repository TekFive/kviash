package org.tekfive.kviash.exchange.actions.adapters

import org.tekfive.kviash.exchange.Exchange

interface ForwardAdapter {
    fun forwardTo(path: String, exchange: Exchange)
}