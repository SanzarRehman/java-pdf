#!/usr/bin/env bash
#
# One-command local startup for EasyJavaPdf in low-RAM Chromium mode.
#
#   1. starts the persistent warm renderer-server (one shared Chromium, reused pages)
#   2. waits for it to become healthy
#   3. starts the Spring Boot app pointed at it (pdf.renderer=chromium)
#   4. stops the renderer-server when the app exits
#
# Usage:
#   ./run.sh                 # default port 8081
#   SERVER_PORT=9090 ./run.sh
#
# Tunables: SERVER_PORT, RENDERER_SERVER_PORT, RENDERER_CONCURRENCY, PUPPETEER_EXECUTABLE_PATH
#
set -euo pipefail
cd "$(dirname "$0")"

SERVER_PORT="${SERVER_PORT:-8081}"
RENDERER_SERVER_PORT="${RENDERER_SERVER_PORT:-3001}"
export RENDERER_SERVER_PORT
export RENDERER_CONCURRENCY="${RENDERER_CONCURRENCY:-2}"

# --- start the warm renderer-server in the background ---
./start-renderer.sh >/tmp/renderer-server.log 2>&1 &
RENDERER_PID=$!
echo "renderer-server starting (pid $RENDERER_PID), log: /tmp/renderer-server.log"

cleanup() {
  echo "Stopping renderer-server (pid $RENDERER_PID)..."
  kill "$RENDERER_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# --- wait for /health ---
HEALTH="http://127.0.0.1:${RENDERER_SERVER_PORT}/health"
for i in $(seq 1 30); do
  if curl -sf "$HEALTH" >/dev/null 2>&1; then
    echo "renderer-server healthy at $HEALTH"
    break
  fi
  if ! kill -0 "$RENDERER_PID" 2>/dev/null; then
    echo "ERROR: renderer-server died on startup. Log:" >&2
    cat /tmp/renderer-server.log >&2
    exit 1
  fi
  sleep 1
done

# --- start the Spring app pointed at the warm server ---
export RENDERER_SERVER_URL="http://127.0.0.1:${RENDERER_SERVER_PORT}"
export PDF_RENDERER="chromium"
echo "Starting app on :${SERVER_PORT} with RENDERER_SERVER_URL=$RENDERER_SERVER_URL"
./gradlew bootRun --args="--server.port=${SERVER_PORT}"
