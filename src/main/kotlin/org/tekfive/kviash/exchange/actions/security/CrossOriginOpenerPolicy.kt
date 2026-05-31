package org.tekfive.kviash.exchange.actions.security

enum class CrossOriginOpenerPolicy(val headerValue: String) {
    UNSAFE_NONE("unsafe-none"),
    SAME_ORIGIN_ALLOW_POPUPS("same-origin-allow-popups"),
    SAME_ORIGIN("same-origin"),
}
