#!/usr/bin/env bash
#
# build-and-run.sh — build the EasyJavaPdf image and start it with a CPU/RAM/concurrency
# profile, ready for the concurrency benchmark. Linux-friendly (works on any Docker host).
#
# Usage:
#   ./bench/build-and-run.sh
#   CPUS=8 MEMORY=6g RENDERER_CONCURRENCY=4 ./bench/build-and-run.sh
#   SKIP_BUILD=1 ./bench/build-and-run.sh        # reuse existing image
#
# Env (all optional):
#   IMAGE                docker image tag        (default easyjavapdf:bench)
#   NAME                 container name          (default easyjavapdf-bench)
#   PORT                 host port -> 8081       (default 8081)
#   CPUS                 --cpus limit            (default 4)
#   MEMORY               -m memory limit         (default 3g)
#   RENDERER_CONCURRENCY parallel renders        (default 2; keep ~= CPUS/2 for heavy docs)
#   SKIP_BUILD=1         skip docker build
#
set -euo pipefail
cd "$(dirname "$0")/.."   # repo root

IMAGE="${IMAGE:-easyjavapdf:bench}"
NAME="${NAME:-easyjavapdf-bench}"
PORT="${PORT:-8081}"
CPUS="${CPUS:-4}"
MEMORY="${MEMORY:-3g}"
CONC="${RENDERER_CONCURRENCY:-2}"

if [ "${SKIP_BUILD:-0}" != "1" ]; then
  echo "[1/3] Building $IMAGE (this can take a few minutes on first run)..."
  docker build -t "$IMAGE" .
else
  echo "[1/3] SKIP_BUILD=1 — reusing $IMAGE"
fi

echo "[2/3] Removing any old container named $NAME..."
docker rm -f "$NAME" >/dev/null 2>&1 || true

echo "[3/3] Starting $NAME  (cpus=$CPUS  mem=$MEMORY  concurrency=$CONC  port=$PORT)"
docker run -d --rm --name "$NAME" \
  -p "${PORT}:8081" \
  --cpus="$CPUS" -m "$MEMORY" \
  -e RENDERER_CONCURRENCY="$CONC" \
  -e PDF_EXECUTOR_CORE_SIZE="$CONC" \
  -e PDF_EXECUTOR_MAX_SIZE="$CONC" \
  -e PDF_CHROMIUM_PARALLEL_WORKERS="$CONC" \
  -e PDF_CHROMIUM_MAX_PROCESSES="$CONC" \
  "$IMAGE" >/dev/null

printf "Waiting for health"
for _ in $(seq 1 90); do
  if curl -sf "http://localhost:${PORT}/api/v1.0/health" >/dev/null 2>&1; then
    printf " — UP\n"; break
  fi
  printf "."; sleep 1
done

if ! curl -sf "http://localhost:${PORT}/api/v1.0/health" >/dev/null 2>&1; then
  echo "ERROR: service did not become healthy. Logs:" >&2
  docker logs "$NAME" 2>&1 | tail -30 >&2
  exit 1
fi

echo "Health:    $(curl -s "http://localhost:${PORT}/api/v1.0/health")"
echo "Container: $(docker ps --filter "name=$NAME" --format '{{.Names}} ({{.Status}})')"
echo
echo "Ready. Run the benchmark:"
echo "  CONTAINER=$NAME PORT=$PORT RENDERER=playwright ./bench/concurrency-test.sh"
