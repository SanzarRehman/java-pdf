
# =============================================================================
# EasyJavaPdf Docker Image
# Full PDF generation with Java + Node.js + Puppeteer/Chromium
# Using Alpine with proper Chromium configuration
# =============================================================================

# Stage 1: Build the Java application
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app
COPY . .

# Build the application (skip tests for faster build)
RUN ./gradlew bootJar -x test --no-daemon

# Stage 1.5: Download Microsoft Core Fonts on Debian (ttf-mscorefonts-installer not available on Alpine)
FROM debian:bullseye-slim AS font-downloader
RUN echo "ttf-mscorefonts-installer msttcorefonts/accepted-mscorefonts-eula select true" | debconf-set-selections && \
    echo "deb http://deb.debian.org/debian bullseye contrib" >> /etc/apt/sources.list && \
    apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        fontconfig \
        libfreetype6 \
        ttf-mscorefonts-installer \
        xfonts-75dpi \
        xfonts-base && \
    rm -rf /var/lib/apt/lists/*

# Stage 2: Production image
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="SanzarRehman"
LABEL description="EasyJavaPdf - High-performance PDF generation service with Chromium/Puppeteer support"

# Install required packages
RUN apk update && apk add --no-cache \
    # Node.js and npm
    nodejs \
    npm \
    # Chromium browser
    chromium \
    # Fonts for proper PDF rendering
    fontconfig \
    freetype \
    ttf-dejavu \
    ttf-liberation \
    font-noto \
    font-noto-cjk \
    # PDF utilities for merging
    poppler-utils \
    # Additional dependencies for Chromium
    nss \
    harfbuzz \
    ca-certificates \
    # Utilities
    curl \
    dumb-init \
    # dbus for some Chrome features (helps prevent crashes)
    dbus \
    && rm -rf /var/cache/apk/*

# Copy Microsoft Core Fonts (Arial, Times New Roman, etc.) from Debian stage into Alpine
COPY --from=font-downloader /usr/share/fonts/truetype/msttcorefonts/ /usr/share/fonts/msttcorefonts/
RUN fc-cache -f -v

# Set Puppeteer environment variables - Alpine uses 'chromium' not 'chromium-browser'
ENV PUPPETEER_SKIP_CHROMIUM_DOWNLOAD=true \
    PUPPETEER_EXECUTABLE_PATH=/usr/bin/chromium-browser \
    CHROME_PATH=/usr/bin/chromium-browser \
    CHROMIUM_FLAGS="--no-sandbox --disable-dev-shm-usage --disable-gpu --disable-software-rasterizer --no-zygote" \
    NODE_PATH=/app/node_modules \
    PDF_SCRIPTS_PATH=/app/scripts

# Prefer renderer-server (in-container) over spawning node per request
ENV PDF_CHROMIUM_NODE_PATH=/usr/bin/node \
    PDF_CHROMIUM_RENDERER_SERVER_URL=http://127.0.0.1:3001 \
    RENDERER_SERVER_HOST=127.0.0.1 \
    RENDERER_SERVER_PORT=3001 \
    RENDERER_POOL_SIZE=1 \
    PDF_CHROMIUM_PARALLEL_WORKERS=2 \
    PDF_CHROMIUM_MAX_PROCESSES=4 \
    PDF_CHROMIUM_CHUNKED_DEFAULT_PARALLELISM=1

# Create app directory
WORKDIR /app

# Copy package.json and install Node.js dependencies
COPY package.json package-lock.json* ./
RUN npm ci --omit=dev || npm install --omit=dev

# Copy Puppeteer scripts
COPY src/main/resources/scripts/ ./scripts/

# Docker entrypoint to run renderer-server + Spring Boot
COPY docker/entrypoint.sh /app/entrypoint.sh

# Make scripts executable and set up symlinks for module resolution
RUN chmod +x ./scripts/*.js && \
    ln -s /app/node_modules /app/scripts/node_modules

RUN chmod +x /app/entrypoint.sh

# Copy the built JAR from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Create directory for temporary files with proper permissions
RUN mkdir -p /tmp/pdf-generation && chmod 777 /tmp/pdf-generation

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/api/v1.0/health || exit 1

# Expose port
EXPOSE 8081

# JVM optimizations for container environment
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -Djava.awt.headless=true"

# Run the application
ENTRYPOINT ["dumb-init", "--", "/app/entrypoint.sh"]
