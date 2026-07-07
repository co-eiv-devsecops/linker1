# OpenTelemetry Instrumentation

This document describes Linker1's observability instrumentation: logs, metrics, and traces, all built on the [OpenTelemetry Java SDK](https://opentelemetry.io/docs/languages/java/), and the small reusable library (`linker.telemetry`) that lets the rest of the codebase use them with minimal code.

---

## Why manual instrumentation, not the Java agent

OpenTelemetry for Java offers two integration paths: attaching the [auto-instrumentation javaagent](https://opentelemetry.io/docs/zero-code/java/agent/) (`-javaagent:opentelemetry-javaagent.jar`, zero source changes, instruments known frameworks automatically), or building the SDK programmatically and calling its API directly from application code. Linker1 uses the second approach: every span, metric, and log statement in this codebase is an explicit line of code in `src/`, not something injected by bytecode manipulation at startup. This keeps the instrumentation visible, testable, and reviewable like any other code, at the cost of having to write it by hand.

---

## Architecture

Following the same Dependency Injection / Inversion of Control approach as [`FEATURE_FLAGS.md`](FEATURE_FLAGS.md): the OpenTelemetry SDK is built once, in the composition root, and injected into small, single-purpose wrapper classes. Nothing outside `linker.telemetry` imports an OpenTelemetry class directly.

```text
Main (Composition Root)
 ├── Telemetry.createFromEnvironment()
 │        │
 │        ▼
 │   OpenTelemetry SDK (reads OTEL_* env vars, exports via OTLP)
 │
 ├── RequestMetrics(telemetry.meter())                  ──┐
 ├── LinkSpans(telemetry.tracer(), telemetry.meter())     ├─→ injected into LinkRoutes
 ├── SystemMetrics(telemetry.meter())                  ───┘   (via constructor)
 └── SLF4J Logger (per class, standard pattern)
```

### `Telemetry` — the composition root

```java
Telemetry telemetry = Telemetry.createFromEnvironment();
```

Internally, this calls `AutoConfiguredOpenTelemetrySdk.initialize()` (from `opentelemetry-sdk-extension-autoconfigure`), which builds a fully configured `OpenTelemetrySdk` by reading the standard `OTEL_*` environment variables — see [OTLP export configuration](#otlp-export-configuration) below. It also installs the Logback appender (`OpenTelemetryAppender.install(...)`) so application logs flow through the same pipeline as metrics and traces.

The constructor `Telemetry(OpenTelemetry openTelemetry)` is also public and takes an already-built `OpenTelemetry` instance directly — this is the seam tests use to inject an in-memory SDK instead of one wired to a real (or absent) OTLP collector.

### `RequestMetrics` — RED-method HTTP metrics

One method, called once per request:

```java
requestMetrics.recordRequest(route, statusCode, durationMillis);
```

This records a request counter, an error counter (only incremented when `statusCode >= 400`), and a duration histogram — the **Rate / Errors / Duration** trio from the RED method. Callers never touch a `Counter` or `Histogram` directly.

### `LinkSpans` — traces for the two user-facing operations

```java
T result = linkSpans.traceCreate(url, () -> service.createResult(url));
T result = linkSpans.traceResolve(id, () -> service.get(id));
```

Each call produces one trace with two spans: a parent span for the whole operation (`link.create` / `link.resolve`) and a child span around the actual persistence/lookup call (`link.create.persist` / `link.resolve.lookup`). The child span's status is set to `ERROR` (with the exception recorded) if the wrapped call throws, and that failure propagates up to the parent span's status too. The unit of work is a `ThrowingSupplier<T, E>` — a `Supplier`-like functional interface that's allowed to throw a checked exception, since `LinkService`'s methods throw `SQLException`.

The duration of that child span is also recorded in the `linker.db.operation.duration` histogram (tagged with an `operation` attribute of `create` or `resolve`), so `LinkSpans` needs both a `Tracer` and a `Meter` injected via its constructor.

### `SystemMetrics` — point-in-time gauges

```java
systemMetrics.setLinkCount(repository.countLinks());
systemMetrics.recordHeapUsage();
```

Two synchronous gauges (`LongGauge`, via `meter.gaugeBuilder(name).ofLongs().build()`): the current number of stored links, and the JVM heap currently in use. `Main` samples both every 30 seconds via a `ScheduledExecutorService`.

---

## What's instrumented, concretely

### Metrics (2 counters, 2 gauges, 2 histograms — minimum met)

| Name | Type | Unit | Meaning |
| --- | --- | --- | --- |
| `linker.http.requests` | Counter | `{request}` | Total HTTP requests handled |
| `linker.http.errors` | Counter | `{request}` | Total requests that returned >=400 |
| `linker.links.count` | Gauge | `{link}` | Current number of stored short links |
| `linker.jvm.heap.used` | Gauge | `By` | JVM heap memory currently in use |
| `linker.http.request.duration` | Histogram | `ms` | HTTP request duration, tagged with `route` and `status_code` |
| `linker.db.operation.duration` | Histogram | `ms` | Duration of the repository call inside a `link.create`/`link.resolve` trace, tagged with `operation` (`create`/`resolve`) |

### Traces (2 traces, 2 spans each — minimum met)

- **`link.create`** (parent) → **`link.create.persist`** (child): wraps `POST /link`, both the plain-URL and aliased-URL paths.
- **`link.resolve`** (parent) → **`link.resolve.lookup`** (child): wraps `GET /{id}`.

### Logs (2+ entries per level — minimum met)

See [`docs/LOGGING.md`](LOGGING.md) for the full table of log levels and where each is used.

---

## OTLP export configuration

Export is controlled entirely by the standard OpenTelemetry environment variables — no custom configuration mechanism:

| Variable | Purpose | Default if unset |
| --- | --- | --- |
| `OTEL_SERVICE_NAME` | Service name attached to every span/metric/log | `linker1` (set explicitly in `deploy.sh`/`cloud-init.yaml`) |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | Collector endpoint (e.g. a Grafana Cloud OTLP gateway URL) | Unset: the SDK's autoconfigure module falls back to its own default (`http://localhost:4317`, `grpc` protocol) and simply fails to export silently-per-batch if nothing is listening there — it does **not** prevent the app from starting. |
| `OTEL_EXPORTER_OTLP_PROTOCOL` | `grpc` (default for this SDK) or `http/protobuf` | `grpc` |
| `OTEL_EXPORTER_OTLP_HEADERS` | Auth headers, e.g. `Authorization=Basic ...` for Grafana Cloud | none |

**Important operational detail, verified directly**: if `OTEL_EXPORTER_OTLP_ENDPOINT` is set to an **empty string** (as opposed to being entirely absent), the SDK throws `ConfigurationException: OTLP endpoint must be a valid URL` and the application **fails to start**. `deploy.sh` and `cloud-init.yaml` account for this: the `Environment="OTEL_EXPORTER_OTLP_ENDPOINT=..."` line is only written into the systemd unit when a real value is provided; if none is, the variable is omitted entirely rather than set to `""`.

### Setting it up for Grafana Cloud

1. From the Grafana Cloud stack's "Connections > OpenTelemetry" page, get the OTLP gateway URL and an API token.
2. On the VM (or when redeploying), export before running `deploy.sh`:

   ```bash
   export OTEL_EXPORTER_OTLP_ENDPOINT="https://otlp-gateway-<region>.grafana.net/otlp"
   export OTEL_EXPORTER_OTLP_HEADERS="Authorization=Basic <base64-encoded-instance-id:api-token>"
   bash deploy.sh
   ```

3. `deploy.sh` preserves whatever value is already configured on redeploy if the variable isn't passed again, the same way it already does for `LD_SDK_KEY`.

---

## Testability

Every class in `linker.telemetry` is fully unit tested (see `test/linker/telemetry/`), using `opentelemetry-sdk-testing`'s `InMemoryMetricReader` and a plain `SdkTracerProvider`/`SdkMeterProvider` built without any exporter — no network calls, no real collector needed to run the test suite. `LinkRoutesTest` and `LinkRoutesErrorHandlingTest` exercise the full HTTP-to-span-to-metric path end-to-end, including the unexpected-failure (`500`) branches.
