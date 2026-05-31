package org.tekfive.kviash.routing

import org.tekfive.jfk.FromJsonObject
import org.tekfive.kviash.exchange.CustomParameterRegistry
import org.tekfive.kviash.http.HttpMethod
import org.tekfive.kviash.http.HttpRequest
import org.tekfive.kviash.http.HttpRequestContent
import org.tekfive.kviash.http.HttpRequestPath
import org.tekfive.kviash.http.HttpResponse
import org.tekfive.kviash.http.HttpSession
import org.tekfive.kviash.exchange.Exchange
import org.tekfive.kviash.http.HttpRequestParameters
import java.net.URL
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObjectInstance

/**
 * The type of parameters that can be declared in a route function.
 */
internal enum class RouteFunctionParameterType(val clazz: KClass<*>, val takenFromBodyContent: Boolean = false) {
    RouteExchangeType(Exchange::class),
    HttpRequestPathType(HttpRequestPath::class),
    HttpRequestParametersType(HttpRequestParameters::class),
    HttpRequestType(HttpRequest::class),
    HttpResponseType(HttpResponse::class),
    HttpSessionType(HttpSession::class),
    HttpRequestContentType(HttpRequestContent::class, takenFromBodyContent = true),
    HttpRequestParameterMapType(Map::class),
    JfkJsonObjectType(PlaceholderClass::class, takenFromBodyContent = true),
    JfkJsonArrayType(PlaceholderClass::class, takenFromBodyContent = true),
    JfkFromJsonObjectType(PlaceholderClass::class, takenFromBodyContent = true),
    UrlType(URL::class),
    HttpMethodType(HttpMethod::class),
    PrimitiveType(PrimitiveParameterType::class),
    RegisteredCustomType(PlaceholderClass::class),
    ;

    companion object {
        private const val JFK_JSON_OBJECT = "org.tekfive.jfk.JsonObject"
        private const val JFK_JSON_ARRAY = "org.tekfive.jfk.JsonArray"

        fun fromClass(clazz: KClass<*>, customParameterRegistry: CustomParameterRegistry?): RouteFunctionParameterType? {
            val type = entries.firstOrNull { it.clazz == clazz }
            if (type != null) {
                return type
            }

            val qualifiedName = clazz.qualifiedName
            if (qualifiedName == JFK_JSON_OBJECT) {
                return JfkJsonObjectType
            }
            if (qualifiedName == JFK_JSON_ARRAY) {
                return JfkJsonArrayType
            }
            if (PrimitiveParameterType.fromClass(clazz) != null) {
                return PrimitiveType
            }
            if (clazz.companionObjectInstance is FromJsonObject<*>) {
                return JfkFromJsonObjectType
            }
            if (customParameterRegistry != null && customParameterRegistry.isRegistered(clazz)) {
                return RegisteredCustomType
            }

            return null
        }
    }
}

/**
 * Placeholder to satisfy the non-null KClass constraint for enum entries that detect types by qualified name at runtime.
 */
private class PlaceholderClass {}
