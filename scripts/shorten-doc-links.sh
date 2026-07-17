#!/bin/bash
#
# Bulk-creates linker1 short links for documentation URLs (issue #73: "TODO
# LINK DE DOCUMENTACIÓN debe utilizar su acortador de URLs").
#
# Reads a list of "alias|target-url" pairs (one per line, # comments and
# blank lines ignored), POSTs each to /link with an explicit alias so the
# resulting short URL is stable and readable
# (https://1.n-la-c.app/<alias>), and prints a markdown-ready
# "original -> short" table so the mapping can be copy-pasted into the
# wiki/docs that reference these links.
#
# A link that already exists with the same alias+url is idempotent (the API
# returns 200, not 201) -- safe to re-run.
#
# Usage:
#   bash scripts/shorten-doc-links.sh [links-file]
# Env vars:
#   BASE_URL   linker1 instance to create links against (default: production)

set -euo pipefail

BASE_URL="${BASE_URL:-https://1.n-la-c.app}"
LINKS_FILE="${1:-$(dirname "$0")/doc-links.txt}"

if [ ! -f "$LINKS_FILE" ]; then
  echo "::error::Links file not found: $LINKS_FILE" >&2
  exit 1
fi

echo "| Alias | Target | Short URL |"
echo "| --- | --- | --- |"

while IFS='|' read -r alias url; do
  # Skip blank lines and comments.
  [ -z "${alias// }" ] && continue
  case "$alias" in \#*) continue ;; esac

  alias="$(echo "$alias" | xargs)"
  url="$(echo "$url" | xargs)"

  # Plain string construction instead of jq (not guaranteed present on every
  # runner/shell): safe here because alias/url values in doc-links.txt are
  # plain identifiers and URLs with no embedded quotes or control characters.
  json_escape() { printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'; }
  payload="{\"url\":\"$(json_escape "$url")\",\"alias\":\"$(json_escape "$alias")\"}"

  response=$(curl -sS -w "\n%{http_code}" -X POST "$BASE_URL/link" \
    -H "Content-Type: application/json" \
    -d "$payload")

  http_code=$(echo "$response" | tail -1)
  body=$(echo "$response" | sed '$d')

  case "$http_code" in
    200|201)
      echo "| \`$alias\` | $url | $BASE_URL/$alias |"
      ;;
    409)
      # Alias taken -- confirm it already points at the same url before
      # treating this as success (a real conflict, e.g. someone else's
      # alias, should not be silently swallowed).
      echo "::warning::Alias '$alias' already exists (409) -- verify it points at $url" >&2
      echo "| \`$alias\` | $url | $BASE_URL/$alias (pre-existing, verify) |"
      ;;
    *)
      echo "::error::Failed to create short link for alias '$alias' (HTTP $http_code): $body" >&2
      exit 1
      ;;
  esac
done < "$LINKS_FILE"
