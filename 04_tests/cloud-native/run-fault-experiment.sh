#!/usr/bin/env bash
set -Eeuo pipefail

# Injects one controlled merchant-service outage through Kubernetes and checks
# the public backend contract before, during, and after the outage. The HPA is
# temporarily removed so that it cannot immediately recreate the failed target;
# its exact manifest is restored by the exit trap.
readonly NAMESPACE="${NAMESPACE:-lumalife}"
readonly BACKEND_SERVICE="${BACKEND_SERVICE:-backend}"
readonly MERCHANT_DEPLOYMENT="${MERCHANT_DEPLOYMENT:-merchant-service}"
readonly LOCAL_PORT="${LOCAL_PORT:-18090}"
readonly OUTPUT_DIR="${OUTPUT_DIR:-04_tests/cloud-native/fault}"
readonly HPA_NAME="${HPA_NAME:-merchant-service}"

mkdir -p "${OUTPUT_DIR}"
readonly HPA_SNAPSHOT="${OUTPUT_DIR}/hpa-before.yaml"
readonly PORT_FORWARD_LOG="${OUTPUT_DIR}/port-forward.log"
readonly PORT_FORWARD_PID_FILE="${OUTPUT_DIR}/port-forward.pid"
readonly MERCHANT_REPLICAS_BEFORE="$(kubectl -n "${NAMESPACE}" get deployment "${MERCHANT_DEPLOYMENT}" -o jsonpath='{.spec.replicas}')"
readonly HEALTH_URL="http://127.0.0.1:${LOCAL_PORT}/actuator/health/readiness"
readonly MERCHANT_URL="http://127.0.0.1:${LOCAL_PORT}/api/v1/merchants?keyword=%E5%92%96%E5%95%A1"

hpa_removed=0
restored=0

capture_request() {
  local name="$1" url="$2"
  local headers_file="${OUTPUT_DIR}/${name}-headers.txt"
  local body_file="${OUTPUT_DIR}/${name}-body.txt"
  local status_code
  status_code="$(curl --silent --show-error --max-time 15 -D "${headers_file}" -o "${body_file}" -w '%{http_code}' "${url}" 2>"${OUTPUT_DIR}/${name}-curl.log" || true)"
  printf '%s\n' "${status_code}" > "${OUTPUT_DIR}/${name}-status.txt"
  printf 'request=%s status=%s\n' "${name}" "${status_code}" | tee -a "${OUTPUT_DIR}/requests.txt"
  return 0
}

wait_for_backend() {
  for _ in $(seq 1 60); do
    if [[ "$(curl --silent --max-time 2 -o /dev/null -w '%{http_code}' "${HEALTH_URL}" 2>/dev/null || true)" == "200" ]]; then
      return 0
    fi
    sleep 1
  done
  return 1
}

restore_cluster() {
  if ((restored)); then return 0; fi
  trap - EXIT
  kubectl -n "${NAMESPACE}" scale deployment "${MERCHANT_DEPLOYMENT}" --replicas="${MERCHANT_REPLICAS_BEFORE}" >/dev/null 2>&1 || true
  kubectl -n "${NAMESPACE}" wait --for=condition=Available "deployment/${MERCHANT_DEPLOYMENT}" --timeout=120s >/dev/null 2>&1 || true
  if ((hpa_removed)); then
    kubectl -n "${NAMESPACE}" apply -f "${HPA_SNAPSHOT}" >/dev/null 2>&1 || true
  fi
  if [[ -f "${PORT_FORWARD_PID_FILE}" ]]; then
    kill "$(<"${PORT_FORWARD_PID_FILE}")" >/dev/null 2>&1 || true
  fi
  restored=1
}
trap restore_cluster EXIT

kubectl -n "${NAMESPACE}" get deployment "${MERCHANT_DEPLOYMENT}" -o yaml > "${OUTPUT_DIR}/deployment-before.yaml"
kubectl -n "${NAMESPACE}" get pods -o wide > "${OUTPUT_DIR}/pods-before.txt"
kubectl -n "${NAMESPACE}" get endpoints "${MERCHANT_DEPLOYMENT}" -o yaml > "${OUTPUT_DIR}/endpoints-before.yaml" 2>/dev/null || true
if kubectl -n "${NAMESPACE}" get hpa "${HPA_NAME}" >/dev/null 2>&1; then
  kubectl -n "${NAMESPACE}" get hpa "${HPA_NAME}" -o yaml > "${HPA_SNAPSHOT}"
  kubectl -n "${NAMESPACE}" delete hpa "${HPA_NAME}" --wait=true >/dev/null
  hpa_removed=1
fi

kubectl -n "${NAMESPACE}" port-forward "service/${BACKEND_SERVICE}" "${LOCAL_PORT}:8080" > "${PORT_FORWARD_LOG}" 2>&1 &
port_forward_pid=$!
printf '%s\n' "${port_forward_pid}" > "${PORT_FORWARD_PID_FILE}"
wait_for_backend

kubectl -n "${NAMESPACE}" get deployments,pods,endpoints -o wide > "${OUTPUT_DIR}/before.txt"
capture_request before-health "${HEALTH_URL}"
capture_request before-merchants "${MERCHANT_URL}"

kubectl -n "${NAMESPACE}" scale deployment "${MERCHANT_DEPLOYMENT}" --replicas=0 >/dev/null
for _ in $(seq 1 90); do
  scaled_replicas="$(kubectl -n "${NAMESPACE}" get deployment "${MERCHANT_DEPLOYMENT}" -o jsonpath='{.status.replicas}' 2>/dev/null || true)"
  scaled_ready="$(kubectl -n "${NAMESPACE}" get deployment "${MERCHANT_DEPLOYMENT}" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || true)"
  if [[ -z "${scaled_replicas}" || "${scaled_replicas}" == "0" ]] && [[ -z "${scaled_ready}" || "${scaled_ready}" == "0" ]]; then
    break
  fi
  sleep 1
done
kubectl -n "${NAMESPACE}" get deployments,pods,endpoints -o wide > "${OUTPUT_DIR}/during.txt"
capture_request during-health "${HEALTH_URL}"
capture_request during-merchants "${MERCHANT_URL}"

kubectl -n "${NAMESPACE}" scale deployment "${MERCHANT_DEPLOYMENT}" --replicas="${MERCHANT_REPLICAS_BEFORE}" >/dev/null
kubectl -n "${NAMESPACE}" wait --for=condition=Available "deployment/${MERCHANT_DEPLOYMENT}" --timeout=120s >/dev/null
kubectl -n "${NAMESPACE}" get deployments,pods,endpoints -o wide > "${OUTPUT_DIR}/after.txt"
capture_request after-health "${HEALTH_URL}"
capture_request after-merchants "${MERCHANT_URL}"

kubectl -n "${NAMESPACE}" logs deployment/backend --tail=300 > "${OUTPUT_DIR}/backend.log" 2>&1 || true
kubectl -n "${NAMESPACE}" logs deployment/merchant-service --tail=300 > "${OUTPUT_DIR}/merchant.log" 2>&1 || true
kubectl -n "${NAMESPACE}" get events --sort-by=.lastTimestamp > "${OUTPUT_DIR}/events.txt" || true

before_status="$(<"${OUTPUT_DIR}/before-merchants-status.txt")"
during_status="$(<"${OUTPUT_DIR}/during-merchants-status.txt")"
after_status="$(<"${OUTPUT_DIR}/after-merchants-status.txt")"
before_health="$(<"${OUTPUT_DIR}/before-health-status.txt")"
during_health="$(<"${OUTPUT_DIR}/during-health-status.txt")"
after_health="$(<"${OUTPUT_DIR}/after-health-status.txt")"
if [[ "${before_status}" == "200" && "${during_health}" == "200" && "${after_status}" == "200" && "${before_health}" == "200" && "${after_health}" == "200" && "${during_status}" != "200" ]]; then
  experiment_result="PASS"
else
  experiment_result="BLOCKED"
fi

cat > "${OUTPUT_DIR}/summary.md" <<EOF
# Merchant-service fault experiment

- Result: ${experiment_result}
- Injection: ${MERCHANT_DEPLOYMENT} scaled from ${MERCHANT_REPLICAS_BEFORE} to 0, then restored
- Public merchant request: before=${before_status}, during=${during_status}, after=${after_status}
- Backend readiness: before=${before_health}, during=${during_health}, after=${after_health}
- HPA: temporarily removed during injection and restored from hpa-before.yaml
- Raw evidence: ${OUTPUT_DIR}

The expected course-project behavior is a non-200, explicit service-boundary
failure during the outage while backend readiness remains healthy and the
request recovers after the merchant deployment is restored. No data volume or
application source was deleted.
EOF

restore_cluster
echo "Fault experiment result: ${experiment_result}"
