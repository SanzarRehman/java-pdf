#!/bin/sh
set -eu

RENDERER_PORT="${RENDERER_SERVER_PORT:-3001}"
RENDERER_HOST="${RENDERER_SERVER_HOST:-127.0.0.1}"
RENDERER_URL="http://${RENDERER_HOST}:${RENDERER_PORT}"

echo "Starting renderer-server on ${RENDERER_URL} (pool=${RENDERER_POOL_SIZE:-2})"
node /app/scripts/renderer-server.js &
RENDERER_PID=$!

# Wait briefly for renderer server
tries=30
while [ $tries -gt 0 ]; do
  if curl -fsS "${RENDERER_URL}/health" >/dev/null 2>&1; then
    echo "renderer-server is healthy"
    break
  fi
  tries=$((tries-1))
  sleep 0.2
done

if [ $tries -eq 0 ]; then
  echo "renderer-server failed to become healthy" >&2
  kill -TERM "$RENDERER_PID" >/dev/null 2>&1 || true
  exit 1
fi

echo "Starting Spring Boot app"
java $JAVA_OPTS -jar /app/app.jar &
JAVA_PID=$!

term_handler() {
  echo "Received termination signal, shutting down..."
  kill -TERM "$JAVA_PID" >/dev/null 2>&1 || true
  kill -TERM "$RENDERER_PID" >/dev/null 2>&1 || true
  wait "$JAVA_PID" >/dev/null 2>&1 || true
  wait "$RENDERER_PID" >/dev/null 2>&1 || true
  exit 0
}

trap term_handler INT TERM

wait "$JAVA_PID"
status=$?

echo "Spring Boot exited (code=$status), stopping renderer-server"
kill -TERM "$RENDERER_PID" >/dev/null 2>&1 || true
wait "$RENDERER_PID" >/dev/null 2>&1 || true
exit "$status"
