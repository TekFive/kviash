package org.tekfive.kviash.routing

import org.tekfive.kviash.ConfigurationOverride
import org.tekfive.kviash.DefaultKviashConfiguration
import org.tekfive.kviash.KviashConfiguration
import org.tekfive.kviash.StackableConfiguration
import org.tekfive.kviash.exchange.CustomParameterRegistry
import org.tekfive.kviash.http.HttpMethod
import org.tekfive.kviash.http.toPath
import org.tekfive.kviash.http.toPathSegments
import org.tekfive.kviash.exchange.ExchangeAction
import org.tekfive.kviash.exchange.ExchangeFunction
import org.tekfive.kviash.exchange.ExchangePipeline
import org.tekfive.kviash.exchange.HttpErrorCode
import org.tekfive.kviash.exchange.interceptors.PipelineInterceptor
import kotlin.reflect.KFunction

class RouteTable private constructor(
    val name: String,
    val ignoreTrailingSlash: Boolean,
    val enableRouteCache: Boolean,
    val maxCacheSize: Int?,
) {
    private val rootTreeNode = RouteTree(ignoreTrailingSlash, enableRouteCache, maxCacheSize)

    private val routeScopeStack = mutableListOf<RouteScope>()

    internal fun build(): RouteTree {
        return rootTreeNode.copy()
    }

    fun with(
        path: String? = null,

        interceptor: PipelineInterceptor? = null,
        interceptors: List<PipelineInterceptor> = emptyList(),

        preFunctionBefore: KFunction<*>? = null,
        preFunctionsBefore: List<KFunction<*>> = emptyList(),
        preFunction: KFunction<*>? = null,
        preFunctions: List<KFunction<*>> = emptyList(),
        preActionBefore: ExchangeAction? = null,
        preActionsBefore: List<ExchangeAction> = emptyList(),
        preAction: ExchangeAction? = null,
        preActions: List<ExchangeAction> = emptyList(),

        postFunctionBefore: KFunction<*>? = null,
        postFunctionsBefore: List<KFunction<*>> = emptyList(),
        postFunction: KFunction<*>? = null,
        postFunctions: List<KFunction<*>> = emptyList(),
        postActionBefore: ExchangeAction? = null,
        postActionsBefore: List<ExchangeAction> = emptyList(),
        postAction: ExchangeAction? = null,
        postActions: List<ExchangeAction> = emptyList(),

        customParameterRegistry: CustomParameterRegistry? = null,
        configuration: ConfigurationOverride? = null,
        routeAttributes: List<Pair<String, Any>> = emptyList(),
        urlPlugin: UrlPlugin? = null,

        block: (RouteTable.() -> Unit)): RouteTable {

        val parentScope = routeScopeStack.firstOrNull()
        val customRouteParameterRegistry = customParameterRegistry ?: parentScope?.customParameterRegistry

        val interceptors = listOfNotNull(interceptor) + interceptors

        val preActionsBefore = (listOfNotNull(preActionBefore) + preActionsBefore).toMutableList()
        preActionsBefore.addAll(0, (listOfNotNull(preFunctionBefore) + preFunctionsBefore).map { ExchangeFunction(it, customRouteParameterRegistry) })

        val preActions = (listOfNotNull(preAction) + preActions).toMutableList()
        preActions.addAll(0, (listOfNotNull(preFunction) + preFunctions).map { ExchangeFunction(it, customRouteParameterRegistry) })

        val postActionsBefore = (listOfNotNull(postActionBefore) + postActionsBefore).toMutableList()
        postActionsBefore.addAll(0, (listOfNotNull(postFunctionBefore) + postFunctionsBefore).map { ExchangeFunction(it, customRouteParameterRegistry) })

        val postActions = (listOfNotNull(postAction) + postActions).toMutableList()
        postActions.addAll(0, (listOfNotNull(postFunction) + postFunctions).map { ExchangeFunction(it, customRouteParameterRegistry) })

        val exchange = RouteScope(
            configuration,
            path,
            interceptors,
            preActionsBefore,
            preActions,
            postActionsBefore,
            postActions,
            customRouteParameterRegistry,
            routeAttributes.associate { it.first to it.second },
            parentScope,
            this,

        )
        routeScopeStack.add(0, exchange)

        if (urlPlugin != null) {
            if (urlPlugin is RouteRegistrationAware) {
                urlPlugin.onRouteRegistered(exchange.pathSegments.toPath())
            }
        }

        try {
            block()
        } finally {
            routeScopeStack.removeFirst()
        }
        return this
    }

    fun add(method: HttpMethod, function: KFunction<*>, vararg attributes: Pair<String, Any?>, acceptTypes: Set<String> = emptySet()): RouteTable {
        addPipelines(methods = setOf(method), functions = listOf(function), attributes = attributes, acceptTypes = acceptTypes)
        return this
    }

    fun add(function: KFunction<*>, vararg attributes: Pair<String, Any?>): RouteTable {
        addPipelines(methods = toMethods(listOf(function)), functions = listOf(function), attributes = attributes)
        return this
    }

    fun add(function1: KFunction<*>, function2: KFunction<*>, vararg attributes: Pair<String, Any?>): RouteTable {
        addPipelines(methods = toMethods(listOf(function1)), functions = listOf(function1), attributes = attributes)
        addPipelines(methods = toMethods(listOf(function2)), functions = listOf(function2), attributes = attributes)
        return this
    }

    fun add(function1: KFunction<*>, function2: KFunction<*>, function3: KFunction<*>, vararg attributes: Pair<String, Any?>): RouteTable {
        addPipelines(methods = toMethods(listOf(function1)), functions = listOf(function1), attributes = attributes)
        addPipelines(methods = toMethods(listOf(function2)), functions = listOf(function2), attributes = attributes)
        addPipelines(methods = toMethods(listOf(function3)), functions = listOf(function3), attributes = attributes)
        return this
    }

    fun add(function1: KFunction<*>, function2: KFunction<*>, function3: KFunction<*>, function4: KFunction<*>, vararg attributes: Pair<String, Any?>): RouteTable {
        addPipelines(methods = toMethods(listOf(function1)), functions = listOf(function1), attributes = attributes)
        addPipelines(methods = toMethods(listOf(function2)), functions = listOf(function2), attributes = attributes)
        addPipelines(methods = toMethods(listOf(function3)), functions = listOf(function3), attributes = attributes)
        addPipelines(methods = toMethods(listOf(function4)), functions = listOf(function4), attributes = attributes)
        return this
    }

    fun add(routePath: String, method: HttpMethod, function: KFunction<*>, vararg attributes: Pair<String, Any?>, acceptTypes: Set<String> = emptySet()): RouteTable {
        addPipelines(routePath = routePath, methods = setOf(method), functions = listOf(function), attributes = attributes, acceptTypes = acceptTypes)
        return this
    }

    fun add(routePath: String, function: KFunction<*>, vararg attributes: Pair<String, Any?>): RouteTable {
        addPipelines(routePath = routePath, methods = toMethods(listOf(function)), functions = listOf(function), attributes = attributes)
        return this
    }

    fun add(routePath: String, function1: KFunction<*>, function2: KFunction<*>, vararg attributes: Pair<String, Any?>): RouteTable {
        addPipelines(routePath = routePath, methods = toMethods(listOf(function1)), functions = listOf(function1), attributes = attributes)
        addPipelines(routePath = routePath, methods = toMethods(listOf(function2)), functions = listOf(function2), attributes = attributes)
        return this
    }

    fun add(routePath: String, function1: KFunction<*>, function2: KFunction<*>, function3: KFunction<*>, vararg attributes: Pair<String, Any?>): RouteTable {
        addPipelines(routePath = routePath, methods = toMethods(listOf(function1)), functions = listOf(function1), attributes = attributes)
        addPipelines(routePath = routePath, methods = toMethods(listOf(function2)), functions = listOf(function2), attributes = attributes)
        addPipelines(routePath = routePath, methods = toMethods(listOf(function3)), functions = listOf(function3), attributes = attributes)
        return this
    }

    fun add(routePath: String, function1: KFunction<*>, function2: KFunction<*>, function3: KFunction<*>, function4: KFunction<*>, vararg attributes: Pair<String, Any?>): RouteTable {
        addPipelines(routePath = routePath, methods = toMethods(listOf(function1)), functions = listOf(function1), attributes = attributes)
        addPipelines(routePath = routePath, methods = toMethods(listOf(function2)), functions = listOf(function2), attributes = attributes)
        addPipelines(routePath = routePath, methods = toMethods(listOf(function3)), functions = listOf(function3), attributes = attributes)
        addPipelines(routePath = routePath, methods = toMethods(listOf(function4)), functions = listOf(function4), attributes = attributes)
        return this
    }

    fun add(method: HttpMethod, vararg attributes: Pair<String, Any?>, acceptTypes: Set<String> = emptySet(), action: ExchangeAction): RouteTable {
        addPipelines(methods = setOf(method), actions = listOf(action), attributes = attributes, acceptTypes = acceptTypes)
        return this
    }

    fun add(methods: Set<HttpMethod>, vararg attributes: Pair<String, Any?>, acceptTypes: Set<String> = emptySet(), action: ExchangeAction): RouteTable {
        addPipelines(methods = methods, actions = listOf(action), attributes = attributes, acceptTypes = acceptTypes)
        return this
    }

    fun add(routePath: String, action: ExchangeAction, vararg attributes: Pair<String, Any?>, acceptTypes: Set<String> = emptySet()): RouteTable {
        addPipelines(routePath = routePath, actions = listOf(action), attributes = attributes, acceptTypes = acceptTypes)
        return this
    }

    fun add(routePath: String, method: HttpMethod, action: ExchangeAction, vararg attributes: Pair<String, Any?>, acceptTypes: Set<String> = emptySet()): RouteTable {
        addPipelines(routePath = routePath, methods = setOf(method), actions = listOf(action), attributes = attributes, acceptTypes = acceptTypes)
        return this
    }

    fun add(routePath: String, methods: Set<HttpMethod>, action: ExchangeAction, vararg attributes: Pair<String, Any?>, acceptTypes: Set<String> = emptySet()): RouteTable {
        addPipelines(routePath = routePath, methods = methods, actions = listOf(action), attributes = attributes, acceptTypes = acceptTypes)
        return this
    }


    fun add(routePath: String, method: HttpMethod, vararg attributes: Pair<String, Any?>, acceptTypes: Set<String> = emptySet(), action: ExchangeAction): RouteTable {
        addPipelines(routePath = routePath, methods = setOf(method), actions = listOf(action), attributes = attributes, acceptTypes = acceptTypes)
        return this
    }


    fun add(routePath: String, methods: Set<HttpMethod>, vararg attributes: Pair<String, Any?>, acceptTypes: Set<String> = emptySet(), action: ExchangeAction): RouteTable {
        addPipelines(routePath = routePath, methods = methods, actions = listOf(action), attributes = attributes, acceptTypes = acceptTypes)
        return this
    }

    fun add(routePath: String, routeFunction: KFunction<*>, methods: Set<HttpMethod>, vararg attributes: Pair<String, Any?>, acceptTypes: Set<String> = emptySet()): RouteTable {
        addPipelines(routePath = routePath, functions = listOf(routeFunction), methods = methods, attributes = attributes, acceptTypes = acceptTypes)
        return this
    }


    fun notFound(function: KFunction<*>): RouteTable {
        val pipelines = createPipelines(functions = listOf(function))
        rootTreeNode.addNotFound(pipelines.first())
        return this
    }

    fun notFound(action: ExchangeAction): RouteTable {
        val pipelines = createPipelines(actions = listOf(action))
        rootTreeNode.addNotFound(pipelines.first())
        return this
    }

    override fun toString(): String {
        return rootTreeNode.toString()
    }

    private fun addPipelines(
        routePath: String? = null,
        methods: Set<HttpMethod> = emptySet(),
        functions: List<KFunction<*>> = emptyList(),
        actions: List<ExchangeAction> = emptyList(),
        attributes: Array<out Pair<String, Any?>> = emptyArray(),
        acceptTypes: Set<String> = emptySet(),
    ) {
        require(methods.isNotEmpty()) { "Only methods can be specified." }

        val pipelines = createPipelines(routePath, functions, actions, attributes, acceptTypes)
        for (pipeline in pipelines) {
            rootTreeNode.add(methods, pipeline)
        }
    }

    private fun createPipelines(
        routePath: String? = null,
        functions: List<KFunction<*>> = emptyList(),
        actions: List<ExchangeAction> = emptyList(),
        attributes: Array<out Pair<String, Any?>> = emptyArray(),
        acceptTypes: Set<String> = emptySet(),
    ): List<ExchangePipeline> {

        require(functions.isNotEmpty() || actions.isNotEmpty()) { "Either route function or action must be provided." }

        val scope = routeScopeStack.firstOrNull()
        val pathSegments = mutableListOf<String>()
        if (scope != null) {
            pathSegments.addAll(scope.pathSegments)
        }

        val configuration = scope?.configuration ?: DefaultKviashConfiguration

        if (routePath != null) {
            pathSegments.addAll(routePath.toPathSegments(ignoreTrailingSlash))
        }

        val route = pathSegments.toPath()

        for (action in actions) {
            if (action is RouteRegistrationAware) {
                action.onRouteRegistered(route)
            }
        }

        val parsedPath = RoutePath(route.toPathSegments(ignoreTrailingSlash))

        val interceptors = scope?.interceptors ?: emptyList()
        val preActions = scope?.preActions ?: emptyList()
        val postActions = scope?.postActions ?: emptyList()
        val customRouteParameterRegistry = scope?.customParameterRegistry

        val routeAttributes = (scope?.routeAttributes ?: emptyMap()) + attributes.associate { it.first to it.second }

        val createdPipelines = mutableListOf<ExchangePipeline>()

        for (function in functions) {
            val exchangeFunction = ExchangeFunction(function, customRouteParameterRegistry)
            createdPipelines.add(
                ExchangePipeline(
                    configuration,
                    parsedPath,
                    interceptors,
                    preActions,
                    exchangeFunction,
                    postActions,
                    routeAttributes,
                    acceptTypes,
                )
            )
        }

        for (action in actions) {
            createdPipelines.add(
                ExchangePipeline(
                    configuration,
                    parsedPath,
                    interceptors,
                    preActions,
                    action,
                    postActions,
                    routeAttributes,
                    acceptTypes,
                )
            )
        }

        return createdPipelines
    }



    companion object {
        fun register(
            name: String = "default",
            ignoreTrailingSlash: Boolean = true,
            enableRouteCache: Boolean = true,
            maxCacheSize: Int? = null,

            interceptor: PipelineInterceptor? = null,
            interceptors: List<PipelineInterceptor> = emptyList(),

            preFunctionBefore: KFunction<*>? = null,
            preFunctionsBefore: List<KFunction<*>> = emptyList(),
            preFunction: KFunction<*>? = null,
            preFunctions: List<KFunction<*>> = emptyList(),
            preActionBefore: ExchangeAction? = null,
            preActionsBefore: List<ExchangeAction> = emptyList(),
            preAction: ExchangeAction? = null,
            preActions: List<ExchangeAction> = emptyList(),

            postFunctionBefore: KFunction<*>? = null,
            postFunctionsBefore: List<KFunction<*>> = emptyList(),
            postFunction: KFunction<*>? = null,
            postFunctions: List<KFunction<*>> = emptyList(),
            postActionBefore: ExchangeAction? = null,
            postActionsBefore: List<ExchangeAction> = emptyList(),
            postAction: ExchangeAction? = null,
            postActions: List<ExchangeAction> = emptyList(),

            customParameterRegistry: CustomParameterRegistry? = null,
            configuration: ConfigurationOverride? = null,
            routeAttributes: List<Pair<String, Any>> = emptyList(),
            urlPlugin: UrlPlugin? = null,

            block: (RouteTable.() -> Unit)
        ): RouteTable {
            val routeTable = RouteTable(name, ignoreTrailingSlash, enableRouteCache, maxCacheSize)
            routeTable.with(
                path = null,
                interceptor = interceptor,
                interceptors = interceptors,
                preFunctionBefore = preFunctionBefore,
                preFunctionsBefore = preFunctionsBefore,
                preFunction = preFunction,
                preFunctions = preFunctions,
                preActionBefore = preActionBefore,
                preActionsBefore = preActionsBefore,
                preAction = preAction,
                preActions = preActions,
                postFunctionBefore = postFunctionBefore,
                postFunctionsBefore = postFunctionsBefore,
                postFunction = postFunction,
                postFunctions = postFunctions,
                postActionBefore = postActionBefore,
                postActionsBefore = postActionsBefore,
                postAction = postAction,
                postActions = postActions,
                customParameterRegistry = customParameterRegistry,
                configuration = configuration,
                routeAttributes = routeAttributes,
                urlPlugin = urlPlugin
            ) {
                block(routeTable)
            }
            Router.register(name, routeTable.build())

            return routeTable
        }

        fun toMethods(functions: List<KFunction<*>>): Set<HttpMethod> {
            return functions.flatMap { getBestMethods(it.name) }.toSet()
        }

        fun getBestMethods(functionName: String): Set<HttpMethod> {
            for (method in HttpMethod.values()) {
                if (functionName.startsWith(method.name, true)) {
                    return setOf(method)
                }
            }

            return HttpMethod.values().toSet()
        }
    }

    private class RouteScope(
        val configuration: KviashConfiguration,
        val pathSegments: List<String>,
        val interceptors: List<PipelineInterceptor>,
        val preActions: List<ExchangeAction>,
        val postActions: List<ExchangeAction>,
        val customParameterRegistry: CustomParameterRegistry?,
        val routeAttributes: Map<String, Any?>,
    ) {


        companion object {
            operator fun invoke(
                configurationOverride: ConfigurationOverride?,
                path: String?,
                interceptors: List<PipelineInterceptor>,
                preActionsBefore: List<ExchangeAction>,
                preActions: List<ExchangeAction>,
                postActionsBefore: List<ExchangeAction>,
                postActions: List<ExchangeAction>,
                customParameterRegistry: CustomParameterRegistry?,
                routeAttributes: Map<String, Any?>,
                parentScope: RouteScope?,
                routesTable: RouteTable,
            ) : RouteScope {


                val parentScopeConfiguration = parentScope?.configuration ?: DefaultKviashConfiguration
                val configuration = if (configurationOverride != null) {
                    StackableConfiguration(configurationOverride, parentScopeConfiguration)
                } else {
                    parentScopeConfiguration
                }

                val pathSegments = mutableListOf<String>()
                if (parentScope != null) {
                    pathSegments.addAll(parentScope.pathSegments)
                }

                if (path != null) {
                    pathSegments.addAll(path.toPathSegments(routesTable.ignoreTrailingSlash))
                }

                val interceptors = interceptors + (parentScope?.interceptors ?: emptyList())
                val preprocessors = preActionsBefore + (parentScope?.preActions ?: emptyList()) + preActions
                val postprocessors = postActionsBefore + (parentScope?.postActions ?: emptyList()) + postActions

                val customRouteParameterRegistry = customParameterRegistry ?: parentScope?.customParameterRegistry


                val routeAttributes = (parentScope?.routeAttributes ?: emptyMap()) + routeAttributes

                return RouteScope(
                    configuration,
                    pathSegments,
                    interceptors,
                    preprocessors,
                    postprocessors,
                    customRouteParameterRegistry,
                    routeAttributes
                )
            }
        }
    }
}
