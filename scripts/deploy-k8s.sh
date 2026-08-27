#!/usr/bin/env bash
set -Eeuo pipefail

readonly NAMESPACE="lumalife"
readonly IMAGE_TAG="${1:?usage: deploy-k8s.sh <image-tag>}"
readonly BACKEND_IMAGE="${BACKEND_IMAGE:-ghcr.io/daihao007/lumalife-backend}"
readonly FRONTEND_IMAGE="${FRONTEND_IMAGE:-ghcr.io/daihao007/lumalife-frontend}"
readonly ROLLOUT_TIMEOUT="${ROLLOUT_TIMEOUT:-300s}"
readonly HEALTHCHECK_IMAGE="${HEALTHCHECK_IMAGE:-curlimages/curl:8.12.1}"

diagnostics() {
  echo "::group::Kubernetes deployment diagnostics"
  kubectl -n "${NAMESPACE}" get deployments,pods,services -o wide || true
  kubectl -n "${NAMESPACE}" describe deployment backend frontend || true
  kubectl -n "${NAMESPACE}" describe pods || true
  echo "::endgroup::"
}
trap diagnostics ERR

kubectl apply -k k8s
kubectl -n "${NAMESPACE}" set image deployment/backend \
  "backend=${BACKEND_IMAGE}:${IMAGE_TAG}"
kubectl -n "${NAMESPACE}" set image deployment/frontend \
  "frontend=${FRONTEND_IMAGE}:${IMAGE_TAG}"

kubectl -n "${NAMESPACE}" rollout status deployment/backend --timeout="${ROLLOUT_TIMEOUT}"
kubectl -n "${NAMESPACE}" rollout status deployment/frontend --timeout="${ROLLOUT_TIMEOUT}"

healthcheck_name="deployment-health-${GITHUB_RUN_ID:-local}-${GITHUB_RUN_ATTEMPT:-1}"
healthcheck_name="${healthcheck_name:0:63}"
kubectl -n "${NAMESPACE}" delete pod "${healthcheck_name}" --ignore-not-found
kubectl -n "${NAMESPACE}" run "${healthcheck_name}" \
  --rm --attach --restart=Never \
  --image="${HEALTHCHECK_IMAGE}" \
  --command -- sh -ec '
    curl --fail --silent --show-error --retry 12 --retry-delay 2 http://backend:8080/actuator/health/readiness | grep -q "\"status\":\"UP\""
    curl --fail --silent --show-error --retry 12 --retry-delay 2 http://frontend/healthz | grep -q "^ok$"
    curl --fail --silent --show-error --retry 12 --retry-delay 2 http://frontend/actuator/health/readiness | grep -q "\"status\":\"UP\""
  '

kubectl -n "${NAMESPACE}" get deployments,pods,services -o wide
echo "Kubernetes rollout and in-cluster health checks passed for image tag ${IMAGE_TAG}."
