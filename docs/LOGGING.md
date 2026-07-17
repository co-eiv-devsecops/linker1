# Logging Configuration

## Framework

Linker1 logs through [SLF4J](https://1.n-la-c.app/doc-slf4j) with [Logback](https://1.n-la-c.app/doc-logback) as the binding, configured in [`resources/logback.xml`](../resources/logback.xml) (packaged at the root of the jar's classpath, so it applies regardless of how the jar is launched).

Every log call in application code goes through a class-scoped SLF4J `Logger`:

```java
private static final Logger log = LoggerFactory.getLogger(LinkRoutes.class);
```

## Choosing verbosity: the `LOG_LEVEL` environment variable

The application's own log verbosity is controlled by a single environment variable, **`LOG_LEVEL`**, read at startup by `logback.xml`:

```xml
<logger name="linker" level="${LOG_LEVEL:-INFO}" />
<logger name="Main" level="${LOG_LEVEL:-INFO}" />
```

Valid values, from least to most verbose: `ERROR`, `WARN`, `INFO` (default), `DEBUG`, `TRACE`.

This is deliberately scoped to the `linker` package and the `Main` class only — not the Logback root logger. Third-party libraries (Jetty, the SQLite JDBC driver, the LaunchDarkly SDK, etc.) are pinned to `INFO` regardless of `LOG_LEVEL`, so setting `LOG_LEVEL=TRACE` to debug the application doesn't flood the console with framework-internal noise. Verified directly: with `LOG_LEVEL=TRACE` against the same packaged jar, application log lines appear at `TRACE`/`DEBUG` as expected while third-party output stays at `INFO` (hundreds of `DEBUG`-level Jetty/SQLite lines otherwise appear if the root logger itself is set to `TRACE`).

### Changing verbosity without rebuilding — same deployable artifact

`LOG_LEVEL` is read at process startup, not baked into the jar, so the exact same build artifact can run at any verbosity just by changing how it's launched:

```bash
# Default (INFO) -- no action needed
java -jar linker1-1.0-jar-with-dependencies.jar

# More detail for troubleshooting a specific issue
LOG_LEVEL=DEBUG java -jar linker1-1.0-jar-with-dependencies.jar

# Maximum verbosity
LOG_LEVEL=TRACE java -jar linker1-1.0-jar-with-dependencies.jar
```

On the deployed VM, this is a systemd `Environment=` line ([`deploy.sh`](../deploy.sh) writes it into `/etc/systemd/system/linker1.service`):

```ini
Environment="LOG_LEVEL=INFO"
```

To change verbosity on the live instance without a redeploy: edit that line (or override it via `systemctl edit linker1.service`), then `sudo systemctl daemon-reload && sudo systemctl restart linker1.service`. The jar itself never changes.

## Log levels used and what they mean here

| Level | Used for | Example |
| --- | --- | --- |
| `TRACE` | Finest-grained request lifecycle detail | `Received GET request on path=/{id}` |
| `DEBUG` | Steps within a request/operation that are useful when actively troubleshooting but noisy otherwise | `Resolving link id={id}` |
| `INFO` | Significant, expected events worth keeping visible by default | `Created link id={id} url={url}`, `Server started on port={port}` |
| `WARN` | Recoverable problems / expected-but-notable failure paths (client error responses) | `Link id={id} not found`, `Alias conflict for alias={alias}` |
| `ERROR` | Unexpected failures that produce a 500 response or otherwise need investigation | `Failed to resolve link id={id}` (with the causing exception) |

## Log export via OpenTelemetry

Every log line is also delivered to the OpenTelemetry Logs pipeline via [`opentelemetry-logback-appender-1.0`](https://1.n-la-c.app/doc-otel-logback-appender), configured as a second appender alongside the console:

```xml
<appender name="OTEL" class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">
    <captureExperimentalAttributes>true</captureExperimentalAttributes>
    <captureCodeAttributes>true</captureCodeAttributes>
    <captureMdcAttributes>*</captureMdcAttributes>
</appender>
```

`Telemetry`'s constructor calls `OpenTelemetryAppender.install(openTelemetry)` once at startup, wiring this appender to the same `OpenTelemetry` SDK instance used for metrics and traces (see [`docs/INSTRUMENTATION.md`](INSTRUMENTATION.md)). Whether these logs actually leave the process depends on whether an OTLP endpoint is configured — see that document for the `OTEL_EXPORTER_OTLP_ENDPOINT` variable.
