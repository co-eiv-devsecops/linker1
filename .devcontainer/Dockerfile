FROM mcr.microsoft.com/devcontainers/java:1-21-bookworm

# Elimina el repositorio de Yarn que provoca errores de firmas GPG
RUN rm -f /etc/apt/sources.list.d/yarn.list

# Instala las herramientas necesarias para el proyecto
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        maven \
        sqlite3 \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*