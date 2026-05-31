package org.tekfive.kviash.exchange.actions.security

enum class CrossOriginResourcePolicy(val headerValue: String) {
    SAME_SITE("same-site"),
    SAME_ORIGIN("same-origin"),
    CROSS_ORIGIN("cross-origin"),
}
