package org.tekfive.kviash.http.adapters.undertow

import io.undertow.server.HttpServerExchange
import io.undertow.server.session.Session
import org.tekfive.kviash.http.HttpSession

class UndertowSessionAdapter(
    private val session: Session,
    private val exchange: HttpServerExchange,
) : HttpSession {
    override val id: String?
        get() = session.id

    override val isNew: Boolean
        get() = false // Undertow's Session interface does not expose isNew

    override val creationTime: Long?
        get() = session.creationTime

    override val lastAccessedTime: Long?
        get() = session.lastAccessedTime

    override val attributeNames: List<String>
        get() = session.attributeNames.toList()

    override fun getAttribute(key: String): Any? {
        return session.getAttribute(key)
    }

    override fun setAttribute(key: String, value: Any?) {
        session.setAttribute(key, value)
    }

    override fun removeAttribute(key: String) {
        session.removeAttribute(key)
    }

    override fun invalidate() {
        session.invalidate(exchange)
    }
}
