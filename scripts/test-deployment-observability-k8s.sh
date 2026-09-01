#!/usr/bin/env bash
set -Eeuo pipefail

readonly NAMESPACE="${NAMESPACE:-lumalife}"
readonly SERVICE="${SERVICE:-identity-service}"
readonly CONTAINER="${CONTAINER:-identity-service}"
readonly FAILURE_TIMEOUT="${FAILURE_TIMEOUT:-45s}"
readonly RECOVERY_TIMEOUT="${RECOVERY_TIMEOUT:-180s}"
readonly EVIDENCE_DIR="${1:-artifacts/d07-deployment-failure}"
readonly CONTEXT="$(kubectl config current-context)"

if [[ ! "${CONTEXT}" =~ ^kind- ]]; then
  echo "Refusing failure injection outside a disposable Kind cluster: ${CONTEXT}" >&2
  exit 2
fi

mkdir -p "${EVIDENCE_DIR}"
good_image="$(kubectl -n "${NAMESPACE}" get deployment "${SERVICE}" -o jsonpath="{.spec.template.spec.containers[?(@.name=='${CONTAINER}')].image}")"
test -n "${good_image}"
bad_image="${good_image%:*}:d07-missing-${GITHUB_RUN_ID:-local}"
injected=0

restore_good_image() {
  kubectl -n "${NAMESPACE}" set image "deployment/${SERVICE}" "${CONTAINER}=${good_image}"
  kubectl -n "${NAMESPACE}" rollout status "deployment/${SERVICE}" --timeout="${RECOVERY_TIMEOUT}"
}

restore() {
  if [[ "${injected}" == 1 ]]; then
    restore_good_image >/dev/null 2>&1 || true
  fi
}
trap restore EXIT

printf 'context=%s\nservice=%s\ngood_image=%s\nbad_image=%s\n' \
  "${CONTEXT}" "${SERVICE}" "${good_image}" "${bad_image}" | tee "${EVIDENCE_DIR}/00-scenario.txt"

kubectl -n "${NAMESPACE}" set image "deployment/${SERVICE}" "${CONTAINER}=${bad_image}"
injected=1
set +e
kubectl -n "${NAMESPACE}" rollout status "deployment/${SERVICE}" --timeout="${FAILURE_TIMEOUT}" \
  >"${EVIDENCE_DIR}/01-rollout-failure.txt" 2>&1
rollout_code=$?
set -e
if [[ "${rollout_code}" == 0 ]]; then
  echo "The deliberately missing image unexpectedly rolled out successfully." >&2
  exit 1
fi

kubectl -n "${NAMESPACE}" get deployments,replicasets,pods -o wide | tee "${EVIDENCE_DIR}/02-workloads.txt"
kubectl -n "${NAMESPACE}" describe deployment "${SERVICE}" | tee "${EVIDENCE_DIR}/03-deployment-describe.txt"
kubectl -n "${NAMESPACE}" describe pods -l "app.kubernetes.io/name=${SERVICE}" | tee "${EVIDENCE_DIR}/04-pod-describe.txt"
kubectl -n "${NAMESPACE}" get events --sort-by=.lastTimestamp | tee "${EVIDENCE_DIR}/05-events.txt"
kubectl -n "${NAMESPACE}" rollout history "deployment/${SERVICE}" | tee "${EVIDENCE_DIR}/06-rollout-history.txt"
kubectl -n "${NAMESPACE}" logs --all-containers --prefix --tail=200 -l "app.kubernetes.io/name=${SERVICE}" \
  >"${EVIDENCE_DIR}/07-current-logs.txt" 2>&1 || true
kubectl -n "${NAMESPACE}" logs --all-containers --prefix --tail=200 --previous -l "app.kubernetes.io/name=${SERVICE}" \
  >"${EVIDENCE_DIR}/08-previous-logs.txt" 2>&1 || true

if ! grep -Eqi 'ErrImagePull|ImagePullBackOff|Failed to pull image|pull access denied|not found' \
    "${EVIDENCE_DIR}/04-pod-describe.txt" "${EVIDENCE_DIR}/05-events.txt"; then
  echo "The rollout failed, but image-pull evidence was not found." >&2
  exit 1
fi

restore_good_image | tee "${EVIDENCE_DIR}/09-recovery-rollout.txt"
recovered_image="$(kubectl -n "${NAMESPACE}" get deployment "${SERVICE}" -o jsonpath="{.spec.template.spec.containers[?(@.name=='${CONTAINER}')].image}")"
test "${recovered_image}" = "${good_image}"
kubectl -n "${NAMESPACE}" get deployment,pods -l "app.kubernetes.io/name=${SERVICE}" -o wide \
  | tee "${EVIDENCE_DIR}/10-recovered-workloads.txt"
injected=0
trap - EXIT

echo "Expected image-pull failure was diagnosed and ${SERVICE} recovered to ${good_image}."
