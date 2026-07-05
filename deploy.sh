#!/bin/bash
set -euo pipefail

REPO_URL="${1:-${REPO_URL:-}}"
WORKDIR="$(pwd)"
APP_DIR="/opt/linker1"
DB_DIR="/var/lib/linker1"
WEB_DIR="/var/www/linker1"

if [ -f "$WORKDIR/pom.xml" ] && [ -d "$WORKDIR/src" ]; then
  REPO_DIR="$WORKDIR"
else
  REPO_DIR="$WORKDIR/linker1"
fi

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
sudo apt install -y git maven openjdk-21-jdk nginx

# 3. Crear directorios
echo "✓ Creating directories..."
sudo mkdir -p "$APP_DIR"
sudo mkdir -p "$DB_DIR"
sudo mkdir -p "$WEB_DIR"

# 4. Preparar fuente
if [ "$REPO_DIR" = "$WORKDIR" ]; then
  echo "✓ Using current repository directory..."
  if [ -d "$REPO_DIR/.git" ]; then
    if git -C "$REPO_DIR" symbolic-ref -q HEAD > /dev/null; then
      git -C "$REPO_DIR" pull --ff-only
    else
      echo "✓ HEAD desacoplado (ej. tras un rollback a un tag); no se hace pull."
    fi
  fi
else
  if [ ! -d "$REPO_DIR/.git" ]; then
    echo "✓ Cloning repo..."
    git clone "$REPO_URL" "$REPO_DIR"
  else
    echo "✓ Updating repo..."
    if git -C "$REPO_DIR" symbolic-ref -q HEAD > /dev/null; then
      git -C "$REPO_DIR" pull --ff-only
    else
      echo "✓ HEAD desacoplado (ej. tras un rollback a un tag); no se hace pull."
    fi
  fi
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

# 6. Frontend
echo "✓ Deploying frontend..."
sudo cp -r public/* "$WEB_DIR/"
sudo chown -R www-data:www-data "$WEB_DIR"

# 7. Systemd service
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

# 8. NGINX CONFIG (FIXED)
echo "✓ Configuring Nginx..."
sudo rm -f /etc/nginx/sites-enabled/default

sudo tee /etc/nginx/sites-available/linker1 > /dev/null <<EOF
server {
    listen 80 default_server;
    server_name _;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
}
EOF

sudo ln -sf /etc/nginx/sites-available/linker1 /etc/nginx/sites-enabled/linker1
sudo nginx -t

# 9. Restart services
echo "✓ Reloading systemd..."
sudo systemctl daemon-reload
sudo systemctl enable linker1.service

echo "✓ Starting services..."
sudo systemctl restart linker1.service
sudo systemctl restart nginx

# 10. Status
echo ""
echo "=== SERVICE STATUS ==="
sudo systemctl status linker1.service --no-pager

echo ""
echo "=== DONE ==="
echo "App running on: PORT 8080"
echo "Local test: curl http://localhost:8080"
echo "Public test: http://1.n-la-c.app:8080"
echo ""
echo "Logs: sudo journalctl -u linker1.service -f"