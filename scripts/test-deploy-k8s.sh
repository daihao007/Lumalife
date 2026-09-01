#!/usr/bin/env bash
set -Eeuo pipefail

readonly SCRIPT_PATH="${1:-scripts/deploy-k8s.sh}"

bash -n "${SCRIPT_PATH}"
bash -n scripts/test-deployment-observability-k8s.sh

grep -q 'readonly IMAGE_PULL_TIMEOUT="${IMAGE_PULL_TIMEOUT:-1800s}"' "${SCRIPT_PATH}"
grep -q 'readonly ROLLOUT_TIMEOUT="${ROLLOUT_TIMEOUT:-900s}"' "${SCRIPT_PATH}"
grep -q '^prefetch_images() {' "${SCRIPT_PATH}"
grep -q '^apply_versioned_manifests() {' "${SCRIPT_PATH}"
grep -q 'app.kubernetes.io/version: main' k8s/services.yaml
grep -q 'app.kubernetes.io/version: \${IMAGE_TAG}' "${SCRIPT_PATH}"
grep -q 'EXPECTED_VERSION=\${IMAGE_TAG}' "${SCRIPT_PATH}"
grep -q 'EXPECTED_SHA=\${SOURCE_SHA}' "${SCRIPT_PATH}"
grep -q 'check_java_service identity-service 8081' "${SCRIPT_PATH}"
grep -q 'check_java_service merchant-service 8082' "${SCRIPT_PATH}"
grep -q 'check_java_service order-service 8083' "${SCRIPT_PATH}"
grep -q 'frontend/version.json' "${SCRIPT_PATH}"
grep -q 'Refusing failure injection outside a disposable Kind cluster' scripts/test-deployment-observability-k8s.sh

test "$(grep -c 'path: /actuator/health/liveness' k8s/services.yaml)" -eq 6
test "$(grep -c 'path: /actuator/health/readiness' k8s/services.yaml)" -eq 3
test "$(grep -c 'app.kubernetes.io/version: main' k8s/services.yaml)" -eq 9

rendered="$(kubectl kustomize k8s)"
test "$(grep -c 'kind: Deployment' <<<"${rendered}")" -eq 5
test "$(grep -c 'app.kubernetes.io/version: main' <<<"${rendered}")" -eq 15
grep -q -- '--from-file=10-bootstrap.sh=database/init/10-bootstrap.sh' "${SCRIPT_PATH}"
grep -q '^kubectl -n "\${NAMESPACE}" create configmap lumalife-mysql-migrations' "${SCRIPT_PATH}"
grep -q -- '--from-file=V008__service_order_lines.sql=database/migrations/V008__service_order_lines.sql' "${SCRIPT_PATH}"
grep -q 'version IN (.*V008' .github/workflows/ci.yml
grep -q '^            database/init$' .github/workflows/ci.yml
grep -q 'database/init/10-bootstrap.sh' .github/workflows/deploy-ecs-k3s.yml

grep -q 'mountPath: /database/migrations' k8s/mysql.yaml
grep -q 'name: lumalife-mysql-migrations' k8s/mysql.yaml

prefetch_call_line="$(grep -n '^prefetch_images$' "${SCRIPT_PATH}" | cut -d: -f1)"
apply_call_line="$(grep -n '^apply_versioned_manifests$' "${SCRIPT_PATH}" | cut -d: -f1)"

test -n "${prefetch_call_line}"
test -n "${apply_call_line}"
test "${prefetch_call_line}" -lt "${apply_call_line}"

if grep -q 'kubectl apply -k k8s' "${SCRIPT_PATH}"; then
  echo "The base manifests must not trigger a transient :main rollout." >&2
  exit 1
fi

echo "Deployment script prefetch and timeout checks passed."
