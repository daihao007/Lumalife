#!/usr/bin/env bash
set -Eeuo pipefail

readonly OUTPUT_DIR="${1:?usage: validate-results.sh <output-dir> [requests-per-repeat] [concurrency] [repeats]}"
readonly EXPECTED_REQUESTS="${2:-90}"
readonly EXPECTED_CONCURRENCY="${3:-12}"
readonly EXPECTED_REPEATS="${4:-3}"
readonly SUMMARY="${OUTPUT_DIR}/comparison-summary.csv"
readonly METADATA="${OUTPUT_DIR}/run-metadata.txt"

test -s "${SUMMARY}"
test -s "${METADATA}"

awk -F, '
  NR == 1 {
    expected = "mode,api,requests,successful,failed,error_rate,throughput_rps,average_ms,p95_ms,cpu_avg_percent,cpu_max_percent,memory_avg_mib,memory_max_mib,result_json,result_csv,resources_csv"
    if ($0 != expected) exit 1
    next
  }
  NF == 16 && $10 != "" && $10 != "N/A" && $11 != "" && $11 != "N/A" && $12 != "" && $12 != "N/A" && $13 != "" && $13 != "N/A" { rows++ }
  END { exit !(rows == 6) }
' "${SUMMARY}"

grep -Fxq "repeats=${EXPECTED_REPEATS}" "${METADATA}"
grep -Fxq "apis=merchant-search,categories,merchant-detail" "${METADATA}"
grep -Fxq "modes=microservices,monolith" "${METADATA}"

for mode in microservices monolith; do
  for api in merchant-search categories merchant-detail; do
    stem="${mode}-${api}"
    json="${OUTPUT_DIR}/${stem}.json"
    csv="${OUTPUT_DIR}/${stem}.csv"
    resources="${OUTPUT_DIR}/${stem}-resources.csv"
    log="${OUTPUT_DIR}/${stem}.log"

    test -s "${json}"
    test -s "${csv}"
    test -s "${resources}"
    test -f "${log}"

    jq -e \
      --argjson expectedRequests "${EXPECTED_REQUESTS}" \
      --argjson expectedConcurrency "${EXPECTED_CONCURRENCY}" \
      --argjson expectedRepeats "${EXPECTED_REPEATS}" \
      '(
        .workload.repeats == $expectedRepeats and
        .workload.requestsPerRepeat == $expectedRequests and
        .workload.concurrency == $expectedConcurrency and
        ([.measurements[] | select(.phase | startswith("repeat-"))] | length == $expectedRepeats) and
        .summary.requests == ($expectedRequests * $expectedRepeats) and
        .summary.failed == 0 and
        .summary.errorRate == 0
      )' "${json}" >/dev/null

    awk -F, 'NR == 1 && $0 == "timestamp,cpu_percent,memory_usage" { header=1; next }
      NR > 1 && $2 != "N/A" && $3 != "N/A" { samples++ }
      END { exit !(header && samples > 0) }' "${resources}"

    grep -Fq "\"${mode}\",\"${api}\"," "${SUMMARY}"
  done
done

echo "Performance result matrix is complete: 2 modes x 3 APIs x ${EXPECTED_REPEATS} repeats with CPU/memory samples."
