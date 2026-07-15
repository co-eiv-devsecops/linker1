# Health Check

`GET /healthz` reports whether Linker1 can reach its MySQL dependency, so an external monitor (Grafana Cloud Synthetic Monitoring, a load balancer, `systemd`, etc.) can detect an unhealthy instance without needing to understand the app's internals.

---

## Why a separate MySQL connection, not SQLite

Linker1's actual datastore is SQLite (`LinkRepository`, `LINKER_DB_PATH`) — that doesn't change here. The lab assignment asks specifically for a healthcheck that runs `SELECT 1` against MySQL, so `HealthCheck` opens its own, independent MySQL connection purely to answer that question. SQLite reachability isn't part of `/healthz`: if the MySQL dependency is unset or unreachable, `/healthz` reports unhealthy even though the app itself (link creation/resolution) is unaffected, since that path never touches MySQL.

## `linker.health`

```text
Main (Composition Root)
 ├── Supplier<Connection> (MySQL, built from MYSQL_* env vars)
 │        │
 │        ▼
 └── HealthCheck(telemetry.tracer(), connectionSupplier) ──→ injected into HealthRoutes
```

### `HealthCheck`

```java
HealthCheck.Result result = healthCheck.check();
// result.healthy(): boolean
// result.detail():  "OK" or the failure message
```

Takes a `Supplier<Connection>`, not a live `Connection`, injected via the constructor — the same Dependency Injection approach used everywhere else in this codebase (see [`INSTRUMENTATION.md`](INSTRUMENTATION.md), [`FEATURE_FLAGS.md`](FEATURE_FLAGS.md)). This means a MySQL outage fails only the health check's own request, not application startup: `Main` builds the supplier once, and every call to `check()` opens (and closes) a fresh connection through it, retrying implicitly on every poll.

Each call:
1. Starts a span named `mysql.healthcheck`, explicitly `SpanKind.SERVER` (per the lab's requirement — a deliberate departure from the more conventional `CLIENT` kind used for outbound calls elsewhere in this codebase), with `db.system=mysql` and `db.statement=SELECT 1` attributes.
2. Opens a connection via the supplier and executes `SELECT 1`.
3. Sets the span status to `OK` on success, or `ERROR` (with the exception recorded) if the connection or query fails — covering both "MySQL unreachable" and "query itself failed".

### `HealthCheckMetrics`

Every call to `HealthCheck.check()` also records to `HealthCheckMetrics`, following the same RED-method pattern as `linker.telemetry.RequestMetrics` (see [`INSTRUMENTATION.md`](INSTRUMENTATION.md)):

| Name | Type | Unit | Meaning |
| --- | --- | --- | --- |
| `linker.healthcheck.checks` | Counter | `{check}` | Total number of health checks performed, tagged with `outcome` (`healthy`/`unhealthy`) |
| `linker.healthcheck.failures` | Counter | `{check}` | Total number of health checks that failed |
| `linker.healthcheck.duration` | Histogram | `ms` | Duration of the check (connection + `SELECT 1`), tagged with `outcome` |
| `linker.healthcheck.up` | Gauge | `{status}` | `1` if the last check was healthy, `0` otherwise -- the single number to alert on |

### `HealthRoutes`

Registers `GET /healthz`:

| Result | HTTP status | Body |
| --- | --- | --- |
| Healthy | `200` | `OK` |
| Unhealthy | `503` | `Unhealthy: <detail>` |

**Registration order matters**: `HealthRoutes` is registered in `Main.java` before `LinkRoutes`. `LinkRoutes` owns a catch-all `GET /{id}` — if it were registered first, Javalin would match `/healthz` as `id=healthz` and this route would never be reached.

---

## Configuration

| Variable | Purpose | Default if unset |
| --- | --- | --- |
| `MYSQL_HOST` | MySQL host for the healthcheck connection | `localhost` |
| `MYSQL_DATABASE` | MySQL database name | empty |
| `MYSQL_USER` | MySQL user | empty |
| `MYSQL_PWD` | MySQL password | empty |

If left unconfigured, `/healthz` simply reports `503 Unhealthy` (connection refused / access denied, depending on what's actually listening at `MYSQL_HOST`) rather than the application failing to start — consistent with how `OTEL_EXPORTER_OTLP_ENDPOINT` being unset degrades gracefully instead of crashing (see [`INSTRUMENTATION.md`](INSTRUMENTATION.md)).

`deploy.sh` and `infra/cloud-init.yaml` preserve whatever value is already configured on redeploy if a variable isn't passed again, the same pattern already used for `LD_SDK_KEY` and the `OTEL_*` variables.

### JDBC driver registration, a build-specific detail

`pom.xml` adds `com.mysql:mysql-connector-j` alongside the existing `org.xerial:sqlite-jdbc`. Both declare a JDBC 4.0 SPI file at the same path (`META-INF/services/java.sql.Driver`). `maven-assembly-plugin`'s default merge strategy for the fat jar keeps only one dependency's copy of a duplicate resource ("last one wins"), which silently dropped MySQL's driver registration in favor of SQLite's. `Main.java` works around this with an explicit `Class.forName("com.mysql.cj.jdbc.Driver")` call before the connection supplier is built, which triggers the driver's static self-registration block regardless of what the merged SPI file contains.

---

## Testability

`test/linker/health/HealthCheckTest.java` uses the same `InMemorySpanExporter` + `SdkTracerProvider` pattern as `LinkSpansTest` (see [`INSTRUMENTATION.md`](INSTRUMENTATION.md#testability)) to assert span name, `SpanKind.SERVER`, and status — no real MySQL instance is needed to run the suite; a SQLite in-memory connection stands in wherever a real `Connection` is needed, and a throwing supplier simulates an unreachable database. `test/linker/health/HealthRoutesTest.java` starts a real Javalin instance on an ephemeral port and asserts the actual HTTP status/body for both the healthy and unhealthy cases.

---

## Grafana Cloud Synthetic Monitoring

The lab also asks for a Synthetic Monitoring check in Grafana Cloud that polls `/healthz` every 5 minutes to generate synthetic traffic. This is a manual, one-time setup step in the Grafana Cloud UI, not something expressed in this repo's code or Terraform:

1. Go to `https://coralavocado2395.grafana.net/a/grafana-synthetic-monitoring-app/checks`.
2. Create a new **HTTP** check against `https://1.n-la-c.app/healthz`.
3. Set the check frequency to **5 minutes**.
4. Save — Grafana Cloud will begin probing the endpoint on that schedule and reporting the results (uptime, latency) on the Synthetic Monitoring dashboard.

## Related documents

- [Instrumentation](INSTRUMENTATION.md)
- [Deployment](DEPLOYMENT.md)
- [Monitoring (Grafana)](MONITORING.md) — dashboard covering `linker.healthcheck.*`, panel guide, and the automated post-deploy check that queries these metrics
- [`src/linker/health/`](../src/linker/health/)
