#!/bin/bash
#
# Rollback linker1 to a previous stable tag.
#
# Usage: bash scripts/rollback.sh <tag>
# Example: bash scripts/rollback.sh v1.0.0
#
# Must be run from inside the existing linker1 git checkout on the VM
# (the same one deploy.sh normally builds from). It stops the service,
# checks out the target tag, rebuilds via deploy.sh, and verifies the
# service comes back up.

set -euo pipefail

TAG="${1:-}"
SERVICE_NAME="linker1.service"
REPO_URL="https://github.com/co-eiv-devsecops/linker1.git"

if [ -z "$TAG" ]; then
  echo "Usage: bash scripts/rollback.sh <tag>"
  echo "Example: bash scripts/rollback.sh v1.0.0"
  exit 1
fi

if [[ ! "$TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "ERROR: invalid tag format: $TAG"
  echo "Expected vMAJOR.MINOR.PATCH (example: v1.0.0)"
  exit 1
fi

echo "=== ROLLBACK TO $TAG ==="

echo "✓ Verifying the tag exists..."
git fetch --tags --force
if ! git rev-parse "$TAG" >/dev/null 2>&1; then
  echo "ERROR: tag $TAG does not exist in the repository."
  exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
  echo "ERROR: there are uncommitted changes in the VM checkout."
  echo "Check 'git status' before continuing; rollback does not discard local changes."
  exit 1
fi

echo "✓ Stopping $SERVICE_NAME..."
sudo systemctl stop "$SERVICE_NAME"

echo "✓ Switching code to tag $TAG..."
git checkout "$TAG"

echo "✓ Rebuilding and restarting via deploy.sh..."
bash deploy.sh "$REPO_URL"

echo "✓ Verifying the service..."
sleep 3

if ! systemctl is-active --quiet "$SERVICE_NAME"; then
  echo "ERROR: $SERVICE_NAME did not stay active after the rollback."
  sudo systemctl status "$SERVICE_NAME" --no-pager || true
  exit 1
fi

STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/ || echo "000")
if [ "$STATUS" != "200" ]; then
  echo "ERROR: GET / returned $STATUS instead of 200."
  exit 1
fi

echo "=== ROLLBACK COMPLETE ==="
echo "Active version: $(git describe --tags)"
echo "Service: $(systemctl is-active "$SERVICE_NAME")"
echo "GET /: $STATUS"
