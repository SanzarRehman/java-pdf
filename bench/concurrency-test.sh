#!/usr/bin/env bash
#
# concurrency-test.sh — fire N concurrent HTML->PDF requests at a running container,
# sample its RAM/CPU during the burst, and print a full report (success, drain time,
# latency p50/p90/max, RAM peak/avg, CPU peak/avg, throughput). Linux-friendly.
#
# Prereq: container started via build-and-run.sh (or any running instance).
#
# Usage:
#   ./bench/concurrency-test.sh
#   RENDERER=chromium REQUESTS=20 ./bench/concurrency-test.sh
#   HTML_FILE=/path/to/real.html ./bench/concurrency-test.sh
#
# Env (all optional):
#   HOST/PORT       service location          (default localhost / 8081)
#   CONTAINER       container name for stats  (default easyjavapdf-bench)
#   RENDERER        itext | chromium | playwright   (default playwright)
#   REQUESTS        concurrent requests       (default 20)
#   HTML_FILE       input HTML                (default ./bench/sample.html, auto-generated)
#   ROWS            rows in generated table   (default 1300)
#   MAXTIME         per-request curl timeout  (default 180s)
#   WARMUP=0        skip warmup renders
#
set -euo pipefail
cd "$(dirname "$0")"

HOST="${HOST:-localhost}"
PORT="${PORT:-8081}"
CONTAINER="${CONTAINER:-easyjavapdf-bench}"
RENDERER="${RENDERER:-playwright}"
REQUESTS="${REQUESTS:-20}"
HTML_FILE="${HTML_FILE:-./sample.html}"
ROWS="${ROWS:-1300}"
MAXTIME="${MAXTIME:-180}"
URL="http://${HOST}:${PORT}/api/v1.0/print"

# --- generate a synthetic wide/heavy table if no input HTML exists ---
gen_sample() {
  local out="$1" rows="$2"
  {
    echo '<html><head><meta charset="utf-8"><style>'
    echo '@page page0 { margin:0.25in; size: landscape; }'
    echo 'body{font-family:Calibri,Arial,sans-serif;font-size:8pt}'
    echo 'table{border-collapse:collapse;width:100%}td,th{border:1px solid #888;padding:1px 3px;white-space:nowrap}'
    echo 'th{background:#eee}'
    echo '</style></head><body>'
    echo '<h3 style="text-align:center">Benchmark Report — Synthetic Wide Table</h3>'
    echo '<table><thead><tr>'
    for c in SL StudentID Name Gender Mobile Email EnrolledYear Semester Program CGPA Credits Status LastSemester StudentStatus Completion Address; do
      echo "<th>$c</th>"
    done
    echo '</tr></thead><tbody>'
    awk -v n="$rows" 'BEGIN{
      for(i=1;i<=n;i++){
        printf "<tr><td>%d</td><td>10000%05d</td><td>Student Name %d</td><td>%s</td>",i,i,i,(i%2?"Male":"Female");
        printf "<td>0111%07d</td><td>user%05d@example.com</td><td>2025</td><td>SPRING</td><td>CSE</td>",i,i;
        printf "<td>%.2f</td><td>%d.00</td><td>%s</td><td>FALL 2025</td><td>%s</td><td>Incomplete</td><td>addr-%05d@example.com</td></tr>\n",
               (i%400)/100.0, (i%24), (i%3?"Incomplete":"Complete"), (i%2?"Active":"Inactive"), i;
      }
    }'
    echo '</tbody></table></body></html>'
  } > "$out"
}

if [ ! -f "$HTML_FILE" ]; then
  echo "No HTML at '$HTML_FILE' — generating a synthetic ${ROWS}-row x16-col table..."
  gen_sample "$HTML_FILE" "$ROWS"
fi
HTML_BYTES=$(wc -c < "$HTML_FILE" | tr -d ' ')

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "============================================================"
echo " EasyJavaPdf concurrency benchmark"
echo "   url=$URL  renderer=$RENDERER  requests=$REQUESTS"
echo "   html=$HTML_FILE (${HTML_BYTES} bytes)  container=$CONTAINER"
echo "============================================================"

# --- container resource limits (for context) ---
if docker inspect "$CONTAINER" >/dev/null 2>&1; then
  NANO=$(docker inspect -f '{{.HostConfig.NanoCpus}}' "$CONTAINER" 2>/dev/null || echo 0)
  MEML=$(docker inspect -f '{{.HostConfig.Memory}}' "$CONTAINER" 2>/dev/null || echo 0)
  CPUS_LIM=$(awk -v n="$NANO" 'BEGIN{printf (n>0)?n/1e9:0}')
  MEM_LIM=$(awk -v m="$MEML" 'BEGIN{printf (m>0)?m/1024/1024/1024:0}')
  echo "Limits:    cpus=${CPUS_LIM:-unset}  memory=${MEM_LIM:-unset}GiB"
fi

# --- warm up the worker threads so the first measured batch isn't a cold start ---
if [ "${WARMUP:-1}" = "1" ]; then
  WARM=$(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$CONTAINER" 2>/dev/null | awk -F= '/^RENDERER_CONCURRENCY=/{print $2}')
  WARM="${WARM:-2}"
  echo "Warming up $WARM worker(s)..."
  for _ in $(seq 1 "$WARM"); do
    curl -s -X POST "$URL" -F "html=@${HTML_FILE}" -F "renderer=$RENDERER" -o /dev/null >/dev/null 2>&1 &
  done
  wait
fi

# --- start a RAM/CPU sampler in the background (independent of the request burst) ---
SAMPLES="$WORK/stats.txt"
: > "$SAMPLES"
( while :; do
    docker stats "$CONTAINER" --no-stream --format "{{.CPUPerc}} {{.MemUsage}}" 2>/dev/null >> "$SAMPLES" || break
  done ) &
SAMPLER=$!
disown "$SAMPLER" 2>/dev/null || true   # avoid "Terminated" job message on kill

# --- fire the concurrent burst; wait ONLY on request PIDs (not the sampler) ---
echo "Firing $REQUESTS concurrent requests..."
START=$(date +%s.%N)
pids=()
for n in $(seq 1 "$REQUESTS"); do
  ( curl -s --max-time "$MAXTIME" -X POST "$URL" \
      -F "html=@${HTML_FILE}" -F "renderer=$RENDERER" \
      -o /dev/null -w "%{http_code} %{time_total} %{size_download}\n" > "$WORK/r-$n.txt" 2>&1 ) &
  pids+=("$!")
done
for p in "${pids[@]}"; do wait "$p" || true; done
END=$(date +%s.%N)
kill "$SAMPLER" >/dev/null 2>&1 || true

cat "$WORK"/r-*.txt > "$WORK/all.txt"

# --- compute & print the report (awk only; portable) ---
awk -v start="$START" -v end="$END" -v req="$REQUESTS" -v renderer="$RENDERER" '
  function pct(p,   idx){ if(nt==0)return 0; idx=int(nt*p); if(idx>=nt)idx=nt-1; return times[idx] }
  function maxof(a,len,  i,mx){ mx=0; for(i=0;i<len;i++) if(a[i]>mx) mx=a[i]; return mx }
  function avgof(a,len,  i,s){ s=0; for(i=0;i<len;i++) s+=a[i]; return (len? s/len:0) }
  FNR==NR {                                  # first file: results (code time size)
    code[NR]=$1; if($1=="200"){ok++; times[nt++]=$2+0}; n++; next
  }
  {                                          # second file: stats (cpu% mem unit /...)
    c=$1; sub(/%/,"",c); cpu[ncpu++]=c+0
    m=$2
    if (m ~ /GiB/) { sub(/GiB/,"",m); mem[nmem++]=m*1024 }
    else if (m ~ /MiB/) { sub(/MiB/,"",m); mem[nmem++]=m+0 }
    else if (m ~ /KiB/) { sub(/KiB/,"",m); mem[nmem++]=m/1024 }
  }
  END {
    # sort latencies (insertion sort; small N)
    for(i=1;i<nt;i++){v=times[i];j=i-1;while(j>=0&&times[j]>v){times[j+1]=times[j];j--}times[j+1]=v}
    wall=end-start
    printf "\n------------------------------------------------------------\n"
    printf " RESULT (%s)\n", renderer
    printf "------------------------------------------------------------\n"
    printf "  success:     %d / %d   (HTTP 200)\n", ok, req
    printf "  wall clock:  %.1f s   (burst start -> all done)\n", wall
    printf "  throughput:  %.2f req/s\n", (wall>0? req/wall:0)
    printf "  latency:     min %.1f  p50 %.1f  p90 %.1f  max %.1f  (s)\n", (nt?times[0]:0), pct(0.5), pct(0.9), (nt?times[nt-1]:0)
    printf "  RAM:         peak %.0f MiB   avg %.0f MiB\n", maxof(mem,nmem), avgof(mem,nmem)
    printf "  CPU:         peak %.0f%%   avg %.0f%%   (100%% = 1 core)\n", maxof(cpu,ncpu), avgof(cpu,ncpu)
    bad=req-ok; if(bad>0){ printf "  non-200:     %d  (codes:", bad; for(i=1;i<=n;i++) if(code[i]!="200") printf " %s", code[i]; printf ")\n" }
    printf "------------------------------------------------------------\n"
  }
' "$WORK/all.txt" "$SAMPLES"

echo
echo "Per-request (code  time_s  bytes):"
sort -k2 -n "$WORK/all.txt" | nl -w2 -s'  '
