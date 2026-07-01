#!/bin/bash

# Script de despliegue para Linker1 en OCI VM
# Uso:
#   bash deploy.sh <REPO_URL>
#   o bien:
#   REPO_URL=<REPO_URL> bash deploy.sh

set -euo pipefail

REPO_URL="${1:-${REPO_URL:-}}"
REPO_DIR="${REPO_DIR:-linker1}"
APP_DIR="/opt/linker1"
DB_DIR="/var/lib/linker1"

if [ -z "$REPO_URL" ]; then
	echo "Debes indicar la URL del repositorio Git."
	echo "Ejemplo SSH: bash deploy.sh git@github.com:usuario/linker1.git"
	echo "Ejemplo HTTPS con token: bash deploy.sh https://github.com/usuario/linker1.git"
	exit 1
fi

echo "=== Despliegue de Linker1 en OCI ==="

# 1. Actualizar el sistema
echo "✓ Actualizando sistema..."
sudo apt update
sudo apt upgrade -y

# 2. Instalar Git, Maven y Java 21
echo "✓ Instalando Git, Maven y Java..."
sudo apt install -y git maven openjdk-21-jdk

# 3. Crear directorios de la aplicación
echo "✓ Creando directorios..."
sudo mkdir -p "$APP_DIR"
sudo mkdir -p "$DB_DIR"

# 4. Clonar o actualizar el repositorio
if [ ! -d "$REPO_DIR/.git" ]; then
	echo "✓ Clonando repositorio..."
	git clone "$REPO_URL" "$REPO_DIR"
else
	echo "✓ Actualizando repositorio existente..."
	git -C "$REPO_DIR" pull --ff-only
fi

cd "$REPO_DIR"

# 5. Compilar la aplicación en la VM
echo "✓ Compilando la aplicación en la VM..."
mvn clean package

JAR_FILE=$(find target -maxdepth 1 -name '*-jar-with-dependencies.jar' | head -n 1)
if [ -z "$JAR_FILE" ]; then
	echo "No se encontró el JAR generado en target/."
	exit 1
fi

sudo cp "$JAR_FILE" "$APP_DIR/linker1.jar"
sudo chown -R $(whoami):$(whoami) "$APP_DIR"
sudo chown -R $(whoami):$(whoami) "$DB_DIR"

# 6. Crear archivo de servicio systemd
echo "✓ Creando servicio systemd..."
sudo tee /etc/systemd/system/linker1.service > /dev/null <<EOF
[Unit]
Description=Linker1 URL Shortener
After=network.target

[Service]
Type=simple
WorkingDirectory=$APP_DIR
ExecStart=/usr/bin/java -jar $APP_DIR/linker1.jar
Restart=always
RestartSec=10
User=$(whoami)
Environment="LINKER_PORT=8000"
Environment="LINKER_DB_PATH=$DB_DIR/linker1.db"

[Install]
WantedBy=multi-user.target
EOF

# 7. Recargar systemd y habilitar el servicio
echo "✓ Habilitando servicio..."
sudo systemctl daemon-reload
sudo systemctl enable linker1.service

# 8. Iniciar la aplicación
echo "✓ Iniciando aplicación..."
sudo systemctl start linker1.service

# 9. Verificar estado
echo ""
echo "=== Estado del servicio ==="
sudo systemctl status linker1.service --no-pager

echo ""
echo "✓ Despliegue completado."
echo "  - Compilado en la VM con Maven"
echo "  - Repositorio: $REPO_URL"
echo "  - App escuchando en puerto 8000"
echo "  - Base de datos en: $DB_DIR/linker1.db"
echo "  - Logs: sudo journalctl -u linker1.service -f"
echo "  - Reiniciar: sudo systemctl restart linker1.service"
