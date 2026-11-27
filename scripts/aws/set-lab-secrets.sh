#!/usr/bin/env bash
# Copies the AWS Academy Learner Lab credentials into this repo's GitHub secrets.
# Usage: paste the "AWS CLI" block from the lab's "AWS Details" panel into
# ~/.aws/credentials, then run:  scripts/aws/set-lab-secrets.sh [owner/repo]
# Run it again every time you restart the lab (credentials change each session).
set -euo pipefail
REPO="${1:-Anderfg13/linker1}"
CREDS="${AWS_SHARED_CREDENTIALS_FILE:-$HOME/.aws/credentials}"
get() { sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*//p" "$CREDS" | head -n1 | tr -d '\r'; }
for k in aws_access_key_id aws_secret_access_key aws_session_token; do
  v="$(get "$k")"
  [ -n "$v" ] || { echo "missing $k in $CREDS" >&2; exit 1; }
  name="$(echo "$k" | tr a-z A-Z)"
  printf '%s' "$v" | gh secret set "$name" --repo "$REPO"
done
echo "Secrets updated for $REPO"
