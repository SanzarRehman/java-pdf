# Benchmark scripts

Two Linux-friendly scripts to build/run the service and measure it under concurrent load.
They print **time, RAM, CPU, latency percentiles, and throughput** so you can pick the right
renderer (`playwright` / `chromium` / `itext`) and `concurrency × cores × memory` profile
**on your own production hardware** (numbers differ from a dev laptop / Docker Desktop).

## Prerequisites
- Docker, `bash`, `curl`, `awk` (all standard on Linux).
- Run from the repo root or anywhere; scripts resolve their own paths.

## 1. Build & run

```bash
./bench/build-and-run.sh
```

Builds the image and starts a container with a CPU/RAM/concurrency profile. Override via env:

| Env | Default | Meaning |
|-----|---------|---------|
| `CPUS` | `4` | `--cpus` limit |
| `MEMORY` | `3g` | `-m` memory limit |
| `RENDERER_CONCURRENCY` | `2` | parallel renders (keep ≈ `CPUS/2` for heavy docs) |
| `PORT` | `8081` | host port |
| `IMAGE` / `NAME` | `easyjavapdf:bench` / `easyjavapdf-bench` | image tag / container name |
| `SKIP_BUILD` | – | `SKIP_BUILD=1` reuses the existing image |

Examples:
```bash
CPUS=8 MEMORY=6g RENDERER_CONCURRENCY=4 ./bench/build-and-run.sh
SKIP_BUILD=1 CPUS=4 ./bench/build-and-run.sh        # re-run, no rebuild
```

## 2. Concurrency test

```bash
./bench/concurrency-test.sh
```

Fires N concurrent requests, samples the container's RAM/CPU during the burst, and prints a
full report. Override via env:

| Env | Default | Meaning |
|-----|---------|---------|
| `RENDERER` | `playwright` | `playwright` \| `chromium` \| `itext` |
| `REQUESTS` | `20` | concurrent requests |
| `HTML_FILE` | `./bench/sample.html` | input HTML (auto-generated if missing) |
| `ROWS` | `1300` | rows in the generated table (render weight) |
| `PORT` / `HOST` / `CONTAINER` | `8081` / `localhost` / `easyjavapdf-bench` | target |
| `MAXTIME` | `180` | per-request curl timeout (s) |

> No input HTML? The script generates a synthetic **1300-row × 16-column** wide table
> (landscape, borders) that approximates a heavy spreadsheet export. Point `HTML_FILE` at
> your real document for a representative test.

Examples:
```bash
RENDERER=playwright ./bench/concurrency-test.sh
RENDERER=chromium  REQUESTS=20 ./bench/concurrency-test.sh
HTML_FILE=/data/report.html RENDERER=playwright ./bench/concurrency-test.sh
```

## End-to-end (compare two renderers at 8 cores)

```bash
CPUS=8 MEMORY=6g RENDERER_CONCURRENCY=4 ./bench/build-and-run.sh
RENDERER=playwright ./bench/concurrency-test.sh
RENDERER=chromium   ./bench/concurrency-test.sh
```

## Sample output

```
------------------------------------------------------------
 RESULT (playwright)
------------------------------------------------------------
  success:     20 / 20   (HTTP 200)
  wall clock:  16.5 s   (burst start -> all done)
  throughput:  1.21 req/s
  latency:     min 1.3  p50 8.7  p90 14.0  max 16.5  (s)
  RAM:         peak 1716 MiB   avg 839 MiB
  CPU:         peak 627%   avg 153%   (100% = 1 core)
------------------------------------------------------------
```

## Reading the results
- **latency under burst = queue wait.** With concurrency C and per-render time R, request k
  waits ~`(k/C)·R`. It is *not* slow rendering — a single warm render is ~1–2 s.
- **Heavy docs are CPU-bound.** A 20k-cell table costs ~1–2 CPU-seconds; burst latency is
  bounded by `total_work / cores`. More concurrency than the box can feed **hurts** (renders
  time-slice and each slows down).
- **Tune concurrency to cores, then scale out.** Keep `RENDERER_CONCURRENCY ≈ cores/2` for
  heavy docs; for more burst capacity add **replicas**, not threads.
- **Playwright vs chromium:** same output, but Playwright (JVM→CDP, no Node sidecar) uses
  far less RAM and is faster under load in our tests — re-confirm on your hardware.
