#!/usr/bin/env bash
set -Eeuo pipefail

# This experiment is intentionally small and observable. It exercises the
# merchant-service HPA through the in-cluster service endpoint and keeps the
# raw load log plus every kubectl observation. Missing metrics-server data is
# recorded as N/A and makes the experiment incomplete; it is never replaced by
# a guessed CPU value.
readonly NAMESPACE="${NAMESPACE:-lumalife}"
readonly TARGET_DEPLOYMENT="${TARGET_DEPLOYMENT:-merchant-service}"
readonly HPA_NAME="${HPA_NAME:-merchant-service}"
readonly LOAD_IMAGE="${LOAD_IMAGE:-curlimages/curl:8.12.1}"
readonly LOAD_SECONDS="${LOAD_SECONDS:-120}"
readonly COOLDOWN_SECONDS="${COOLDOWN_SECONDS:-150}"
readonly LOAD_CONCURRENCY="${LOAD_CONCURRENCY:-20}"
readonly SAMPLE_SECONDS="${SAMPLE_SECONDS:-10}"
readonly OUTPUT="${OUTPUT:-04_tests/cloud-native/hpa-observation.csv}"
readonly LOAD_NAME="hpa-load-${RANDOM}"
readonly LOAD_LOG="${OUTPUT%.csv}-load.log"
readonly TOP_LOG="${OUTPUT%.csv}-top.log"
readonly SUMMARY="${OUTPUT%.csv}-summary.md"

mkdir -p "$(dirname "${OUTPUT}")"
: > "${LOAD_LOG}"
: > "${TOP_LOG}"
printf 'phase,timestamp,pods,ready_pods,hpa_current,hpa_desired,current_cpu_percent,cpu_target,cpu_usage,memory_usage,cumulative_requests,cumulative_errors,throughput_rps,avg_latency_ms,p95_latency_ms\n' > "${OUTPUT}"

record() {
  local phase="$1"
  local timestamp pods ready current desired current_cpu target cpu_usage memory_usage
  local cumulative_requests cumulative_errors previous_requests
  local throughput avg_latency p95_latency

  timestamp="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  pods="$(kubectl -n "${NAMESPACE}" get deployment "${TARGET_DEPLOYMENT}" -o jsonpath='{.status.replicas}' 2>/dev/null || true)"
  ready="$(kubectl -n "${NAMESPACE}" get deployment "${TARGET_DEPLOYMENT}" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || true)"
  current="$(kubectl -n "${NAMESPACE}" get hpa "${HPA_NAME}" -o jsonpath='{.status.currentReplicas}' 2>/dev/null || true)"
  desired="$(kubectl -n "${NAMESPACE}" get hpa "${HPA_NAME}" -o jsonpath='{.status.desiredReplicas}' 2>/dev/null || true)"
  current_cpu="$(kubectl -n "${NAMESPACE}" get hpa "${HPA_NAME}" -o jsonpath='{.status.currentMetrics[0].resource.current.averageUtilization}' 2>/dev/null || true)"
  target="$(kubectl -n "${NAMESPACE}" get hpa "${HPA_NAME}" -o jsonpath='{.spec.metrics[0].resource.target.averageUtilization}' 2>/dev/null || true)"

  cpu_usage="N/A"
  memory_usage="N/A"
  if top_output="$(kubectl -n "${NAMESPACE}" top pod -l "app=${TARGET_DEPLOYMENT}" --no-headers 2>/dev/null)"; then
    printf '%s %s\n' "${timestamp}" "${top_output}" >> "${TOP_LOG}"
    cpu_usage="$(printf '%s\n' "${top_output}" | awk '{sum += $2} END {if (NR) print sum; else print "N/A"}')"
    memory_usage="$(printf '%s\n' "${top_output}" | awk '{sum += $3} END {if (NR) print sum; else print "N/A"}')"
  else
    printf '%s metrics-unavailable kubectl-top\n' "${timestamp}" >> "${TOP_LOG}"
  fi

  cumulative_requests="$(awk 'NF >= 3 {n++} END {print n + 0}' "${LOAD_LOG}")"
  cumulative_errors="$(awk 'NF >= 3 && $2 != 200 {n++} END {print n + 0}' "${LOAD_LOG}")"
  previous_requests="${last_requests:-0}"
  throughput="$(awk -v now="${cumulative_requests}" -v old="${previous_requests}" -v seconds="${SAMPLE_SECONDS}" 'BEGIN {printf "%.2f", (now-old)/seconds}')"
  avg_latency="$(awk '{sum += $3; n++} END {if (n) printf "%.2f", sum/n; else print "N/A"}' "${LOAD_LOG}")"
  p95_latency="$(awk '{print $3}' "${LOAD_LOG}" | sort -n | awk 'NF {a[NR]=$1} END {if (!NR) print "N/A"; else {i=int((NR*95+99)/100); print a[i]}}')"
  printf '%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n' \
    "${phase}" "${timestamp}" "${pods:-N/A}" "${ready:-N/A}" "${current:-N/A}" "${desired:-N/A}" \
    "${current_cpu:-N/A}" "${target:-N/A}" "${cpu_usage}" "${memory_usage}" \
    "${cumulative_requests}" "${cumulative_errors}" "${throughput}" "${avg_latency}" "${p95_latency}" | tee -a "${OUTPUT}"
  last_requests="${cumulative_requests}"
}

cleanup() {
  kubectl -n "${NAMESPACE}" delete pod "${LOAD_NAME}" --ignore-not-found --wait=false >/dev/null 2>&1 || true
}
trap cleanup EXIT

kubectl -n "${NAMESPACE}" get deployment "${TARGET_DEPLOYMENT}" >/dev/null
kubectl -n "${NAMESPACE}" get hpa "${HPA_NAME}" >/dev/null
kubectl -n "${NAMESPACE}" get hpa "${HPA_NAME}" -o yaml > "${OUTPUT%.csv}-hpa.yaml"
kubectl -n "${NAMESPACE}" get deployment "${TARGET_DEPLOYMENT}" -o yaml > "${OUTPUT%.csv}-deployment.yaml"
record baseline

load_command="
  end=\$((\$(date +%s) + ${LOAD_SECONDS}));
  while [ \$(date +%s) -lt \$end ]; do
    result=\$(curl --silent --show-error --max-time 5 -H 'X-Internal-Service-Token: compose-internal-token' -o /dev/null -w '%{http_code} %{time_total}' 'http://${TARGET_DEPLOYMENT}:8082/internal/v1/merchants?keyword=%E5%92%96%E5%95%A1' 2>/dev/null || printf '000 5');
    code=\${result%% *}; seconds=\${result##* }; millis=\$(awk -v value=\"\${seconds}\" 'BEGIN {printf \"%.0f\", value*1000}');
    printf '%s %s %s\\n' \$(date +%s%3N) \${code} \${millis};
  done
"
kubectl -n "${NAMESPACE}" run "${LOAD_NAME}" \
  --image="${LOAD_IMAGE}" \
  --restart=Never \
  --command -- sh -ec "for worker in \$(seq 1 ${LOAD_CONCURRENCY}); do ( ${load_command} ) & done; wait" \
  > "${OUTPUT%.csv}-load-create.log" 2>&1 &
load_create_pid=$!

for _ in $(seq 1 30); do
  phase="$(kubectl -n "${NAMESPACE}" get pod "${LOAD_NAME}" -o jsonpath='{.status.phase}' 2>/dev/null || true)"
  if [[ "${phase}" == "Running" || "${phase}" == "Failed" || "${phase}" == "Succeeded" ]]; then break; fi
  sleep 1
done
kubectl -n "${NAMESPACE}" logs -f "${LOAD_NAME}" > "${LOAD_LOG}" 2>/dev/null &
logs_pid=$!

for _ in $(seq 1 $((LOAD_SECONDS / SAMPLE_SECONDS))); do
  sleep "${SAMPLE_SECONDS}"
  record load
done

kubectl -n "${NAMESPACE}" delete pod "${LOAD_NAME}" --ignore-not-found --wait=true >/dev/null
kill "${logs_pid}" >/dev/null 2>&1 || true
wait "${logs_pid}" 2>/dev/null || true
wait "${load_create_pid}" 2>/dev/null || true
record load-complete

for _ in $(seq 1 $((COOLDOWN_SECONDS / SAMPLE_SECONDS))); do
  sleep "${SAMPLE_SECONDS}"
  record cooldown
done

metrics_condition="$(kubectl -n "${NAMESPACE}" get hpa "${HPA_NAME}" -o jsonpath='{.status.conditions[?(@.type=="ScalingActive")].status}' 2>/dev/null || true)"
able_to_scale="$(kubectl -n "${NAMESPACE}" get hpa "${HPA_NAME}" -o jsonpath='{.status.conditions[?(@.type=="AbleToScale")].status}' 2>/dev/null || true)"
metrics_message="$(kubectl -n "${NAMESPACE}" get hpa "${HPA_NAME}" -o jsonpath='{.status.conditions[?(@.type=="ScalingActive")].message}' 2>/dev/null || true)"
cat > "${SUMMARY}" <<EOF
# HPA experiment summary

- Target: ${TARGET_DEPLOYMENT}
- Namespace: ${NAMESPACE}
- Load: ${LOAD_CONCURRENCY} workers for ${LOAD_SECONDS} seconds
- HPA scaling active condition: ${metrics_condition:-N/A}
- HPA able-to-scale condition: ${able_to_scale:-N/A}
- Scaling message: ${metrics_message:-N/A}
- Raw observations: ${OUTPUT}
- Raw request log: ${LOAD_LOG}
- Raw kubectl top log: ${TOP_LOG}

The experiment is considered complete only when Kubernetes resource metrics are
available and the observed replica transition is supported by the raw CSV.
Missing metrics are retained as N/A and are a blocked cloud-native experiment,
not a successful scale result.
EOF

echo "HPA experiment observations written to ${OUTPUT}"
