# lib-logging

Logging library for servlet Spring Boot applications, generated with `java-skeleton` (Gradle + `java-library`
plugin, Spring Boot BOM imported as a `platform()`; it is a plain jar, not an executable one).

## Usage

Adding the dependency to a Spring Boot **servlet** application is enough: no `@Import` or
`@ComponentScan` is needed, and nothing has to be written. `LoggingAutoConfiguration` is listed in
`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
and is active only in servlet web applications. Every bean backs off when the application defines its
own bean of the same type.

The library does not bring Spring Web, the servlet API or AspectJ: the application has them. For the
automatic method logs the application needs `spring-boot-starter-aspectj` (Spring AOP). `lib-tenant`
is optional.

### Format

| Profile | Format |
|---|---|
| none (default) | Readable text: time, level, service name (`spring.application.name`), thread, `[correlationId,tenant]`, logger and message |
| `prod` | Structured JSON (Spring Boot structured logging, `logstash` format): MDC entries and on-demand fields are separate JSON fields |

The defaults are added with the lowest precedence, so the application can override any of them
(`logging.structured.format.console`, `logging.pattern.console`).

### Correlation id and MDC

- A request with the header `X-Correlation-Id` keeps that value (only `A-Z a-z 0-9 . _ -`, up to 128
  characters, otherwise it is replaced); a request without it gets a generated UUID.
- The value is returned in the response header `X-Correlation-Id` and is sent as `X-Correlation-Id` on
  outgoing calls made with an injected `RestClient.Builder`.
- MDC keys: `correlationId` and, when `lib-tenant` is on the classpath and the request has a tenant,
  `tenant` (`TenantContext.current()`). Without `lib-tenant` the application logs normally with no `tenant` key.
- The MDC is cleared when the request ends. For a controller that returns `Callable`, `CompletableFuture`
  or `DeferredResult` the keys are set again in the async dispatch, so they are present while the handler
  result is processed.

### Automatic logs and level policy

| What | Level |
|---|---|
| Each request, when it finishes: method, route, status and duration | `INFO` |
| 4xx response (no stack trace) | `WARN` |
| Request slower than `pirca.logging.slow-request-threshold` (default `2s`) | `WARN` |
| 5xx response or unhandled exception, with the stack trace once | `ERROR` |
| Entry and exit with duration of every method of controller and service beans | `DEBUG` (nothing at `INFO` or higher) |

The request line is written once, when the request finishes, also for controllers that return `Callable`,
`CompletableFuture` or `DeferredResult`: the status and the duration are the real ones, measured from the
start of the request to the end of the async work.

The method logs use Spring AOP: they only cover calls that reach a proxied bean, not calls inside the
same class.

One log per exception: an exception handler leaves the exception it handled in the request attribute
`cl.pirca.lib.errors.EXCEPTION` and does not log it; the filter reads that attribute and writes the one
request line (4xx `WARN` without trace, 5xx `ERROR` with trace). An exception that nobody handled is
logged by the filter as `ERROR` with its trace and answered with a 500, so the servlet container does
not log it again. This library does not depend on `lib-errors`.

### The `Log` API

For logs written on demand. The level is the last call: `debug()`, `info()`, `warn()`, `error()` or
`error(Throwable)` (the stack trace is written once).

```java
Log.action("debts").description("Debts listed").field("count", 3).info();
Log.action("payment").description("Payment failed").field("debtId", id).error(exception);
```

`action` is always the first field and `description` is the readable message. In text each field is
`key=value`; in JSON each field is a separate field. The log inherits `correlationId` and `tenant` from the
MDC and uses the logger of the class that calls `Log.action`.

### Asynchronous work

Tasks of `@Async` and of Spring's `applicationTaskExecutor` see the `correlationId` and the `tenant` of
the thread that submitted them. `LoggingTaskDecorator` (a `@Primary` `TaskDecorator`) copies the MDC and also
applies every other `TaskDecorator` bean (`lib-tenant`'s, or the application's own). Without `lib-tenant`
only the correlation id travels.

### Filter order

`RequestLoggingFilter` has order `Ordered.HIGHEST_PRECEDENCE + 20`, greater than `TenantFilter.ORDER`
(`HIGHEST_PRECEDENCE + 10`), so the tenant is already set when it runs.

## Build and test

```bash
./gradlew build
```

## Test suites

| Suite      | Directory      | Run                  | Purpose                       |
|------------|----------------|----------------------|-------------------------------|
| `test`     | `src/test`     | `./gradlew test`     | Unit tests                    |
| `intTest`  | `src/intTest`  | `./gradlew intTest`  | Integration tests             |
| `archTest` | `src/archTest` | `./gradlew archTest` | Architecture tests (ArchUnit) |

`./gradlew check` runs the three suites, `test` first. Each suite can also run alone.

### Quality

`./gradlew build` also runs SpotBugs with FindSecBugs on the main code and checks JaCoCo line coverage
(minimum set by `minimumLineCoverage` in `gradle/quality.gradle`). Mutation testing is slow and runs only by hand:
`./gradlew pitest` (report in `build/reports/pitest/index.html`).

### Architecture tests

`ArchitectureTest` enforces the layered design `controller -> service -> repository | client`
(naming in both directions: a class in a layer package has that layer's suffix, and a
class with a layer's suffix lives in that layer's package; no upward calls, no `business`
package) and forbids package cycles.

Every layer rule is tagged `layer-<layer>` (`layer-controller`, `layer-service`,
`layer-repository`, `layer-client`). A layer this artifact does not have is excluded in
`gradle.properties`:

```properties
archTest.excludeLayers=controller,service,repository,client
```

Remove a layer from the list as soon as the artifact gets it. Unknown layer names fail the
build. A rule of a layer that is not excluded fails if the layer has no classes.

## Makefile shortcuts

The `Makefile` holds local shortcuts for the Gradle commands in this README. They are conveniences
for your machine: CI does not use them. Run `make` with no arguments to list them.

| Shortcut | Equivalent command |
|----------|--------------------|
| `make test`     | `./gradlew test`        |
| `make int`      | `./gradlew intTest`     |
| `make arch`     | `./gradlew archTest`    |
| `make build`    | `./gradlew build`       |
| `make me-happy` | `./gradlew clean build` |
| `make publish`       | `./gradlew publish`              |
| `make publish-local` | `./gradlew publishToMavenLocal`  |

## Publish

Local publishing (to consume it from another project on the same machine):

```bash
./gradlew publishToMavenLocal
```

Publish to GitHub Packages (repository `GitHubPackages` in `gradle/publish.gradle`, target URL set by
`pirca.registryUrl` in `gradle.properties`) with a token
that has the `write:packages` scope, supplied at build time and never stored in the project:

```bash
export GITHUB_ACTOR=<github-user>
export GITHUB_TOKEN=<token>
./gradlew publish
```

or set `gpr.user` and `gpr.token` in `~/.gradle/gradle.properties` (not the project one).

### Using other pirca libs

The `lib-*` libraries (`cl.pirca.lib.*`) are published to GitHub Packages, which asks for a token
even to read. `gradle/repositories.gradle` reads it at build time; it is never stored in the
project. A build that uses no pirca lib needs no token. The registry base URL is the single
`pirca.registryUrl` line in `gradle.properties`.

Locally, set a token with the `read:packages` scope in the environment:

```bash
export GITHUB_ACTOR=<github-user>
export GITHUB_TOKEN=<token>
```

or in `~/.gradle/gradle.properties` (not the project one): `gpr.user=<github-user>` and
`gpr.token=<token>`.

In a Docker build, supply the token as a BuildKit secret:

```bash
docker build --secret id=github_actor,env=GITHUB_ACTOR --secret id=github_token,env=GITHUB_TOKEN .
```

This project has no Dockerfile. A service generated by java-skeleton already reads both secrets in
its `ci/docker/Dockerfile`.

Never pass the token as `ARG` or `ENV`: it would stay in the image layers and history. A secret
mount is available only during that `RUN` and is not stored in the image.
