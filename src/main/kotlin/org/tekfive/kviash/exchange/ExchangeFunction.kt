package org.tekfive.kviash.exchange

import org.tekfive.jfk.Json
import org.tekfive.jfk.JsonArray
import org.tekfive.jfk.FromJsonObject
import org.tekfive.jfk.JsonObject
import org.tekfive.jfk.JsonMappingException
import org.tekfive.jfk.JsonParseException
import org.tekfive.kviash.routing.ExpressionParameterCoercionException
import org.tekfive.kviash.routing.PrimitiveParameterType
import org.tekfive.kviash.routing.RequiredRouteParameterUnavailableException
import org.tekfive.kviash.routing.RouteFunctionParameterType
import org.tekfive.kviash.routing.RoutesConfigurationException
import java.lang.IllegalArgumentException
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.valueParameters

internal class ExchangeFunction(
    val function: KFunction<*>,
    val action: ExchangeAction,
    val requiredPathParameterTypes: List<PrimitiveParameterType>,
) : ExchangeAction {

    override fun invoke(exchange: Exchange): Any? {
        return action(exchange)
    }

    companion object {
        operator fun invoke(function: KFunction<*>, customParameterRegistry: CustomParameterRegistry?): ExchangeFunction {
            val parameterProviders = mutableListOf<((parameters: MutableMap<KParameter, Any?>, exchange: Exchange, parameterSegmentIndices: MutableList<Int>) -> Any?)>()

            val instanceType = function.instanceParameter?.type?.classifier as KClass<*>?
            if (instanceType != null) {
                val noArgConstructor = instanceType.constructors.firstOrNull { it.parameters.isEmpty() }
                if (noArgConstructor == null) {
                    throw IllegalArgumentException("The class for the route function $function must have a no-arg constructor.")
                }
                val receiverParameter = function.parameters.first()
                parameterProviders.add {parameters, _, _ ->
                    parameters[receiverParameter] = noArgConstructor.call()
                }
            }

            val requiredPathParameters = mutableListOf<PrimitiveParameterType>()
            var requiredBodyContentCount = 0

            for (valueParameter in function.valueParameters) {
                val parameterClass = valueParameter.type.classifier as KClass<*>
                val routeParameterType = RouteFunctionParameterType.Companion.fromClass(parameterClass, customParameterRegistry)
                val optional = valueParameter.isOptional
                val nullable = valueParameter.type.isMarkedNullable

                if (routeParameterType != null && routeParameterType.takenFromBodyContent && !optional && !nullable) {
                    requiredBodyContentCount++
                    if (requiredBodyContentCount > 1) {
                        throw RoutesConfigurationException(
                            "Route function ${function.name} has multiple required body content parameters. Only one non-nullable, non-default body content parameter is allowed."
                        )
                    }
                }
                if (routeParameterType == null) {
                    if (optional) {
                        // Don't provide parameters for any optional parameters.
                    } else if (nullable) {
                        // Always supply null
                        parameterProviders.add { parameters, _, _ ->
                            parameters[valueParameter] = null
                        }
                    } else {
                        throw IllegalArgumentException("Unsupported parameter type in route function $this of type ${parameterClass.qualifiedName}.")
                    }
                } else {
                    when (routeParameterType) {
                        RouteFunctionParameterType.PrimitiveType -> {

                            if (nullable) {
                                parameterProviders.add { parameters, _, _ ->
                                    parameters[valueParameter] = null
                                }
                                continue
                            }

                            val fromPathType = PrimitiveParameterType.Companion.fromClass(parameterClass)!!
                            requiredPathParameters.add(fromPathType)

                            parameterProviders.add { parameters, exchange, parameterSegmentIndices ->
                                val segmentValue = exchange.requestPath[parameterSegmentIndices.removeFirst()]
                                val value = fromPathType.segmentToValue(segmentValue)
                                if (value == null) {
                                    // The only way this can happen at runtime is that an explicit regular expression was used that doesn't match the parameter type.
                                    throw ExpressionParameterCoercionException(
                                        segmentValue,
                                        function,
                                        valueParameter,
                                        "The route route function $function cannot be converted to the type ${fromPathType.clazz.simpleName} for parameter ${valueParameter.name}."
                                    )
                                }
                                parameters[valueParameter] = value
                            }
                        }

                        RouteFunctionParameterType.RouteExchangeType -> {
                            parameterProviders.add { parameters, exchange, _ ->
                                parameters[valueParameter] = exchange
                            }
                        }

                        RouteFunctionParameterType.HttpRequestType -> {
                            parameterProviders.add { parameters, exchange, _ ->
                                parameters[valueParameter] = exchange.request
                            }
                        }

                        RouteFunctionParameterType.UrlType -> {
                            parameterProviders.add { parameters, exchange, _ ->
                                parameters[valueParameter] = exchange.request.url
                            }
                        }

                        RouteFunctionParameterType.HttpRequestPathType -> {
                            parameterProviders.add { parameters, exchange, _ ->
                                parameters[valueParameter] = exchange.requestPath
                            }
                        }

                        RouteFunctionParameterType.HttpMethodType -> {
                            parameterProviders.add { parameters, exchange, _ ->
                                parameters[valueParameter] = exchange.request.method
                            }
                        }

                        RouteFunctionParameterType.HttpRequestParametersType -> {
                            parameterProviders.add { parameters, exchange, _ ->
                                parameters[valueParameter] = exchange.parameters
                            }
                        }

                        RouteFunctionParameterType.HttpRequestParameterMapType -> {
                            parameterProviders.add { parameters, exchange, _ ->
                                parameters[valueParameter] = exchange.request.parameters.associate { it.name to it.values }
                            }
                        }

                        RouteFunctionParameterType.HttpRequestContentType -> {
                            parameterProviders.add { parameters, exchange, _ ->
                                parameters[valueParameter] = exchange.request.content
                            }
                        }

                        RouteFunctionParameterType.JfkJsonObjectType -> {
                            parameterProviders.add { parameters, exchange, _ ->
                                val text = exchange.request.content?.text
                                val parsed = if (text.isNullOrBlank()) {
                                    null
                                } else {
                                    try {
                                        Json.parse(text) as? JsonObject
                                    } catch (_: JsonParseException) {
                                        null
                                    }
                                }
                                if (parsed == null && !optional && !nullable) {
                                    ReturnErrorStatus.onBadRequest("Request body is not a valid JSON object.")
                                }
                                if (parsed != null) {
                                    parameters[valueParameter] = parsed
                                } else if (nullable) {
                                    parameters[valueParameter] = null
                                }
                            }
                        }

                        RouteFunctionParameterType.JfkJsonArrayType -> {
                            parameterProviders.add { parameters, exchange, _ ->
                                val text = exchange.request.content?.text
                                val parsed = if (text.isNullOrBlank()) {
                                    null
                                } else {
                                    try {
                                        Json.parse(text) as? JsonArray
                                    } catch (_: JsonParseException) {
                                        null
                                    }
                                }
                                if (parsed == null && !optional && !nullable) {
                                    ReturnErrorStatus.onBadRequest("Request body is not a valid JSON array.")
                                }
                                if (parsed != null) {
                                    parameters[valueParameter] = parsed
                                } else if (nullable) {
                                    parameters[valueParameter] = null
                                }
                            }
                        }

                        RouteFunctionParameterType.JfkFromJsonObjectType -> {
                            parameterProviders.add(fromJsonProvider@ { parameters, exchange, _ ->
                                val text = exchange.request.content?.text
                                val parsed = if (text.isNullOrBlank()) {
                                    null
                                } else {
                                    try {
                                        Json.parse(text) as? JsonObject
                                    } catch (_: JsonParseException) {
                                        null
                                    }
                                }

                                if (parsed == null) {
                                    if (!optional && !nullable) {
                                        ReturnErrorStatus.onBadRequest("Request body is not a valid JSON object.")
                                    }
                                    if (nullable) {
                                        parameters[valueParameter] = null
                                    }
                                    return@fromJsonProvider null
                                }

                                val fromJson = parameterClass.companionObjectInstance as FromJsonObject<*>
                                try {
                                    parameters[valueParameter] = fromJson.fromJson(parsed)
                                } catch (_: JsonMappingException) {
                                    if (!optional && !nullable) {
                                        ReturnErrorStatus.onBadRequest("Request body does not match ${parameterClass.simpleName}.")
                                    }
                                    if (nullable) {
                                        parameters[valueParameter] = null
                                    }
                                }
                                null
                            })
                        }

                        RouteFunctionParameterType.HttpSessionType -> {
                            parameterProviders.add { parameters, exchange, _ ->
                                val session = exchange.request.getSession(!nullable)
                                if (session == null && !nullable) {
                                    throw RequiredRouteParameterUnavailableException(
                                        function,
                                        valueParameter,
                                    )
                                }
                                parameters[valueParameter] = session
                            }
                        }


                        RouteFunctionParameterType.HttpResponseType -> {
                            parameterProviders.add { parameters, exchange, _ ->
                                parameters[valueParameter] = exchange.response
                            }
                        }

                        RouteFunctionParameterType.RegisteredCustomType -> {
                            parameterProviders.add { parameters, exchange, _ ->
                                val value = customParameterRegistry?.getParameter(parameterClass, exchange)
                                if (value == null && !nullable) {
                                    throw RequiredRouteParameterUnavailableException(
                                        function,
                                        valueParameter,
                                    )
                                }
                                parameters[valueParameter] = value
                            }
                        }

                    }

                }

            }

            val action = { exchange: Exchange ->
                val parameters = mutableMapOf<KParameter, Any?>()
                val segmentIndices = exchange.pipeline.routeParameterSegmentIndices.toMutableList()
                for (parameterProvider in parameterProviders) {
                    parameterProvider(parameters, exchange, segmentIndices)
                }

                function.callBy(parameters)
            }

            return ExchangeFunction(function, action, requiredPathParameters)
        }
    }
}
