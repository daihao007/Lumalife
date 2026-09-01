#!/usr/bin/env bash
set -Eeuo pipefail

readonly NAMESPACE="lumalife"
readonly IMAGE_TAG="${1:?usage: deploy-k8s.sh <image-tag>}"
readonly BACKEND_IMAGE="${BACKEND_IMAGE:-ghcr.io/daihao007/lumalife-backend}"
readonly FRONTEND_IMAGE="${FRONTEND_IMAGE:-ghcr.io/daihao007/lumalife-frontend}"
readonly IDENTITY_IMAGE="${IDENTITY_IMAGE:-ghcr.io/daihao007/lumalife-identity-service}"
readonly MERCHANT_IMAGE="${MERCHANT_IMAGE:-ghcr.io/daihao007/lumalife-merchant-service}"
readonly ORDER_IMAGE="${ORDER_IMAGE:-ghcr.io/daihao007/lumalife-order-service}"
readonly IMAGE_PULL_TIMEOUT="${IMAGE_PULL_TIMEOUT:-1800s}"
readonly ROLLOUT_TIMEOUT="${ROLLOUT_TIMEOUT:-900s}"
readonly HEALTHCHECK_IMAGE="${HEALTHCHECK_IMAGE:-curlimages/curl:8.12.1}"
readonly MYSQL_DATABASE="${MYSQL_DATABASE:-life_assistant}"
readonly MYSQL_USER="${MYSQL_USER:-lifeassist}"
readonly MYSQL_PASSWORD="${MYSQL_PASSWORD:-lumalife-ci-password}"
readonly MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-lumalife-ci-root-password}"

prefetch_pods=()

cleanup_prefetch() {
  if ((${#prefetch_pods[@]} > 0)); then
    kubectl -n "${NAMESPACE}" delete pod "${prefetch_pods[@]}" \
      --ignore-not-found --wait=false >/dev/null 2>&1 || true
  fi
}

diagnostics() {
  echo "::group::Kubernetes deployment diagnostics"
  kubectl -n "${NAMESPACE}" get deployments,statefulsets,pods,services,persistentvolumeclaims -o wide || true
  kubectl -n "${NAMESPACE}" describe deployment backend frontend || true
  kubectl -n "${NAMESPACE}" describe pods || true
  echo "Recent container logs (including the previous crashed instance):"
  kubectl -n "${NAMESPACE}" logs --all-containers --prefix --tail=200 \
    -l 'app.kubernetes.io/part-of=lumalife' || true
  for app in identity-service merchant-service order-service; do
    kubectl -n "${NAMESPACE}" logs --all-containers --prefix --tail=200 -l "app=${app}" || true
    kubectl -n "${NAMESPACE}" logs --all-containers --prefix --tail=200 --previous -l "app=${app}" || true
  done
  kubectl -n "${NAMESPACE}" get events --sort-by=.lastTimestamp || true
  echo "::endgroup::"
}

on_error() {
  local exit_code="$?"
  trap - ERR
  diagnostics
  cleanup_prefetch
  exit "${exit_code}"
}
trap on_error ERR

prefetch_images() {
  local -a images=(
    "${BACKEND_IMAGE}:${IMAGE_TAG}"
    "${FRONTEND_IMAGE}:${IMAGE_TAG}"
    "${IDENTITY_IMAGE}:${IMAGE_TAG}"
    "${MERCHANT_IMAGE}:${IMAGE_TAG}"
    "${ORDER_IMAGE}:${IMAGE_TAG}"
  )
  local -a pod_targets=()
  local safe_tag run_suffix pod_name image index

  safe_tag="$(printf '%s' "${IMAGE_TAG}" | tr '[:upper:]_.' '[:lower:]--' | tr -cd 'a-z0-9-')"
  safe_tag="${safe_tag:0:24}"
  run_suffix="${GITHUB_RUN_ID:-local}-${GITHUB_RUN_ATTEMPT:-1}"
  run_suffix="$(printf '%s' "${run_suffix}" | tr -cd '0-9a-zA-Z-' | tr '[:upper:]' '[:lower:]')"
  run_suffix="${run_suffix:0:16}"

  echo "Pre-pulling ${#images[@]} application images before changing deployments."
  for index in "${!images[@]}"; do
    image="${images[index]}"
    pod_name="image-prefetch-${safe_tag}-${index}-${run_suffix}"
    kubectl -n "${NAMESPACE}" delete pod "${pod_name}" --ignore-not-found --wait=false >/dev/null
    kubectl -n "${NAMESPACE}" run "${pod_name}" \
      --image="${image}" \
      --image-pull-policy=IfNotPresent \
      --restart=Never \
      --labels="app.kubernetes.io/name=lumalife-image-prefetch,app.kubernetes.io/part-of=lumalife" \
      --command -- /bin/sh -c 'exit 0'
    prefetch_pods+=("${pod_name}")
    pod_targets+=("pod/${pod_name}")
  done

  kubectl -n "${NAMESPACE}" wait \
    --for=jsonpath='{.status.phase}'=Succeeded \
    --timeout="${IMAGE_PULL_TIMEOUT}" \
    "${pod_targets[@]}"
  kubectl -n "${NAMESPACE}" get pods "${prefetch_pods[@]}" -o wide
  cleanup_prefetch
  prefetch_pods=()
}

apply_versioned_manifests() {
  local manifests image
  local -a expected_images=(
    "${BACKEND_IMAGE}:${IMAGE_TAG}"
    "${FRONTEND_IMAGE}:${IMAGE_TAG}"
    "${IDENTITY_IMAGE}:${IMAGE_TAG}"
    "${MERCHANT_IMAGE}:${IMAGE_TAG}"
    "${ORDER_IMAGE}:${IMAGE_TAG}"
  )

  manifests="$(kubectl kustomize k8s | sed \
    -e "s|image: ghcr.io/daihao007/lumalife-backend:main|image: ${BACKEND_IMAGE}:${IMAGE_TAG}|" \
    -e "s|image: ghcr.io/daihao007/lumalife-frontend:main|image: ${FRONTEND_IMAGE}:${IMAGE_TAG}|" \
    -e "s|image: ghcr.io/daihao007/lumalife-identity-service:main|image: ${IDENTITY_IMAGE}:${IMAGE_TAG}|" \
    -e "s|image: ghcr.io/daihao007/lumalife-merchant-service:main|image: ${MERCHANT_IMAGE}:${IMAGE_TAG}|" \
    -e "s|image: ghcr.io/daihao007/lumalife-order-service:main|image: ${ORDER_IMAGE}:${IMAGE_TAG}|")"

  for image in "${expected_images[@]}"; do
    grep -Fq "image: ${image}" <<<"${manifests}" || {
      echo "Rendered manifest is missing expected image ${image}." >&2
      return 1
    }
  done
  if grep -Eq 'image: ghcr\.io/daihao007/lumalife-[^:]+:main' <<<"${manifests}"; then
    echo "Rendered manifest still contains a mutable LumaLife :main image." >&2
    return 1
  fi

  printf '%s\n' "${manifests}" | kubectl apply -f -
}

kubectl apply -f k8s/namespace.yaml
kubectl -n "${NAMESPACE}" create secret generic lumalife-mysql \
  --from-literal=database="${MYSQL_DATABASE}" \
  --from-literal=username="${MYSQL_USER}" \
  --from-literal=password="${MYSQL_PASSWORD}" \
  --from-literal=root-password="${MYSQL_ROOT_PASSWORD}" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n "${NAMESPACE}" create configmap lumalife-mysql-init \
  --from-file=001-baseline.sql=database/migrations/V001__baseline_schema.sql \
  --from-file=002-payment-idempotency.sql=database/migrations/V002__payment_idempotency_scope.sql \
  --from-file=003-business-state.sql=database/migrations/V003__business_state_store.sql \
  --from-file=004-service-owned.sql=database/migrations/V004__service_owned_catalog_orders.sql \
  --from-file=005-order-domain-service-tables.sql=database/migrations/V005__order_domain_service_tables.sql \
  --from-file=006-order-domain-state.sql=database/migrations/V006__order_domain_state_and_indexes.sql \
  --from-file=007-microservice-durability.sql=database/migrations/V007__microservice_durability_fixes.sql \
  --dry-run=client -o yaml | kubectl apply -f -

prefetch_images
apply_versioned_manifests

kubectl -n "${NAMESPACE}" rollout status statefulset/mysql --timeout="${ROLLOUT_TIMEOUT}"
kubectl -n "${NAMESPACE}" rollout status deployment/backend --timeout="${ROLLOUT_TIMEOUT}"
kubectl -n "${NAMESPACE}" rollout status deployment/frontend --timeout="${ROLLOUT_TIMEOUT}"
kubectl -n "${NAMESPACE}" rollout status deployment/identity-service --timeout="${ROLLOUT_TIMEOUT}"
kubectl -n "${NAMESPACE}" rollout status deployment/merchant-service --timeout="${ROLLOUT_TIMEOUT}"
kubectl -n "${NAMESPACE}" rollout status deployment/order-service --timeout="${ROLLOUT_TIMEOUT}"

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

kubectl -n "${NAMESPACE}" get deployments,statefulsets,pods,services,persistentvolumeclaims -o wide
echo "Kubernetes rollout and in-cluster health checks passed for image tag ${IMAGE_TAG}."
