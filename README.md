# KViash

KViash (pronounced k-VEE-ash) stands for **K**otlin **via** **S**ynchronous **H**TTP.

A lightweight HTTP routing library for Kotlin that maps HTTP requests to Kotlin functions using a concise DSL. It provides automatic HTTP method inference, path parameter injection, and a composable pipeline of interceptors and actions. Adapters for Jakarta EE servlets, javax servlets, Jetty 12 native handlers, and Undertow are included, but the core depends only on simple request/response interfaces that can be implemented for any HTTP runtime.

## Design Priorities

### Minimal Impact on Consumer

KViash asks very little of the code that uses it. There are no base classes to extend, no interfaces to implement on your controllers, no lifecycle annotations, and no container to bootstrap. Route handlers are ordinary Kotlin functions with whatever parameters make sense for the work they do. KViash inspects each function's signature at registration time and automatically injects the right values — path segments, the request, the session, custom types — so your functions stay focused on business logic rather than HTTP plumbing.

A route handler can be a plain function that takes an `Int` and a `String` and returns a result. It can be tested by calling it directly, reused outside of a web context, and refactored with standard Kotlin tooling. The core depends only on two interfaces (`HttpRequestSource` and `HttpResponseSource`), so it integrates into an existing servlet application, a custom embedded server, or anything else that can provide an HTTP request and accept an HTTP response. Adopting KViash doesn't restructure your project — it fits into what you already have.

### No Annotations — Explicit Route Registration

Routes are registered in code using a DSL, not discovered through classpath scanning or annotation processing. Every route in your application is defined in one place, and you can read that code to understand exactly what URLs map to what functions.

```kotlin
RouteTable.register {
    with(path = "/api/users") {
        add(controller::getAll)
        add("/{}", controller::getById)
        add(controller::postCreate)
    }
}
```

Annotation-based routing scatters route definitions across dozens of controller classes. Understanding the full URL space of the application requires tooling or searching the entire codebase. With KViash, the route table is the single source of truth — readable, diffable, and refactorable with standard Kotlin tooling. Registration is ordinary code, so you can use conditionals, loops, or any other language construct to build your route table dynamically.

### Synchronous Processing

KViash assumes synchronous, blocking HTTP processing. A request arrives, flows through the interceptor and action pipeline on a single thread, and returns a response. There is no reactive API, no `suspend` functions, no callback chains, and no `Mono<T>` wrappers.

This is a deliberate choice for simplicity and readability. Synchronous code is straightforward to write, debug, and reason about. Stack traces are meaningful. Exceptions propagate naturally. Thread-local context works as expected. There is no colored function problem — any code your application already has can be called directly from a route handler without adaptation.

This approach is practical today because of Project Loom. Virtual threads (available since Java 21) allow the JVM to run millions of concurrent blocking operations without the thread-per-request scalability ceiling that historically motivated reactive frameworks. When your servlet container runs on virtual threads, synchronous KViash handlers get the concurrency benefits of asynchronous code with none of the complexity. The blocking style that was once a scalability concern is now a scalability advantage — simple code that scales.

### Content-Type Agnostic

KViash does not assume your server is a JSON API. Route handlers return whatever your application needs — HTML pages, JSON responses, XML documents, binary files, or nothing at all. There is no default serialization, no built-in content negotiation, and no response type that gets special treatment.

Post-actions like `SendActionJSON`, `SendActionHTML`, and `SendActionXML` set the appropriate `Content-Type` header when you want them, but they are opt-in per route scope, not a global default. The `LayoutsInterceptor` wraps HTML responses in layout templates and handles AJAX partial rendering. The `ForwardRequest` post-action dispatches to JSP or other server-side view technologies. All of these compose with each other through the pipeline — an API scope can use `SendActionJSON` while a web scope in the same application uses `ForwardRequest` with layouts.

This design works equally well for traditional server-rendered web applications, JSON APIs, hypermedia-driven applications (htmx, Turbo), or any combination in a single codebase.

## Requirements

- Java 21+
- Kotlin 2.x

## Installation

Gradle dependency:

```kotlin
implementation("org.tekfive:kviash:1.0.0")
```

## Development

Run the test suite:

```bash
./gradlew test
```

Publish to the local Maven repository:

```bash
./gradlew publishToMavenLocal
```

## Quick Start

### 1. Define a Controller

Controllers must be classes with a no-arg constructor or object singletons. Function names that start with an HTTP method name (`get`, `post`, `put`, `delete`, etc.) are automatically mapped to that method.

```kotlin
class UsersController {
    fun getAll(): String = """{"users": []}"""

    fun getById(id: Int): String = """{"id": $id}"""

    fun postCreate(request: HttpRequest): String {
        // handle creation
        return """{"created": true}"""
    }
}
```

Alternatively, you can implement the `ExchangeAction` functional interface directly:

```kotlin
val healthCheck = ExchangeAction { exchange ->
    exchange.response.setContentType("application/json")
    exchange.response.outputWriter.write("""{"status": "ok"}""")
    null
}
```

`ExchangeAction` lambdas are registered with an explicit HTTP method and optional path:

```kotlin
RouteTable.register {
    add("/health", HttpMethod.GET, action = healthCheck)
}
```

### 2. Register Routes

Routes are registered using `RouteTable.register`. The DSL supports nested scopes, explicit paths, path parameters, and lambda actions.

```kotlin
val controller = UsersController()

RouteTable.register {
    with(path = "/api/users") {
        add(controller::getAll)              // GET /api/users
        add("/{}", controller::getById)      // GET /api/users/{id}
        add(controller::postCreate)          // POST /api/users
    }
}
```

### 3. Connect to a Servlet Container

KViash provides servlet adapters for both Jakarta EE (6.0+) and legacy javax (4.x).

**Jakarta EE (Tomcat 10+, Jetty 12+):**

```xml
<servlet>
    <servlet-name>kviash</servlet-name>
    <servlet-class>org.tekfive.kviash.exchange.actions.adapters.servlet.jakarta.JakartaServletExchangeAdapter</servlet-class>
</servlet>
<servlet-mapping>
    <servlet-name>kviash</servlet-name>
    <url-pattern>/*</url-pattern>
</servlet-mapping>
```

**javax Servlet (Tomcat 9, Jetty 11):**

```xml
<servlet>
    <servlet-name>kviash</servlet-name>
    <servlet-class>org.tekfive.kviash.exchange.actions.adapters.servlet.javax.JavaxServletExchangeAdapter</servlet-class>
</servlet>
<servlet-mapping>
    <servlet-name>kviash</servlet-name>
    <url-pattern>/*</url-pattern>
</servlet-mapping>
```

Route registration must happen before the first request. A `ServletContextListener` is a good place:

```kotlin
class AppInitializer : ServletContextListener {
    override fun contextInitialized(event: ServletContextEvent) {
        val controller = UsersController()
        RouteTable.register {
            with(path = "/api/users") {
                add(controller::getAll)
                add("/{}", controller::getById)
                add(controller::postCreate)
            }
        }
    }
}
```

### 4. Connect to Jetty (Native Handler API)

KViash can run directly on Jetty 12's native `Handler` API without the servlet layer. This uses Jetty's lightweight handler pipeline instead of `HttpServlet`.

The KViash artifact includes the Jetty native adapter dependencies.

**Setting up the server:**

```kotlin
import org.eclipse.jetty.server.Server
import org.tekfive.kviash.exchange.actions.adapters.jetty.JettyHandlerExchangeAdapter
import org.tekfive.kviash.routing.RouteTable

fun main() {
    RouteTable.register {
        with(path = "/api/users") {
            add(controller::getAll)
            add("/{}", controller::getById)
            add(controller::postCreate)
        }
    }

    val server = Server(8080)
    server.handler = JettyHandlerExchangeAdapter()
    server.start()
}
```

`JettyHandlerExchangeAdapter` accepts an optional `routeNames` parameter for named route tables, just like the servlet adapters:

```kotlin
server.handler = JettyHandlerExchangeAdapter(routeNames = listOf("api"))
```

**Request forwarding with Jetty:**

In servlet containers, `ForwardRequest` uses `RequestDispatcher.forward()`. With Jetty native, forwarding dispatches to a `Handler` you provide. The `JettyForwardAdapter` wraps the original request with a modified URI path and invokes the target handler:

```kotlin
import org.tekfive.kviash.exchange.actions.ForwardRequest
import org.tekfive.kviash.exchange.actions.adapters.jetty.JettyForwardAdapter

val viewHandler: Handler = // handler that serves your templates/views
val forwardAdapter = JettyForwardAdapter(viewHandler)

RouteTable.register(postActionsBefore = listOf(ForwardRequest(forwardAdapter))) {
    add(controller::getPage) // returned "page.html" is forwarded to viewHandler
}
```

**Serving static resources with Jetty:**

`JettyResourceProvider` adapts Jetty's `Resource` API to serve static files, analogous to `JakartaResourceProvider` for servlet contexts:

```kotlin
import org.eclipse.jetty.util.resource.ResourceFactory
import org.tekfive.kviash.exchange.actions.static.StaticResources
import org.tekfive.kviash.exchange.actions.static.adapters.JettyResourceProvider

val baseResource = ResourceFactory.root().newResource(Path.of("/var/www/static"))
val staticResources = StaticResources(
    resourceProvider = JettyResourceProvider(baseResource),
    enableETag = true,
)

RouteTable.register {
    add("/static/{**}", setOf(HttpMethod.HEAD, HttpMethod.GET), staticResources)
}
```

### 5. Connect to Undertow

KViash can run directly on Undertow's `HttpHandler` API. Since KViash uses synchronous processing, the adapter automatically dispatches from Undertow's IO thread to a worker thread and enables blocking mode.

The KViash artifact includes the Undertow adapter dependencies.

**Setting up the server:**

```kotlin
import io.undertow.Undertow
import org.tekfive.kviash.exchange.actions.adapters.undertow.UndertowHandlerExchangeAdapter
import org.tekfive.kviash.routing.RouteTable

fun main() {
    RouteTable.register {
        with(path = "/api/users") {
            add(controller::getAll)
            add("/{}", controller::getById)
            add(controller::postCreate)
        }
    }

    val server = Undertow.builder()
        .addHttpListener(8080, "0.0.0.0")
        .setHandler(UndertowHandlerExchangeAdapter())
        .build()
    server.start()
}
```

`UndertowHandlerExchangeAdapter` accepts an optional `routeNames` parameter for named route tables:

```kotlin
.setHandler(UndertowHandlerExchangeAdapter(routeNames = listOf("api")))
```

**Request forwarding with Undertow:**

`UndertowForwardAdapter` sets the relative path on the `HttpServerExchange` and dispatches to a target `HttpHandler`:

```kotlin
import org.tekfive.kviash.exchange.actions.ForwardRequest
import org.tekfive.kviash.exchange.actions.adapters.undertow.UndertowForwardAdapter

val viewHandler: HttpHandler = // handler that serves your templates/views
val forwardAdapter = UndertowForwardAdapter(viewHandler)

RouteTable.register(postActionsBefore = listOf(ForwardRequest(forwardAdapter))) {
    add(controller::getPage) // returned "page.html" is forwarded to viewHandler
}
```

**Serving static resources with Undertow:**

`UndertowResourceProvider` adapts Undertow's `ResourceManager` to serve static files:

```kotlin
import io.undertow.server.handlers.resource.PathResourceManager
import org.tekfive.kviash.exchange.actions.static.StaticResources
import org.tekfive.kviash.exchange.actions.static.adapters.UndertowResourceProvider

val resourceManager = PathResourceManager(Path.of("/var/www/static"))
val staticResources = StaticResources(
    resourceProvider = UndertowResourceProvider(resourceManager),
    enableETag = true,
)

RouteTable.register {
    add("/static/{**}", setOf(HttpMethod.HEAD, HttpMethod.GET), staticResources)
}
```

## Route Registration

### HTTP Method Inference

Function names that begin with an HTTP method name are automatically bound to that method:

```kotlin
fun getUsers()     // -> GET
fun postUser()     // -> POST
fun putUser()      // -> PUT
fun deleteUser()   // -> DELETE
```

Functions whose names don't match any HTTP method are mapped to all methods.

You can also bind explicitly:

```kotlin
add(HttpMethod.GET, controller::handleRequest)
add(setOf(HttpMethod.GET, HttpMethod.POST), action = { exchange -> null })
```

### Path Parameters

Use `{}` for inferred path parameters. The parameter type is derived from the function signature:

```kotlin
fun getUser(id: Int): String = ...      // GET /users/{} — matches integer segments
fun getByName(name: String): String = ... // GET /users/{} — matches any segment
```

Use `{regex}` for explicit patterns:

```kotlin
add("/{\\d{4}}", controller::getByYear)  // matches exactly 4 digits
```

Use `{*}` for wildcard (any single segment) and `{**}` for gobbler (matches all remaining segments):

```kotlin
add("/files/{*}", controller::getFile)      // matches /files/anything
add("/assets/{**}", controller::getAsset)   // matches /assets/any/number/of/segments
```

### Scoped Routes

The `with` block groups routes under a common path, and allows shared interceptors, pre/post actions, configuration, and attributes:

```kotlin
RouteTable.register {
    with(path = "/api/v1") {
        with(path = "/users") {
            add(controller::getAll)
            add("/{}", controller::getById)
        }
        with(path = "/orders") {
            add(orderController::getAll)
        }
    }
}
```

### Multiple Route Tables

You can register multiple named route tables. The servlet adapter can be configured to use specific tables via the `RouteNames` init parameter:

```kotlin
RouteTable.register(name = "api") {
    with(path = "/api") { ... }
}

RouteTable.register(name = "web") {
    with(path = "/") { ... }
}
```

```xml
<servlet>
    <servlet-name>api</servlet-name>
    <servlet-class>...JakartaServletExchangeAdapter</servlet-class>
    <init-param>
        <param-name>RouteNames</param-name>
        <param-value>api</param-value>
    </init-param>
</servlet>
```

### Lambda Actions

Routes can use lambda actions instead of function references:

```kotlin
add("/health", HttpMethod.GET) { exchange ->
    exchange.response.setContentType("application/json")
    exchange.response.outputWriter.write("""{"status": "ok"}""")
    null
}
```

## Pipeline Architecture

Each routed request flows through a pipeline:

```
Interceptors -> Pre-Actions -> Action -> Post-Actions
```

- **Interceptors** wrap the entire pipeline and can modify the exchange before and after processing (e.g., layouts, compression).
- **Pre-Actions** run before the main action (e.g., authentication, logging).
- **Action** is the route handler function or lambda.
- **Post-Actions** run after the action (e.g., sending the response, redirects).

### Interceptors

Interceptors implement `PipelineInterceptor` and wrap the pipeline execution:

```kotlin
class AuthInterceptor : PipelineInterceptor {
    override fun intercept(exchange: Exchange, continuePipeline: (Exchange) -> Unit) {
        if (isAuthenticated(exchange)) {
            continuePipeline(exchange)
        } else {
            exchange.response.sendStatus(401)
        }
    }
}
```

Apply interceptors to a route scope:

```kotlin
RouteTable.register(interceptors = listOf(AuthInterceptor())) {
    add(controller::getProtectedResource)
}
```

### Built-in Interceptors

**GZipResponseInterceptor** compresses response bodies with gzip:

```kotlin
RouteTable.register(interceptors = listOf(GZipResponseInterceptor())) {
    add(controller::getData)
}
```

**LayoutsInterceptor** wraps response content in a layout template, extracting outermost HTML elements (`<head>`, `<body>`, `<title>`, etc.) as named fragments accessible via `LayoutContext`:

```kotlin
val layoutsInterceptor = LayoutsInterceptor(
    adapter = myLayoutsAdapter,
    defaultDocumentTemplateName = "application",
    defaultPartialTemplateName = "partial"
)

RouteTable.register(interceptors = listOf(layoutsInterceptor)) {
    add(controller::getPage)
}
```

The interceptor automatically detects AJAX/partial requests (via `Sec-Fetch-Dest`, `X-Requested-With`, `HX-Request`, `Turbo-Frame`, etc.) and selects the appropriate template.

### Built-in Post-Actions

**SendActionResult** sends the action's return value as the HTTP response body:

```kotlin
RouteTable.register(postActionsBefore = listOf(SendActionJSON)) {
    add(controller::getUsers) // returned String is sent as application/json
}
```

Variants: `SendActionJSON`, `SendActionXML`, `SendActionHTML`.

**ForwardRequest** forwards the action's return value as a dispatch path to the servlet container:

```kotlin
RouteTable.register(postActionsBefore = listOf(ForwardRequest(adapter))) {
    add(controller::getPage) // returned "page.jsp" is forwarded
}
```

**RedirectRequest** sends a redirect when the action returns a `redirect:` prefixed path or a `KFunction` reference:

```kotlin
RouteTable.register(postActionsBefore = listOf(RedirectRequest())) {
    add(controller::postForm) // return "redirect:/success" sends 303
}
```

## Function Parameter Injection

Route functions can declare parameters that KViash automatically injects:

| Parameter Type | Injected Value |
|---|---|
| `Exchange` | The current exchange |
| `HttpRequest` | The HTTP request |
| `HttpResponse` | The HTTP response |
| `HttpRequestPath` | The parsed request path |
| `HttpRequestParameters` | The request parameters |
| `HttpMethod` | The HTTP method |
| `HttpSession` | The session (nullable = don't create) |
| `URL` | The full request URL |
| `Int`, `Long`, `String`, etc. | Path segment values |

```kotlin
fun getUser(id: Int, request: HttpRequest): String {
    val host = request.host
    return """{"id": $id, "host": "$host"}"""
}
```

### Custom Parameter Providers

Register custom types to be injected into route functions:

```kotlin
val registry = CustomParameterRegistry()
registry.registerProvider(CurrentUser::class) { exchange ->
    exchange.request.getSession()?.getAttribute("user") as? CurrentUser
}

RouteTable.register(customParameterRegistry = registry) {
    add(controller::getDashboard) // fun getDashboard(user: CurrentUser): String
}
```

## Error Handling

Register error handlers at any scope level:

```kotlin
RouteTable.register {
    with(path = "/api") {
        add(controller::getUsers)

        onError(HttpErrorCode.NOT_FOUND, controller::handleNotFound)
        onError(HttpErrorCode.INTERNAL_SERVER_ERROR, controller::handleError)
        onAnyError(controller::handleAnyError)
    }
}
```

Error handlers are inherited — a handler registered at a parent scope applies to all child routes unless a more specific handler exists at a deeper level.

## Configuration

KViash uses [ACK](../ack) (Kotlin Application Configuration Kit) for its default configuration values. Each property can be set through any ACK source (environment variables, AWS Secrets Manager, AWS SSM Parameter Store, HashiCorp Vault, etc.).

### Properties

| Property Name | Default | Description |
|---|---|---|
| `KVSH_INPUT_BUFFER_SIZE` | `8192` | Input stream buffer size in bytes |
| `KVSH_OUTPUT_BUFFER_SIZE` | `8192` | Output stream buffer size in bytes |
| `KVSH_TRIM_PARAMETER_VALUES` | `true` | Trim whitespace from request parameter values |
| `KVSH_IGNORE_ROUTE_PATH_CASE` | `true` | Case-insensitive route matching |

For example, to configure via environment variables:

```bash
export KVSH_INPUT_BUFFER_SIZE=16384
export KVSH_TRIM_PARAMETER_VALUES=false
```

### Per-Scope Overrides

In addition to ACK-sourced defaults, configuration can be overridden per route scope using `ConfigurationOverride`. These overrides take precedence over the ACK-configured defaults:

```kotlin
RouteTable.register(
    configuration = ConfigurationOverride(
        trimParameterValues = false,
        ignoreRoutePathCase = false,
    )
) {
    add(controller::getCaseSensitiveRoute)
}
```

## URL Generation

Generate URLs for registered route functions within an exchange:

```kotlin
fun getUser(id: Int): String {
    val exchange = Exchange.getThreadLocalContext()!!
    val url = exchange.urlGenerator.getUrl("/users/{id}", 42)
    // -> "http://localhost/users/42"
    return """{"link": "$url"}"""
}
```

## Building

```bash
mvn clean install
```

## Running Tests

```bash
mvn test
```

## License

See LICENSE file for details.
