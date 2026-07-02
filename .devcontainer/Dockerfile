FROM mcr.microsoft.com/devcontainers/java:1-21-bookworm

# Keep the container aligned with the project's Java 21 + Maven toolchain.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        sqlite3 \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*
