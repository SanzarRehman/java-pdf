#!/usr/bin/env bash
#
# Starts the persistent warm Chromium renderer-server (low-RAM mode):
#   - ONE shared Chromium kept warm
#   - pages reused across jobs (navigated, not recreated)
#   - concurrency capped to 1-2
#
# It auto-detects a Chrome/Chromium binary, waits for /health, and prints the URL
# to export as RENDERER_SERVER_URL for the Spring app.
#
# Usage:
#   ./start-renderer.sh                 # foreground
#   ./start-renderer.sh &               # background
#
# Tunables (env vars):
#   RENDERER_SERVER_PORT   (default 3001)
#   RENDERER_CONCURRENCY   (default 2; clamp 1-2; set 1 for absolute min RAM)
#   PUPPETEER_EXECUTABLE_PATH  (override Chrome path)
#
set -euo pipefail
cd "$(dirname "$0")"

PORT="${RENDERER_SERVER_PORT:-3001}"
export RENDERER_SERVER_PORT="$PORT"
export RENDERER_CONCURRENCY="${RENDERER_CONCURRENCY:-2}"

# --- locate a Chrome/Chromium binary if not already provided ---
if [ -z "${PUPPETEER_EXECUTABLE_PATH:-}" ]; then
  for c in \
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
    "/Applications/Chromium.app/Contents/MacOS/Chromium" \
    "/usr/bin/google-chrome" \
    "/usr/bin/chromium" \
    "/usr/bin/chromium-browser"; do
    if [ -x "$c" ]; then PUPPETEER_EXECUTABLE_PATH="$c"; break; fi
  done
fi
export PUPPETEER_EXECUTABLE_PATH="${PUPPETEER_EXECUTABLE_PATH:-}"
if [ -z "$PUPPETEER_EXECUTABLE_PATH" ]; then
  echo "ERROR: No Chrome/Chromium found. Set PUPPETEER_EXECUTABLE_PATH." >&2
  exit 1
fi

SCRIPT="src/main/resources/scripts/renderer-server.js"
echo "Starting renderer-server: port=$PORT concurrency=$RENDERER_CONCURRENCY chrome=$PUPPETEER_EXECUTABLE_PATH"
exec node "$SCRIPT"
