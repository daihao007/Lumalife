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
readonly KUBECTL_LOG="${OUTPUT%.csv}-kubectl.log"
readonly EVENTS_LOG="${OUTPUT%.csv}-events.log"
readonly SERVICE_LOG="${OUTPUT%.csv}-service.log"
readonly SUMMARY="${OUTPUT%.csv}-summary.md"

# CI invokes kubectl from PATH. A Windows checkout running the WSL bash
# wrapper can provide the Windows client explicitly without changing the
# experiment semantics.
if [[ -n "${KUBECTL_BIN:-}" ]]; then
  kubectl() {
    "${KUBECTL_BIN}" "$@"
  }
fi

mkdir -p "$(dirname "${OUTPUT}")"
: > "${LOAD_LOG}"
: > "${TOP_LOG}"
: > "${KUBECTL_LOG}"
: > "${EVENTS_LOG}"
: > "${SERVICE_LOG}"
printf 'phase,timestamp,pods,ready_pods,hpa_current,hpa_desired,current_cpu_percent,cpu_target,cpu_usage,memory_usage,cumulative_requests,cumulative_errors,error_rate_percent,throughput_rps,avg_latency_ms,p95_latency_ms\n' > "${OUTPUT}"

capture_to() {
  local output="$1"
  local label="$2"
  shift 2
  local result status

  if result="$(kubectl "$@" 2>&1)"; then
    status=0
  else
    status=$?
  fi
  {
    printf '===== %s | %s | exit=%s =====\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${label}" "${status}"
    printf '%s\n' "${result}"
  } >> "${output}"
  return "${status}"
}

capture_kubectl() {
  capture_to "${KUBECTL_LOG}" "$@"
}

capture_events() {
  capture_to "${EVENTS_LOG}" "$@"
}

capture_service_logs() {
  capture_to "${SERVICE_LOG}" "$@"
}

blocked_summary() {
  local reason="$1"
  local timestamp
  timestamp="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'preflight-blocked,%s,N/A,N/A,N/A,N/A,N/A,N/A,N/A,N/A,N/A,N/A,N/A,N/A,N/A,N/A\n' "${timestamp}" >> "${OUTPUT}"
  cat > "${SUMMARY}" <<EOF
# HPA experiment summary

- Status: **BLOCKED**
- Target: ${TARGET_DEPLOYMENT}
- Namespace: ${NAMESPACE}
- Blocked before load generation: ${reason}
- Raw CSV: ${OUTPUT}
- Raw request log: ${LOAD_LOG}
- Raw kubectl transcript: ${KUBECTL_LOG}
- Raw events transcript: ${EVENTS_LOG}
- Raw merchant-service log transcript: ${SERVICE_LOG}

No load was started and no replica transition was observed in this run. This
run must not be reported as an HPA PASS. A PASS requires metrics.k8s.io to be
available and the raw CSV to show HPA current/desired and merchant-service
Ready replicas moving from 1 to at least 2 under load and returning to 1 after
cooldown.
EOF
}

record() {
  local phase="$1"
  local timestamp pods ready current desired current_cpu target cpu_usage memory_usage
  local cumulative_requests cumulative_errors previous_requests
  local error_rate throughput avg_latency p95_latency

  timestamp="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  pods="$(kubectl --request-timeout=10s -n "${NAMESPACE}" get deployment "${TARGET_DEPLOYMENT}" -o jsonpath='{.status.replicas}' 2>/dev/null || true)"
  ready="$(kubectl --request-timeout=10s -n "${NAMESPACE}" get deployment "${TARGET_DEPLOYMENT}" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || true)"
  current="$(kubectl --request-timeout=10s -n "${NAMESPACE}" get hpa "${HPA_NAME}" -o jsonpath='{.status.currentReplicas}' 2>/dev/null || true)"
  desired="$(kubectl --request-timeout=10s -n "${NAMESPACE}" get hpa "${HPA_NAME}" -o jsonpath='{.status.desiredReplicas}' 2>/dev/null || true)"
  current_cpu="$(kubectl --request-timeout=10s -n "${NAMESPACE}" get hpa "${HPA_NAME}" -o jsonpath='{.status.currentMetrics[0].resource.current.averageUtilization}' 2>/dev/null || true)"
  target="$(kubectl --request-timeout=10s -n "${NAMESPACE}" get hpa "${HPA_NAME}" -o jsonpath='{.spec.metrics[0].resource.target.averageUtilization}' 2>/dev/null || true)"

  capture_kubectl "${phase} deployment" --request-timeout=10s -n "${NAMESPACE}" get deployment "${TARGET_DEPLOYMENT}" -o yaml || true
  capture_kubectl "${phase} hpa" --request-timeout=10s -n "${NAMESPACE}" get hpa "${HPA_NAME}" -o yaml || true
  capture_kubectl "${phase} pods" --request-timeout=10s -n "${NAMESPACE}" get pods -l "app=${TARGET_DEPLOYMENT}" -o wide || true
  capture_events "${phase} events" --request-timeout=10s -n "${NAMESPACE}" get events --sort-by=.lastTimestamp || true
  capture_service_logs "${phase} merchant-service logs" --request-timeout=10s -n "${NAMESPACE}" logs --all-containers --prefix --tail=200 -l "app=${TARGET_DEPLOYMENT}" || true

  cpu_usage="N/A"
  memory_usage="N/A"
  if top_output="$(kubectl --request-timeout=10s -n "${NAMESPACE}" top pod -l "app=${TARGET_DEPLOYMENT}" --no-headers 2>&1)"; then
    printf '%s %s\n' "${timestamp}" "${top_output}" >> "${TOP_LOG}"
    cpu_usage="$(printf '%s\n' "${top_output}" | awk '{sum += $2} END {if (NR) print sum; else print "N/A"}')"
    memory_usage="$(printf '%s\n' "${top_output}" | awk '{sum += $3} END {if (NR) print sum; else print "N/A"}')"
  else
    printf '%s %s\n' "${timestamp}" "${top_output}" >> "${TOP_LOG}"
  fi

  cumulative_requests="$(awk 'NF >= 3 {n++} END {print n + 0}' "${LOAD_LOG}")"
  cumulative_errors="$(awk 'NF >= 3 && $2 != 200 {n++} END {print n + 0}' "${LOAD_LOG}")"
  previous_requests="${last_requests:-0}"
  error_rate="$(awk -v errors="${cumulative_errors}" -v requests="${cumulative_requests}" 'BEGIN {if (requests == 0) print "N/A"; else printf "%.2f", (errors * 100) / requests}')"
  throughput="$(awk -v now="${cumulative_requests}" -v old="${previous_requests}" -v seconds="${SAMPLE_SECONDS}" 'BEGIN {printf "%.2f", (now-old)/seconds}')"
  avg_latency="$(awk '{sum += $3; n++} END {if (n) printf "%.2f", sum/n; else print "N/A"}' "${LOAD_LOG}")"
  p95_latency="$(awk '{print $3}' "${LOAD_LOG}" | sort -n | awk 'NF {a[NR]=$1} END {if (!NR) print "N/A"; else {i=int((NR*95+99)/100); print a[i]}}')"
  printf '%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n' \
    "${phase}" "${timestamp}" "${pods:-N/A}" "${ready:-N/A}" "${current:-N/A}" "${desired:-N/A}" \
    "${current_cpu:-N/A}" "${target:-N/A}" "${cpu_usage}" "${memory_usage}" \
    "${cumulative_requests}" "${cumulative_errors}" "${error_rate}" "${throughput}" "${avg_latency}" "${p95_latency}" | tee -a "${OUTPUT}"
  last_requests="${cumulative_requests}"
}

cleanup() {
  kubectl -n "${NAMESPACE}" delete pod "${LOAD_NAME}" --ignore-not-found --wait=false >/dev/null 2>&1 || true
}
trap cleanup EXIT

preflight_failures=0
capture_kubectl "current context" config current-context || preflight_failures=$((preflight_failures + 1))
capture_kubectl "metrics APIService" --request-timeout=10s get apiservice v1beta1.metrics.k8s.io -o yaml || preflight_failures=$((preflight_failures + 1))
capture_kubectl "metrics API" --request-timeout=10s get --raw /apis/metrics.k8s.io/v1beta1 || preflight_failures=$((preflight_failures + 1))
capture_kubectl "node metrics" --request-timeout=10s top nodes || preflight_failures=$((preflight_failures + 1))
capture_kubectl "target deployment preflight" --request-timeout=10s -n "${NAMESPACE}" get deployment "${TARGET_DEPLOYMENT}" || preflight_failures=$((preflight_failures + 1))
capture_kubectl "target hpa preflight" --request-timeout=10s -n "${NAMESPACE}" get hpa "${HPA_NAME}" || preflight_failures=$((preflight_failures + 1))
capture_events "preflight events" --request-timeout=10s -n "${NAMESPACE}" get events --sort-by=.lastTimestamp || true
capture_service_logs "preflight merchant-service logs" --request-timeout=10s -n "${NAMESPACE}" logs --all-containers --prefix --tail=200 -l "app=${TARGET_DEPLOYMENT}" || true

if ((preflight_failures > 0)); then
  blocked_summary "Kubernetes context, metrics.k8s.io, target Deployment, or target HPA preflight failed; see ${KUBECTL_LOG}."
  exit 2
fi

kubectl --request-timeout=10s -n "${NAMESPACE}" get hpa "${HPA_NAME}" -o yaml > "${OUTPUT%.csv}-hpa.yaml"
kubectl --request-timeout=10s -n "${NAMESPACE}" get deployment "${TARGET_DEPLOYMENT}" -o yaml > "${OUTPUT%.csv}-deployment.yaml"
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
capture_service_logs "load complete merchant-service logs" --request-timeout=10s -n "${NAMESPACE}" logs --all-containers --prefix --tail=200 -l "app=${TARGET_DEPLOYMENT}" || true
record load-complete

for _ in $(seq 1 $((COOLDOWN_SECONDS / SAMPLE_SECONDS))); do
  sleep "${SAMPLE_SECONDS}"
  record cooldown
done

metrics_condition="$(kubectl -n "${NAMESPACE}" get hpa "${HPA_NAME}" -o jsonpath='{.status.conditions[?(@.type=="ScalingActive")].status}' 2>/dev/null || true)"
able_to_scale="$(kubectl -n "${NAMESPACE}" get hpa "${HPA_NAME}" -o jsonpath='{.status.conditions[?(@.type=="AbleToScale")].status}' 2>/dev/null || true)"
metrics_message="$(kubectl -n "${NAMESPACE}" get hpa "${HPA_NAME}" -o jsonpath='{.status.conditions[?(@.type=="ScalingActive")].message}' 2>/dev/null || true)"
scale_up_observed="$(awk -F, 'NR > 1 && ($1 == "load" || $1 == "load-complete") && $3 ~ /^[0-9]+$/ && $3 >= 2 && $4 ~ /^[0-9]+$/ && $4 >= 2 && $5 ~ /^[0-9]+$/ && $5 >= 2 && $6 ~ /^[0-9]+$/ && $6 >= 2 {found=1} END {print found ? "true" : "false"}' "${OUTPUT}")"
scale_down_observed="$(awk -F, 'NR > 1 && $1 == "cooldown" && $3 == 1 && $4 == 1 && $5 == 1 && $6 == 1 {found=1} END {print found ? "true" : "false"}' "${OUTPUT}")"
experiment_status="BLOCKED"
if [[ "${metrics_condition}" == "True" && "${scale_up_observed}" == "true" && "${scale_down_observed}" == "true" ]]; then
  experiment_status="PASS"
fi
cat > "${SUMMARY}" <<EOF
# HPA experiment summary

- Status: **${experiment_status}**
- Target: ${TARGET_DEPLOYMENT}
- Namespace: ${NAMESPACE}
- Load: ${LOAD_CONCURRENCY} workers for ${LOAD_SECONDS} seconds
- HPA scaling active condition: ${metrics_condition:-N/A}
- HPA able-to-scale condition: ${able_to_scale:-N/A}
- Scaling message: ${metrics_message:-N/A}
- Scale-up observed in raw CSV: ${scale_up_observed}
- Scale-down observed in raw CSV: ${scale_down_observed}
- Raw observations: ${OUTPUT}
- Raw request log: ${LOAD_LOG}
- Raw kubectl top log: ${TOP_LOG}
- Raw kubectl transcript: ${KUBECTL_LOG}
- Raw events transcript: ${EVENTS_LOG}
- Raw merchant-service log transcript: ${SERVICE_LOG}

The experiment is considered complete only when Kubernetes resource metrics are
available and the observed replica transition is supported by the raw CSV.
The script emits PASS only when metrics.k8s.io is active and both HPA and ready
replica scale-up and scale-down transitions are observed; otherwise it emits
BLOCKED.
EOF

echo "HPA experiment observations written to ${OUTPUT}"
