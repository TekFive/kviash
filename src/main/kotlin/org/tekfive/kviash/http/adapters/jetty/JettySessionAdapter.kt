package org.tekfive.kviash.http.adapters.jetty

import org.eclipse.jetty.server.Session as JettySession
import org.tekfive.kviash.http.HttpSession

class JettySessionAdapter(val session: JettySession) : HttpSession {
    override val id: String?
        get() = session.id

    override val isNew: Boolean
        get() = session.isNew

    override val creationTime: Long?
        get() = null // Jetty's Session interface does not expose creation time

    override val lastAccessedTime: Long?
        get() = session.lastAccessedTime

    override val attributeNames: List<String>
        get() = session.attributeNameSet.toList()

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
        session.invalidate()
    }
}
