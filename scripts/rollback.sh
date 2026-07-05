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
  echo "Uso: bash scripts/rollback.sh <tag>"
  echo "Ejemplo: bash scripts/rollback.sh v1.0.0"
  exit 1
fi

if [[ ! "$TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "ERROR: formato de tag invalido: $TAG"
  echo "Se espera vMAJOR.MINOR.PATCH (ejemplo: v1.0.0)"
  exit 1
fi

echo "=== ROLLBACK A $TAG ==="

echo "✓ Verificando que el tag existe..."
git fetch --tags --force
if ! git rev-parse "$TAG" >/dev/null 2>&1; then
  echo "ERROR: el tag $TAG no existe en el repositorio."
  exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
  echo "ERROR: hay cambios sin commitear en el checkout de la VM."
  echo "Revisa 'git status' antes de continuar; el rollback no descarta cambios locales."
  exit 1
fi

echo "✓ Deteniendo $SERVICE_NAME..."
sudo systemctl stop "$SERVICE_NAME"

echo "✓ Cambiando el código al tag $TAG..."
git checkout "$TAG"

echo "✓ Reconstruyendo y reiniciando con deploy.sh..."
bash deploy.sh "$REPO_URL"

echo "✓ Verificando el servicio..."
sleep 3

if ! systemctl is-active --quiet "$SERVICE_NAME"; then
  echo "ERROR: $SERVICE_NAME no quedó activo tras el rollback."
  sudo systemctl status "$SERVICE_NAME" --no-pager || true
  exit 1
fi

STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/ || echo "000")
if [ "$STATUS" != "200" ]; then
  echo "ERROR: GET / respondió $STATUS en lugar de 200."
  exit 1
fi

echo "=== ROLLBACK COMPLETADO ==="
echo "Version activa: $(git describe --tags)"
echo "Servicio: $(systemctl is-active "$SERVICE_NAME")"
echo "GET /: $STATUS"
