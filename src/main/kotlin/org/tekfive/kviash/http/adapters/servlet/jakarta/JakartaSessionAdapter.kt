package org.tekfive.kviash.http.adapters.servlet.jakarta

import org.tekfive.kviash.http.HttpSession

class JakartaSessionAdapter(val session: jakarta.servlet.http.HttpSession) : org.tekfive.kviash.http.HttpSession {
    override val id: String?
        get() = session.id

    override val isNew: Boolean
        get() = session.isNew

    override val creationTime: Long
        get() = session.creationTime

    override val lastAccessedTime: Long
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
        session.invalidate()
    }
}