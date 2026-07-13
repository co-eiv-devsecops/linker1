#!/bin/bash
#
# Post-deploy check: confirm the freshly deployed linker1 instance is actually
# emitting telemetry into Grafana Cloud, not just that /healthz returns 200
# locally on the VM (pipeline.yml already checks that separately).
#
# Queries Grafana Cloud's Prometheus-compatible query API for
# linker_healthcheck_up{service_name="linker1"} and expects a recent value of 1.
# Retries a few times because OTel batches metrics on an interval and Grafana
# Cloud's ingestion has its own propagation delay -- a single immediate query
# right after the deploy would false-negative even on a healthy instance.
#
# Usage: bash scripts/check-grafana-metrics.sh
#
# Required environment variables:
#   GRAFANA_PROM_QUERY_URL   Grafana Cloud Prometheus/Mimir query endpoint,
#                            e.g. https://prometheus-prod-NN-prod-xx.grafana.net/api/prom/api/v1/query
#   GRAFANA_PROM_USER        Grafana Cloud Prometheus instance ID (numeric)
#   GRAFANA_CLOUD_API_KEY    Grafana Cloud API key / access policy token with metrics:read
#
# If any of those are unset, the check is skipped (exit 0) rather than failing
# the deploy -- see docs/DEPLOYMENT.md, these are not configured in the repo yet.
#
# Optional:
#   SERVICE_NAME             OTel service.name to filter on (default: linker1)
#   METRIC                   Metric to query (default: linker_healthcheck_up)
#   MAX_ATTEMPTS              Retry count (default: 6)
#   RETRY_DELAY_SECONDS       Seconds between retries (default: 20)

set -euo pipefail

SERVICE_NAME="${SERVICE_NAME:-linker1}"
METRIC="${METRIC:-linker_healthcheck_up}"
MAX_ATTEMPTS="${MAX_ATTEMPTS:-6}"
RETRY_DELAY_SECONDS="${RETRY_DELAY_SECONDS:-20}"

if [ -z "${GRAFANA_PROM_QUERY_URL:-}" ] || [ -z "${GRAFANA_PROM_USER:-}" ] || [ -z "${GRAFANA_CLOUD_API_KEY:-}" ]; then
  echo "::warning::Grafana post-deploy check skipped -- GRAFANA_PROM_QUERY_URL/GRAFANA_PROM_USER/GRAFANA_CLOUD_API_KEY not configured (see docs/DEPLOYMENT.md)"
  exit 0
fi

QUERY="${METRIC}{service_name=\"${SERVICE_NAME}\"}"
echo "Querying Grafana Cloud for: $QUERY"

attempt=1
while [ "$attempt" -le "$MAX_ATTEMPTS" ]; do
  echo "--- attempt $attempt/$MAX_ATTEMPTS ---"

  RESPONSE=$(curl -sS -u "${GRAFANA_PROM_USER}:${GRAFANA_CLOUD_API_KEY}" \
    --data-urlencode "query=${QUERY}" \
    "${GRAFANA_PROM_QUERY_URL}") || RESPONSE=""

  STATUS=$(echo "$RESPONSE" | jq -r '.status // "error"')
  VALUE=$(echo "$RESPONSE" | jq -r '.data.result[0].value[1] // empty')

  if [ "$STATUS" = "success" ] && [ "$VALUE" = "1" ]; then
    echo "OK: $METRIC=1 for service_name=$SERVICE_NAME -- telemetry pipeline confirmed post-deploy."
    exit 0
  fi

  echo "Not confirmed yet (status=$STATUS value=${VALUE:-<none>}). Response: $RESPONSE"

  if [ "$attempt" -lt "$MAX_ATTEMPTS" ]; then
    sleep "$RETRY_DELAY_SECONDS"
  fi
  attempt=$((attempt + 1))
done

echo "::error::Grafana post-deploy check failed -- $METRIC never reported 1 for service_name=$SERVICE_NAME after $MAX_ATTEMPTS attempts. The instance may not be exporting telemetry (check OTEL_EXPORTER_OTLP_ENDPOINT/OTEL_EXPORTER_OTLP_HEADERS on the VM)."
exit 1
