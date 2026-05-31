package org.tekfive.kviash.http

interface HttpSession {
    val id: String?

    val isNew: Boolean

    val creationTime: Long?

    val lastAccessedTime: Long?

    val attributeNames: List<String>

    fun getAttribute(key: String): Any?

    fun setAttribute(key: String, value: Any?)

    fun removeAttribute(key: String)

    fun invalidate()
}