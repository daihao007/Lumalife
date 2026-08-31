#!/usr/bin/env bash
set -Eeuo pipefail

readonly SCRIPT_PATH="${1:-scripts/deploy-k8s.sh}"

bash -n "${SCRIPT_PATH}"

grep -q 'readonly IMAGE_PULL_TIMEOUT="${IMAGE_PULL_TIMEOUT:-1800s}"' "${SCRIPT_PATH}"
grep -q 'readonly ROLLOUT_TIMEOUT="${ROLLOUT_TIMEOUT:-900s}"' "${SCRIPT_PATH}"
grep -q '^prefetch_images() {' "${SCRIPT_PATH}"
grep -q '^apply_versioned_manifests() {' "${SCRIPT_PATH}"

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
