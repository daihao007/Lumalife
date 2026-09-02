#!/usr/bin/env bash
set -Eeuo pipefail

readonly DETECTOR="$(dirname "$0")/detect-changed-services.sh"

assert_selection() {
  local expected="$1"
  local paths="$2"
  local actual
  actual="$(printf '%s' "${paths}" | bash "${DETECTOR}" --stdin)"
  if [[ "${actual}" != "${expected}" ]]; then
    printf 'expected %s, got %s for paths:\n%s\n' "${expected}" "${actual}" "${paths}" >&2
    return 1
  fi
}

assert_selection '["identity-service"]' $'services/identity-service/src/main/App.java\n'
assert_selection '["merchant-service","order-service"]' $'services/order-service/pom.xml\nservices/merchant-service/Dockerfile\n'
assert_selection '["identity-service","merchant-service","order-service","assistant-service"]' $'services/pom.xml\n'
assert_selection '["identity-service","merchant-service","order-service","assistant-service"]' $'k8s/healthcheck/Dockerfile\n'
assert_selection '["identity-service","merchant-service","order-service","assistant-service"]' $'k8s/services.yaml\n'
assert_selection '["identity-service","merchant-service","order-service","assistant-service"]' $'k8s/kustomization.yaml\n'
assert_selection '["identity-service","merchant-service","order-service","assistant-service"]' $'scripts/smoke-services-k8s.sh\n'
assert_selection '["assistant-service"]' $'services/assistant-service/src/main/java/com/lumalife/assistant/AssistantApi.java\n'
assert_selection '[]' $'docs/README.md\nfrontend/src/App.tsx\n'

echo "Changed-service detection tests passed."
