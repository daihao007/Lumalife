#!/usr/bin/env bash
set -Eeuo pipefail

readonly NAMESPACE="${NAMESPACE:-lumalife}"
readonly TARGET_SERVICE="${TARGET_SERVICE:-backend}"
readonly LOAD_IMAGE="${LOAD_IMAGE:-curlimages/curl:8.12.1}"
readonly LOAD_SECONDS="${LOAD_SECONDS:-180}"
readonly COOLDOWN_SECONDS="${COOLDOWN_SECONDS:-180}"
readonly LOAD_CONCURRENCY="${LOAD_CONCURRENCY:-20}"
readonly OUTPUT="${OUTPUT:-04_tests/cloud-native/hpa-observation.csv}"
readonly LOAD_NAME="hpa-load-${RANDOM}"

mkdir -p "$(dirname "${OUTPUT}")"
printf 'phase,timestamp,pods,ready_pods,hpa_current,hpa_desired,cpu_target\n' > "${OUTPUT}"

record() {
  local phase="$1"
  local timestamp pods ready current desired target
  timestamp="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  pods="$(kubectl -n "${NAMESPACE}" get deployment "${TARGET_SERVICE}" -o jsonpath='{.status.replicas}')"
  ready="$(kubectl -n "${NAMESPACE}" get deployment "${TARGET_SERVICE}" -o jsonpath='{.status.readyReplicas}')"
  current="$(kubectl -n "${NAMESPACE}" get hpa "${TARGET_SERVICE}" -o jsonpath='{.status.currentReplicas}')"
  desired="$(kubectl -n "${NAMESPACE}" get hpa "${TARGET_SERVICE}" -o jsonpath='{.status.desiredReplicas}')"
  target="$(kubectl -n "${NAMESPACE}" get hpa "${TARGET_SERVICE}" -o jsonpath='{.status.currentMetrics[0].resource.current.averageUtilization}')"
  printf '%s,%s,%s,%s,%s,%s,%s\n' "${phase}" "${timestamp}" "${pods:-0}" "${ready:-0}" "${current:-0}" "${desired:-0}" "${target:-0}" | tee -a "${OUTPUT}"
}

cleanup() {
  kubectl -n "${NAMESPACE}" delete pod "${LOAD_NAME}" --ignore-not-found --wait=false >/dev/null 2>&1 || true
}
trap cleanup EXIT

kubectl -n "${NAMESPACE}" get hpa "${TARGET_SERVICE}"
record baseline

kubectl -n "${NAMESPACE}" run "${LOAD_NAME}" \
  --image="${LOAD_IMAGE}" \
  --restart=Never \
  --command -- sh -ec \
  "for worker in \$(seq 1 ${LOAD_CONCURRENCY}); do (while :; do curl --fail --silent --max-time 2 'http://${TARGET_SERVICE}:8080/api/v1/merchants?keyword=%E5%92%96%E5%95%A1' >/dev/null || true; done) & done; wait"

for _ in $(seq 1 $((LOAD_SECONDS / 10))); do
  sleep 10
  record load
done

kubectl -n "${NAMESPACE}" delete pod "${LOAD_NAME}" --ignore-not-found --wait=true
for _ in $(seq 1 $((COOLDOWN_SECONDS / 10))); do
  sleep 10
  record cooldown
done

echo "HPA experiment observations written to ${OUTPUT}"
