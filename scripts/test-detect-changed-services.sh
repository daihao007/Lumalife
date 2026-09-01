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
assert_selection '["identity-service","merchant-service","order-service"]' $'services/pom.xml\n'
assert_selection '["identity-service","merchant-service","order-service"]' $'k8s/healthcheck/Dockerfile\n'
assert_selection '["identity-service","merchant-service","order-service"]' $'k8s/services/kustomization.yaml\n'
assert_selection '["identity-service","merchant-service","order-service"]' $'scripts/smoke-services-k8s.sh\n'
assert_selection '["order-service"]' $'k8s/services/order-service.yaml\n'
assert_selection '[]' $'k8s/services.yaml\n'
assert_selection '[]' $'docs/README.md\nfrontend/src/App.tsx\n'

grep -Fq 'SERVICE_VERSION=${{ steps.version.outputs.version }}' .github/workflows/services-cd.yml
grep -Fq 'GIT_COMMIT=${{ github.sha }}' .github/workflows/services-cd.yml
grep -Fq -- '--build-arg "SERVICE_VERSION=${image_tag}"' .github/workflows/services-cd.yml
grep -Fq -- '--build-arg "GIT_COMMIT=${GITHUB_SHA}"' .github/workflows/services-cd.yml
grep -Fq 'EXPECTED_VERSION=${IMAGE_TAG}' scripts/smoke-services-k8s.sh
grep -Fq 'EXPECTED_SHA=${SOURCE_SHA}' scripts/smoke-services-k8s.sh
grep -Fq '/actuator/info' scripts/smoke-services-k8s.sh

echo "Changed-service detection tests passed."
