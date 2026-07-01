#!/bin/bash
set -euo pipefail

REPO_URL="${1:-${REPO_URL:-}}"
REPO_DIR="linker1"
APP_DIR="/opt/linker1"
DB_DIR="/var/lib/linker1"

if [ -z "$REPO_URL" ]; then
  echo "Uso: bash deploy.sh <REPO_URL>"
  exit 1
fi

echo "=== LINKER DEPLOY ==="

# 1. Actualizar sistema
echo "✓ Updating system..."
sudo apt update -y
sudo apt upgrade -y

# 2. Instalar dependencias
echo "✓ Installing dependencies..."
sudo apt install -y git maven openjdk-21-jdk

# 3. Crear directorios
echo "✓ Creating directories..."
sudo mkdir -p "$APP_DIR"
sudo mkdir -p "$DB_DIR"

# 4. Clonar repo
if [ ! -d "$REPO_DIR/.git" ]; then
  echo "✓ Cloning repo..."
  git clone "$REPO_URL" "$REPO_DIR"
else
  echo "✓ Updating repo..."
  git -C "$REPO_DIR" pull --ff-only
fi

cd "$REPO_DIR"

# 5. Build
echo "✓ Building app..."
mvn clean package

JAR_FILE=$(find target -name '*-jar-with-dependencies.jar' | head -n 1)

if [ -z "$JAR_FILE" ]; then
  echo "ERROR: JAR not found"
  exit 1
fi

echo "✓ Deploying JAR..."
sudo cp "$JAR_FILE" "$APP_DIR/linker1.jar"

sudo chown -R ubuntu:ubuntu "$APP_DIR"
sudo chown -R ubuntu:ubuntu "$DB_DIR"

echo "✓ Deploying frontend..."
sudo mkdir -p /var/www/linker1
sudo cp -r public/* /var/www/linker1/
sudo chown -R ubuntu:ubuntu /var/www/linker1

# 6. Systemd service (8080 IMPORTANT)
echo "✓ Creating systemd service..."
sudo tee /etc/systemd/system/linker1.service > /dev/null <<EOF
[Unit]
Description=Linker1 URL Shortener
After=network.target

[Service]
Type=simple
WorkingDirectory=$APP_DIR
ExecStart=/usr/bin/java -jar $APP_DIR/linker1.jar
Restart=always
RestartSec=5
User=ubuntu
Environment="LINKER_PORT=8080"
Environment="LINKER_DB_PATH=$DB_DIR/linker1.db"

[Install]
WantedBy=multi-user.target
EOF

# 7. Reload systemd
echo "✓ Reloading systemd..."
sudo systemctl daemon-reload
sudo systemctl enable linker1.service

# 8. Restart app
echo "✓ Starting service..."
sudo systemctl restart linker1.service

# 9. Status
echo ""
echo "=== SERVICE STATUS ==="
sudo systemctl status linker1.service --no-pager

echo ""
echo "=== DONE ==="
echo "App running on: PORT 8080"
echo "Local test: curl http://localhost:8080"
echo "Public test: http://1.n-la-c.app"
echo ""
echo "Logs: sudo journalctl -u linker1.service -f"