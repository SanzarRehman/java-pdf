
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
    # dbus for some Chrome features (helps prevent crashes)
    dbus \
    && rm -rf /var/cache/apk/*

# Set Puppeteer environment variables - Alpine uses 'chromium' not 'chromium-browser'
ENV PUPPETEER_SKIP_CHROMIUM_DOWNLOAD=true \
    PUPPETEER_EXECUTABLE_PATH=/usr/bin/chromium-browser \
    CHROME_PATH=/usr/bin/chromium-browser \
    CHROMIUM_FLAGS="--no-sandbox --disable-dev-shm-usage --disable-gpu --disable-software-rasterizer --no-zygote" \
    NODE_PATH=/app/node_modules \
    PDF_SCRIPTS_PATH=/app/scripts

# Create app directory
WORKDIR /app

# Copy package.json and install Node.js dependencies
COPY package.json package-lock.json* ./
RUN npm ci --omit=dev || npm install --omit=dev

# Copy Puppeteer scripts
COPY src/main/resources/scripts/ ./scripts/

# Make scripts executable and set up symlinks for module resolution
RUN chmod +x ./scripts/*.js && \
    ln -s /app/node_modules /app/scripts/node_modules

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
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
