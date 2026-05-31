package org.tekfive.kviash.exchange.actions.content

import org.tekfive.jfk.JsonArray
import org.tekfive.jfk.JsonObject
import org.tekfive.jfk.JsonValue
import org.tekfive.jfk.ToJsonObject

object SendJfkResponse : SendActionResult("application/json") {
    override fun convertToString(value: Any): String {
        return if (value is JsonValue) {
            value.toJsonString()
        } else if (value is ToJsonObject) {
            value.toJsonString()
        } else if (value is List<*> && value.all { (it is JsonValue) || (it is ToJsonObject) }) {
          JsonArray(value).toJsonString()
        } else {
            super.convertToString(value)
        }
    }
}