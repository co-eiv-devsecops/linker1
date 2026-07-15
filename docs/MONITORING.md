# Monitoring (Grafana)

Linker1 is built to export metrics, traces, and logs to Grafana Cloud via OTLP (see [`INSTRUMENTATION.md`](INSTRUMENTATION.md)) and has a Synthetic Monitoring check polling `/healthz` (see [`HEALTHCHECK.md`](HEALTHCHECK.md#grafana-cloud-synthetic-monitoring)). This document covers the piece that was still missing: an actual dashboard to look at, what each panel means, and an automated check that a deploy really is emitting telemetry — not just that `/healthz` returns `200` on the VM. **As of the first real import, the export itself isn't wired up yet in production** — see [Troubleshooting](#troubleshooting-dashboard-shows-no-data-on-every-panel) below before assuming the dashboard or its queries are wrong.

---

## Accessing Grafana

1. Go to `https://coralavocado2395.grafana.net` and sign in (course-provided Grafana Cloud org login).
2. The left sidebar has the pieces relevant to linker1:
   - **Dashboards** → the imported `Linker1 - Application & Health` dashboard (see below).
   - **Explore** → ad-hoc PromQL queries against the metrics datasource; the fastest way to check whether a metric name actually exists before trusting a dashboard panel.
   - **Connections > OpenTelemetry** → the OTLP gateway URL/token used by `deploy.sh`/`pipeline.yml` (see [`INSTRUMENTATION.md`](INSTRUMENTATION.md#setting-it-up-for-grafana-cloud)).
   - **Synthetic Monitoring** → the `Linker1 Health Check` probe on `/healthz`.

## Importing the dashboard

The dashboard lives in this repo as JSON (`docs/grafana/linker1-dashboard.json`), written by hand to Grafana's dashboard schema rather than built directly in the UI — same result, but keeps it reviewable in a PR like any other file. Given it's not the exported result of a live dashboard, one thing needs to be verified once after the first import: whether the panel queries' exact metric names match what the OTLP-to-Prometheus translation actually produced (see [Metric name note](#metric-name-note) below), and adjusted if not.

1. In Grafana: **Dashboards → New → Import**.
2. Upload `docs/grafana/linker1-dashboard.json` (or paste its contents).
3. When prompted for the `DS_PROMETHEUS` input, select the stack's built-in Prometheus/Mimir datasource (typically named `grafanacloud-<stack>-prom`) — this is where linker1's OTLP metrics land.
4. Save. It should now show under **Dashboards** as `Linker1 - Application & Health`.

**Keeping it in sync**: if you tweak panels in the UI afterward, re-export via the dashboard's **Share → Export → "Export for sharing externally"**, overwrite `docs/grafana/linker1-dashboard.json` with the result, and commit it — the same way any other config-as-code change goes through a PR.

## Metric name note

OpenTelemetry metric names use dots (`linker.healthcheck.up`); once they land in a Prometheus-compatible backend they go through OTel's standard Prometheus naming translation: dots become underscores, counters get a `_total` suffix, and units get appended as a suffix (`By` → `_bytes`, `s` → `_seconds`, `ms` → `_milliseconds`). The dashboard's queries assume that translation, e.g. `linker.healthcheck.up` → `linker_healthcheck_up`, `linker.http.requests` → `linker_http_requests_total`. The exact suffixing can vary slightly by OTel Collector/SDK version, so **the first time you import the dashboard, open Explore and confirm the metric names it queries actually exist** (type `linker_` and see what autocompletes); if a panel shows "No data" for a reason other than "no traffic yet," this is the most likely cause — fix the `expr` in that panel and re-export.

| OTel name | Expected Prometheus name | Type |
| --- | --- | --- |
| `linker.http.requests` | `linker_http_requests_total` | Counter |
| `linker.http.errors` | `linker_http_errors_total` | Counter |
| `linker.http.request.duration` | `linker_http_request_duration_milliseconds_{bucket,sum,count}` | Histogram |
| `linker.links.count` | `linker_links_count` | Gauge |
| `linker.jvm.heap.used` | `linker_jvm_heap_used_bytes` | Gauge |
| `linker.jvm.heap.max` | `linker_jvm_heap_max_bytes` | Gauge |
| `linker.jvm.threads` | `linker_jvm_threads` | Gauge |
| `linker.process.uptime` | `linker_process_uptime_seconds` | Gauge |
| `linker.db.operation.duration` | `linker_db_operation_duration_milliseconds_{bucket,sum,count}` | Histogram |
| `linker.healthcheck.checks` | `linker_healthcheck_checks_total` | Counter |
| `linker.healthcheck.failures` | `linker_healthcheck_failures_total` | Counter |
| `linker.healthcheck.duration` | `linker_healthcheck_duration_milliseconds_{bucket,sum,count}` | Histogram |
| `linker.healthcheck.up` | `linker_healthcheck_up` | Gauge |

All series are also labeled `service_name="linker1"` (from `OTEL_SERVICE_NAME`), which every panel filters on via the dashboard's `service_name` template variable.

---

## Troubleshooting: dashboard shows "No data" on every panel

Verified after the first real import (2026-07-12): every panel showed "No data", including ones querying metric names (`linker_http_requests_total`, `linker_http_errors_total`) that autocompleted correctly in Explore's metric picker. Two things worth knowing so this doesn't cause confusion again:

1. **`coralavocado2395.grafana.net` is a shared, course-wide stack, not linker1-exclusive.** Typing `linker` in the metric picker surfaces series from *every group's* project, not just this one — several groups appear to be instrumenting a similarly-named "Linker" URL shortener, so names like `linker_db_connection_state`, `linker_http_requests_in_flight`, or `linker_short_links_created_total` can show up despite not existing anywhere in this repo's code. Always disambiguate by the `service_name` (or `job`) label, e.g. run `{service_name="linker1"}` with no metric name to see only this project's own series before trusting a panel.
2. **The actual cause here**: `{service_name="linker1"}` returned **zero series** — linker1's own instance has never successfully exported telemetry to this stack. This isn't a dashboard bug; it's the gap already called out in [`DEPLOYMENT.md`](DEPLOYMENT.md#optional-otlp-export-and-mysql-healthcheck) — `OTEL_EXPORTER_OTLP_ENDPOINT`/`OTEL_EXPORTER_OTLP_HEADERS` aren't configured as GitHub secrets/variables yet, so the OTel SDK on the VM has nowhere to send batches (see [`INSTRUMENTATION.md`](INSTRUMENTATION.md#otlp-export-configuration) — this fails silently, `/healthz` and `/` still return `200` throughout).

**To fix**: configure `OTEL_EXPORTER_OTLP_ENDPOINT` and `OTEL_EXPORTER_OTLP_HEADERS` (see [`INSTRUMENTATION.md`](INSTRUMENTATION.md#setting-it-up-for-grafana-cloud) for where to get the gateway URL/token from Grafana Cloud's **Connections > OpenTelemetry** page), then redeploy (push to `main`, or `workflow_dispatch` on `pipeline.yml`). Give it a few minutes after the deploy for the first metrics batch to export and get ingested, then re-run `{service_name="linker1"}` in Explore to confirm before expecting the dashboard to populate. This is also exactly the condition the [post-deploy automated check](#post-deploy-automated-check-bonus) below is meant to catch on every future deploy, once its own secrets are configured too.

---

## Panel-by-panel guide (read this during an incident)

### HTTP (RED method)

- **Request rate** — traffic volume (`rate(linker_http_requests_total[5m])` by route). A sudden drop to zero right after a deploy usually means the new instance isn't receiving traffic at all (bad load balancer switchover in the blue/green flow), not that users stopped requesting links.
- **Error rate (%)** — share of requests returning `>=400`. Sustained above ~1-2% right after a deploy is worth investigating **before** the old version is retired in the blue/green switchover — this is the panel that should gate "is it safe to delete the old VM."
- **Request duration (p50/p95/p99)** — a healthy request rate with a spiking p99 often means the new instance is up but struggling (cold JVM, GC pauses, DB connection pool warming up), which a simple `/healthz` 200 check won't catch.

### JVM & process

- **JVM heap used vs max** — used trending up and never dropping back down (no sawtooth from GC) points at a memory leak, not a transient load spike.
- **JVM live threads** — a step change right after deploy is expected (new JVM, fresh thread pools); a steady climb over hours suggests a thread leak.
- **Process uptime** — the fastest way to confirm a deploy actually restarted the process: this should drop back near zero right after a successful deploy. If it keeps climbing across a deploy, the systemd restart didn't take effect.

### Healthcheck (`/healthz`)

- **Up / Down** — `linker_healthcheck_up`, the single number to alert on. `0` means the app itself may be fine but its MySQL dependency isn't reachable (see [`HEALTHCHECK.md`](HEALTHCHECK.md) for why `/healthz` checks MySQL specifically, not the app's real SQLite store).
- **Healthcheck duration** — a rising trend without a change in up/down status usually means MySQL is slow or under load, not unreachable — worth escalating before it flips to fully down.
- **Healthcheck failures** — should be flat at zero in steady state. Any nonzero rate deserves a look even if Up/Down hasn't flipped yet; a flapping check is an early warning.

### Business

- **Stored links (count)** — not an alerting signal by itself, but a sudden drop to (or near) zero after a deploy is a strong signal the wrong `LINKER_DB_PATH` is being used or the database file was lost.
- **DB operation duration (create/resolve)** — separates "the app is slow" (this panel, SQLite) from "the healthcheck is slow" (MySQL) — they hit different datastores and a problem in one doesn't imply a problem in the other.

---

## Post-deploy automated check (bonus)

`scripts/check-grafana-metrics.sh`, called as a step in `deploy-prod` (`.github/workflows/pipeline.yml`) right after the existing local `/healthz` check, queries Grafana Cloud's Prometheus-compatible query API for `linker_healthcheck_up{service_name="linker1"}` and expects a recent value of `1`. It retries (6 attempts, 20s apart by default) because OTel batches exports on an interval and Grafana Cloud's ingestion has its own propagation delay — a single immediate query right after a deploy would false-negative even on a healthy instance.

This closes the gap the plain `HEALTHZ_STATUS` curl in `deploy-prod` leaves open: that check only proves the process answers `/healthz` locally on the VM, not that the observability pipeline (OTLP export → Grafana Cloud ingestion) is actually working end to end post-deploy. A regression like the one fixed in the OTLP protocol issue — app fully healthy, telemetry silently going nowhere — would pass the old check and fail this one.

### Required configuration (not yet set in the repo)

Same pattern as `OTEL_EXPORTER_OTLP_ENDPOINT`/`OTEL_EXPORTER_OTLP_HEADERS` in [`DEPLOYMENT.md`](DEPLOYMENT.md#optional-otlp-export-and-mysql-healthcheck): until these are added, the step prints a `::warning::` and exits `0` (skipped, doesn't block the deploy). Once configured, it becomes a real gate — the step fails `deploy-prod` (and therefore triggers `rollback`, see [`ROLLBACK_STRATEGY.md`](ROLLBACK_STRATEGY.md)) if telemetry isn't confirmed.

| Name | Type | Level | Use |
| --- | --- | --- | --- |
| `GRAFANA_PROM_QUERY_URL` | variable | repo or `prod` env | Grafana Cloud Prometheus/Mimir query endpoint for the stack, e.g. `https://prometheus-prod-NN-prod-xx.grafana.net/api/prom/api/v1/query` — found in the Grafana Cloud portal under the stack's **Prometheus → Details**. |
| `GRAFANA_PROM_USER` | variable | repo or `prod` env | The Prometheus instance ID (numeric), same **Details** page. |
| `GRAFANA_CLOUD_API_KEY` | secret | repo or `prod` env | A Grafana Cloud API key / Cloud Access Policy token scoped to `metrics:read`, generated from **Administration → API keys** (or the newer Access Policies page) on the stack. |

### Running it manually

```bash
export GRAFANA_PROM_QUERY_URL="https://prometheus-prod-NN-prod-xx.grafana.net/api/prom/api/v1/query"
export GRAFANA_PROM_USER="123456"
export GRAFANA_CLOUD_API_KEY="glc_..."
bash scripts/check-grafana-metrics.sh
```

---

## Related documents

- [Instrumentation](INSTRUMENTATION.md) — how metrics/traces/logs get to Grafana Cloud in the first place.
- [Health Check](HEALTHCHECK.md) — `/healthz`, its metrics, and the Synthetic Monitoring check.
- [Deployment](DEPLOYMENT.md) — `pipeline.yml`'s jobs and required secrets/variables.
- [`docs/grafana/linker1-dashboard.json`](grafana/linker1-dashboard.json) — the dashboard itself.
- [`scripts/check-grafana-metrics.sh`](../scripts/check-grafana-metrics.sh) — the post-deploy check implementation.
