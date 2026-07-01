#!/bin/bash
set -e

REPO_DIR="$HOME/linker1"

echo "==> Entrando al proyecto..."
cd "$REPO_DIR"

echo "==> Descargando cambios..."
git pull origin main

echo "==> Compilando..."
mvn clean package

echo "==> Deteniendo aplicación anterior..."
pkill -f "jar-with-dependencies.jar" || true

echo "==> Iniciando nueva versión..."
nohup java -jar target/*-jar-with-dependencies.jar > linker.log 2>&1 &

echo "==> Despliegue completado."
