#!/usr/bin/env bash
set -Eeuo pipefail

readonly NAMESPACE="lumalife"
readonly IMAGE_TAG="${1:?usage: deploy-k8s.sh <image-tag>}"
readonly BACKEND_IMAGE="${BACKEND_IMAGE:-ghcr.io/daihao007/lumalife-backend}"
readonly FRONTEND_IMAGE="${FRONTEND_IMAGE:-ghcr.io/daihao007/lumalife-frontend}"
readonly IDENTITY_IMAGE="${IDENTITY_IMAGE:-ghcr.io/daihao007/lumalife-identity-service}"
readonly MERCHANT_IMAGE="${MERCHANT_IMAGE:-ghcr.io/daihao007/lumalife-merchant-service}"
readonly ORDER_IMAGE="${ORDER_IMAGE:-ghcr.io/daihao007/lumalife-order-service}"
readonly ROLLOUT_TIMEOUT="${ROLLOUT_TIMEOUT:-300s}"
readonly HEALTHCHECK_IMAGE="${HEALTHCHECK_IMAGE:-curlimages/curl:8.12.1}"
readonly MYSQL_DATABASE="${MYSQL_DATABASE:-life_assistant}"
readonly MYSQL_USER="${MYSQL_USER:-lifeassist}"
readonly MYSQL_PASSWORD="${MYSQL_PASSWORD:-lumalife-ci-password}"
readonly MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-lumalife-ci-root-password}"

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
trap diagnostics ERR

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
  --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -k k8s
kubectl -n "${NAMESPACE}" set image deployment/backend \
  "backend=${BACKEND_IMAGE}:${IMAGE_TAG}"
kubectl -n "${NAMESPACE}" set image deployment/frontend \
  "frontend=${FRONTEND_IMAGE}:${IMAGE_TAG}"
kubectl -n "${NAMESPACE}" set image deployment/identity-service "identity-service=${IDENTITY_IMAGE}:${IMAGE_TAG}"
kubectl -n "${NAMESPACE}" set image deployment/merchant-service "merchant-service=${MERCHANT_IMAGE}:${IMAGE_TAG}"
kubectl -n "${NAMESPACE}" set image deployment/order-service "order-service=${ORDER_IMAGE}:${IMAGE_TAG}"

kubectl -n "${NAMESPACE}" rollout status statefulset/mysql --timeout="${ROLLOUT_TIMEOUT}"

# Existing MySQL PVCs do not rerun /docker-entrypoint-initdb.d. Apply the
# versioned migrations and idempotent service backfill on every deployment so
# a code rollout cannot leave the service-owned tables behind the images.
mysql_exec_remote() {
  kubectl -n "${NAMESPACE}" exec statefulset/mysql -- sh -ec \
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP --host=127.0.0.1 --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --default-character-set=utf8mb4 "$@"' \
    sh "$@"
}
mysql_exec_remote --execute='CREATE TABLE IF NOT EXISTS schema_migration (version VARCHAR(64) NOT NULL, description VARCHAR(255) NOT NULL, checksum CHAR(64) NOT NULL, installed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (version)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;'
for migration in database/migrations/V[0-9][0-9][0-9]__*.sql; do
  [ -f "${migration}" ] || continue
  filename=$(basename "${migration}")
  version=${filename%%__*}
  description=${filename#*__}
  description=${description%.sql}
  checksum=$(sha256sum "${migration}" | awk '{print $1}')
  applied_checksum=$(mysql_exec_remote --batch --skip-column-names --execute="SELECT checksum FROM schema_migration WHERE version='${version}'")
  if [ -n "${applied_checksum}" ]; then
    test "${applied_checksum}" = "${checksum}"
  else
    kubectl -n "${NAMESPACE}" exec -i statefulset/mysql -- sh -ec \
      'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP --host=127.0.0.1 --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --default-character-set=utf8mb4' < "${migration}"
    mysql_exec_remote --execute="INSERT INTO schema_migration(version,description,checksum) VALUES ('${version}','${description}','${checksum}')"
  fi
done
kubectl -n "${NAMESPACE}" exec -i statefulset/mysql -- sh -ec \
  'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP --host=127.0.0.1 --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --default-character-set=utf8mb4' < database/backfill-services.sql

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
