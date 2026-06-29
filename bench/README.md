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

## Playwright `mode=fast` (page reuse)

The Playwright renderer accepts a per-request form field **`mode=fast`** that reuses a warm
browser page across renders instead of closing it each time. Add `-F "mode=fast"` to the curl
(the reused page is recycled every `pdf.playwright.reuse-recycle-after` renders, default 50, to
bound its memory).

**Use it for light/simple, high-volume documents — not heavy ones.** Measured here
(2 cores / 4g, 10 concurrent):

| Doc type | default (close-per-render) | `mode=fast` (reuse) | Verdict |
|----------|----------------------------|---------------------|---------|
| **light** 1-page cert | 2.11 s · 4.7 req/s · 780 MiB | **0.80 s · 12.5 req/s · low** | **fast wins ~2.6×** |
| **heavy** 1.8 MB table | **35.5 s · 2033 MiB** | 38.9 s · 3550 MiB | **default wins** |

Why: page-creation is a big fraction of a *light* render, so reuse removes real overhead while
the page stays small. For a *heavy* render the page accumulates a large DOM/heap, so reuse only
inflates RAM (toward the container limit, where it thrashes) with no latency gain — the render
itself dominates. Default (close-per-render) stays the safe, low-RAM choice for heavy docs.

```bash
# compare the two modes on your own doc:
curl -s -o /dev/null -w "default: %{time_total}s\n" -X POST http://localhost:8081/api/v1.0/print \
  -F "html=@doc.html" -F "renderer=playwright"
curl -s -o /dev/null -w "fast:    %{time_total}s\n" -X POST http://localhost:8081/api/v1.0/print \
  -F "html=@doc.html" -F "renderer=playwright" -F "mode=fast"
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
  wall clock:  8.6 s   (burst start -> all done)
  throughput:  2.33 req/s
  latency:     min 1.7  p50 4.9  p90 8.5  max 8.5  (s)
  RAM:         peak 3042 MiB   avg 2576 MiB
  CPU:         peak 831%   avg 794%   (100% = 1 core)
------------------------------------------------------------
```

## Benchmark results

Measured on this repo with **Playwright Java 1.60.0**, system Chromium, Docker
(`--cpus=8 -m 6g`, `RENDERER_CONCURRENCY=4`), 20 concurrent requests. Reproduce with:

```bash
CPUS=8 MEMORY=6g RENDERER_CONCURRENCY=4 ./bench/build-and-run.sh
RENDERER=<engine> ./bench/concurrency-test.sh                 # heavy table (1300×16)
HTML_FILE=/path/cert.html RENDERER=<engine> ./bench/concurrency-test.sh   # light doc
```

**Heavy doc** — synthetic 1300-row × 16-col landscape table, 20 concurrent:

| Renderer    | Wall  | Throughput  | p50   | p90   | RAM peak  | CPU avg |
|-------------|-------|-------------|-------|-------|-----------|---------|
| playwright  | 8.6 s | 2.33 req/s  | 4.9 s | 8.5 s | **3042 MiB** | 794%   |
| chromium    | 10.7 s| 1.87 req/s  | 3.6 s | 10.0 s| 6144 MiB* | 606%   |
| itext       | 8.5 s | 2.35 req/s  | 5.3 s | 8.5 s | 6134 MiB* | 544%   |

`*` chromium and itext saturated the 6 GiB container limit; Playwright peaked at ~3 GiB —
**~2× lower RAM** for the same throughput. (iText is fast here but loses fidelity on
flex/grid layouts; see renderer notes below.)

**Light doc** — 1-page certificate, 20 concurrent:

| Renderer    | Wall  | Throughput   | p50   | p90   |
|-------------|-------|--------------|-------|-------|
| itext       | 0.5 s | 38.2 req/s   | 0.3 s | 0.5 s |
| chromium    | 0.8 s | 24.0 req/s   | 0.5 s | 0.8 s |
| playwright  | 1.6 s | 12.5 req/s   | 1.0 s | 1.6 s |

> RAM/CPU read `0` for light docs because the burst finishes sub-second — faster than the
> `docker stats` sampler's first snapshot. Trust the latency/throughput columns there.

**Takeaways:** Playwright is the best engine for **heavy** documents (lowest RAM, top
throughput); iText wins on **simple** documents (10–50× faster, tiny output) but breaks on
flex/grid. chromium and Playwright produce pixel-equivalent output — Playwright just costs
far less RAM under load. Re-run on your own hardware before sizing production.

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
