#!/usr/bin/env bash
set -Eeuo pipefail

# Runs the same HTTP workload against the same Compose data and host for the
# remote and compatibility backend modes. The dependent service containers are
# kept unchanged; only the backend mode is recreated. Each API is measured by
# the canonical load-test.mjs with three repeats, while Docker CPU/memory
# samples are retained alongside the HTTP JSON/CSV.
readonly OUTPUT_DIR="${OUTPUT_DIR:-04_tests/performance/results/nightly-$(date -u +%Y%m%dT%H%M%SZ)}"
readonly BASE_URL="${PERF_BASE_URL:-http://127.0.0.1:8080}"
readonly REQUESTS="${PERF_REQUESTS:-30}"
readonly CONCURRENCY="${PERF_CONCURRENCY:-8}"
readonly REPEATS="${PERF_REPEATS:-3}"
readonly WARMUP_REQUESTS="${PERF_WARMUP_REQUESTS:-6}"
readonly TIMEOUT_MS="${PERF_TIMEOUT_MS:-3000}"
readonly COMPOSE_PROJECT="${COMPOSE_PROJECT:-lumalife-main}"
readonly BACKEND_SERVICE="${BACKEND_SERVICE:-backend}"
readonly STACK_COMPOSE_ARGS=(-p "${COMPOSE_PROJECT}")
readonly LOAD_TEST="04_tests/performance/load-test.mjs"

mkdir -p "${OUTPUT_DIR}"
printf 'mode,api,requests,successful,failed,error_rate,throughput_rps,average_ms,p95_ms,cpu_avg_percent,cpu_max_percent,memory_avg_mib,memory_max_mib,result_json,result_csv,resources_csv\n' > "${OUTPUT_DIR}/comparison-summary.csv"
failed_runs=0

compose_backend() {
  local mode="$1"
  if [[ "${mode}" == "microservices" ]]; then
    env \
      SPRING_PROFILES_ACTIVE=prod,remote \
      LUMALIFE_IDENTITY_REMOTE_ENABLED=true \
      LUMALIFE_IDENTITY_BACKFILL_COMPLETED=true \
      LUMALIFE_MERCHANT_REMOTE_ENABLED=true \
      LUMALIFE_MERCHANT_BACKFILL_COMPLETED=true \
      LUMALIFE_ORDER_REMOTE_ENABLED=true \
      LUMALIFE_ORDER_BACKFILL_COMPLETED=true \
      LUMALIFE_ASSISTANT_REMOTE_ENABLED=true \
      LUMALIFE_ASSISTANT_BACKFILL_COMPLETED=true \
      LUMALIFE_COMPATIBILITY_STORE_ENABLED=false \
      LUMALIFE_PERSISTENCE=mysql \
      docker compose "${STACK_COMPOSE_ARGS[@]}" up --detach --wait "${BACKEND_SERVICE}"
  else
    env \
      SPRING_PROFILES_ACTIVE=monolith \
      LUMALIFE_IDENTITY_REMOTE_ENABLED=false \
      LUMALIFE_IDENTITY_BACKFILL_COMPLETED=false \
      LUMALIFE_MERCHANT_REMOTE_ENABLED=false \
      LUMALIFE_MERCHANT_BACKFILL_COMPLETED=false \
      LUMALIFE_ORDER_REMOTE_ENABLED=false \
      LUMALIFE_ORDER_BACKFILL_COMPLETED=false \
      LUMALIFE_ASSISTANT_REMOTE_ENABLED=false \
      LUMALIFE_ASSISTANT_BACKFILL_COMPLETED=false \
      LUMALIFE_COMPATIBILITY_STORE_ENABLED=true \
      LUMALIFE_PERSISTENCE=mysql \
      docker compose "${STACK_COMPOSE_ARGS[@]}" up --detach --wait "${BACKEND_SERVICE}"
  fi
}

resource_sample() {
  local resource_csv="$1" backend_id="$2" timestamp stats_line cpu memory
  timestamp="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  stats_line="$(docker stats --no-stream --format '{{.CPUPerc}}|{{.MemUsage}}' "${backend_id}" 2>/dev/null || true)"
  if [[ -n "${stats_line}" ]]; then
    cpu="${stats_line%%|*}"
    memory="${stats_line#*|}"
  else
    cpu="N/A"
    memory="N/A"
  fi
  printf '%s,%s,%s\n' "${timestamp}" "${cpu}" "${memory}" >> "${resource_csv}"
}

summarize_resources() {
  local resource_csv="$1"
  local cpu_avg cpu_max memory_avg memory_max
  cpu_avg="$(awk -F, 'NR > 1 && $2 != "N/A" {gsub(/%/,"",$2); sum += $2; n++} END {if (n) printf "%.2f",sum/n; else print "N/A"}' "${resource_csv}")"
  cpu_max="$(awk -F, 'NR > 1 && $2 != "N/A" {gsub(/%/,"",$2); if ($2 > max) max=$2} END {if (NR > 1) printf "%.2f",max; else print "N/A"}' "${resource_csv}")"
  memory_avg="$(awk -F, 'NR > 1 && $3 != "N/A" {value=$3; sub(/ \/.*/,"",value); if (value ~ /GiB/) {gsub(/GiB/,"",value); value*=1024} else {gsub(/MiB/,"",value)} sum+=value; n++} END {if (n) printf "%.2f",sum/n; else print "N/A"}' "${resource_csv}")"
  memory_max="$(awk -F, 'NR > 1 && $3 != "N/A" {value=$3; sub(/ \/.*/,"",value); if (value ~ /GiB/) {gsub(/GiB/,"",value); value*=1024} else {gsub(/MiB/,"",value)} if (value > max) max=value} END {if (NR > 1) printf "%.2f",max; else print "N/A"}' "${resource_csv}")"
  printf '%s,%s,%s,%s' "${cpu_avg}" "${cpu_max}" "${memory_avg}" "${memory_max}"
}

run_api() {
  local mode="$1" api_name="$2" endpoint="$3"
  local stem="${mode}-${api_name}"
  local result_json="${OUTPUT_DIR}/${stem}.json"
  local result_csv="${OUTPUT_DIR}/${stem}.csv"
  local resource_csv="${OUTPUT_DIR}/${stem}-resources.csv"
  local backend_id load_pid load_exit resource_summary

  backend_id="$(docker compose "${STACK_COMPOSE_ARGS[@]}" ps -q "${BACKEND_SERVICE}")"
  printf 'timestamp,cpu_percent,memory_usage\n' > "${resource_csv}"
  (
    PERF_BASE_URL="${BASE_URL}" \
      PERF_ENDPOINT="${endpoint}" \
      PERF_LABEL="${stem}" \
      PERF_REPEATS="${REPEATS}" \
      PERF_REQUESTS="${REQUESTS}" \
      PERF_CONCURRENCY="${CONCURRENCY}" \
      PERF_WARMUP_REQUESTS="${WARMUP_REQUESTS}" \
      PERF_TIMEOUT_MS="${TIMEOUT_MS}" \
      PERF_MAX_ERROR_RATE=0 \
      PERF_OUTPUT="${result_json}" \
      PERF_CSV="${result_csv}" \
      node "${LOAD_TEST}"
  ) > "${OUTPUT_DIR}/${stem}.log" 2>&1 &
  load_pid=$!
  while kill -0 "${load_pid}" >/dev/null 2>&1; do
    resource_sample "${resource_csv}" "${backend_id}"
    sleep 1
  done
  if wait "${load_pid}"; then
    load_exit=0
  else
    load_exit=$?
    failed_runs=$((failed_runs + 1))
  fi
  resource_sample "${resource_csv}" "${backend_id}"
  resource_summary="$(summarize_resources "${resource_csv}")"

  if [[ -f "${result_json}" ]]; then
    jq -r --arg mode "${mode}" --arg api "${api_name}" \
      --arg cpu_avg "${resource_summary%%,*}" \
      --arg cpu_max "$(printf '%s' "${resource_summary}" | cut -d, -f2)" \
      --arg memory_avg "$(printf '%s' "${resource_summary}" | cut -d, -f3)" \
      --arg memory_max "$(printf '%s' "${resource_summary}" | cut -d, -f4)" \
      --arg json "${result_json}" --arg csv "${result_csv}" --arg resources "${resource_csv}" \
      '. as $root | [$mode,$api,$root.summary.requests,$root.summary.successful,$root.summary.failed,$root.summary.errorRate,$root.summary.throughputRps,$root.summary.averageMs,$root.summary.p95Ms,$cpu_avg,$cpu_max,$memory_avg,$memory_max,$json,$csv,$resources] | @csv' \
      "${result_json}" >> "${OUTPUT_DIR}/comparison-summary.csv"
  else
    printf '%s,%s,,,,,,,,%s,%s,%s,%s,%s,%s,%s\n' "${mode}" "${api_name}" \
      "$(printf '%s' "${resource_summary}" | cut -d, -f1)" \
      "$(printf '%s' "${resource_summary}" | cut -d, -f2)" \
      "$(printf '%s' "${resource_summary}" | cut -d, -f3)" \
      "$(printf '%s' "${resource_summary}" | cut -d, -f4)" "${result_json}" "${result_csv}" "${resource_csv}" \
      >> "${OUTPUT_DIR}/comparison-summary.csv"
  fi
  printf 'performance mode=%s api=%s exit=%s resources=%s\n' "${mode}" "${api_name}" "${load_exit}" "${resource_csv}"
}

restore_remote() {
  compose_backend microservices >/dev/null 2>&1 || true
}
trap restore_remote EXIT

for mode in microservices monolith; do
  compose_backend "${mode}"
  run_api "${mode}" merchant-search '/api/v1/merchants?keyword=%E5%92%96%E5%95%A1'
  run_api "${mode}" categories '/api/v1/categories'
  run_api "${mode}" merchant-detail '/api/v1/merchants/1'
done

cat > "${OUTPUT_DIR}/README.md" <<EOF
# Nightly performance comparison

- Same host and Compose data volume: ${COMPOSE_PROJECT}
- Same workload program: ${LOAD_TEST}
- Modes: microservices (prod,remote) and monolith (explicit monolith compatibility profile)
- APIs: merchant search, categories, merchant detail
- Repeats per API/mode: ${REPEATS}; requests per repeat: ${REQUESTS}; concurrency: ${CONCURRENCY}
- CPU/memory: backend Docker stats samples in each *-resources.csv
- Summary: comparison-summary.csv
- Failed load invocations: ${failed_runs}

The numbers are raw measurements from this run. They are not a claim of
production capacity and should not be compared with a different machine,
data volume, endpoint set, or workload configuration.
EOF

if ((failed_runs > 0)); then
  echo "Performance comparison completed with ${failed_runs} failed load invocation(s)." >&2
  exit 1
fi
echo "Performance comparison written to ${OUTPUT_DIR}"
