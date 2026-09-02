#!/usr/bin/env bash
set -Eeuo pipefail

repo_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
project_name=${MS_E2E_PROJECT:-lumalife-microservice-e2e}
run_id=${MS_E2E_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)-$$}
report_dir=${MS_E2E_REPORT_DIR:-"$repo_dir/04_tests/e2e/microservices/latest"}
compose=(docker compose -p "$project_name" -f "$repo_dir/docker-compose.yml" -f "$repo_dir/docker-compose.e2e.yml")
cleanup=${MS_E2E_KEEP_ENV:-0}
runner_rc=1

# These values are deliberately scoped to this shell and this Compose project.
# They must never replace a developer's ignored .env or its normal volumes.
export MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD:-microservice-e2e-root-password}
export MYSQL_DATABASE=${MYSQL_DATABASE:-life_assistant}
export MYSQL_IDENTITY_DATABASE=${MYSQL_IDENTITY_DATABASE:-life_assistant_identity}
export MYSQL_MERCHANT_DATABASE=${MYSQL_MERCHANT_DATABASE:-life_assistant_merchant}
export MYSQL_ORDER_DATABASE=${MYSQL_ORDER_DATABASE:-life_assistant_order}
export MYSQL_USER=${MYSQL_USER:-lifeassist_e2e}
export MYSQL_PASSWORD=${MYSQL_PASSWORD:-microservice-e2e-password}
export RABBITMQ_USER=${RABBITMQ_USER:-lumalife_e2e}
export RABBITMQ_PASSWORD=${RABBITMQ_PASSWORD:-microservice-e2e-rabbit-password}
export LUMALIFE_INTERNAL_SERVICE_TOKEN=${LUMALIFE_INTERNAL_SERVICE_TOKEN:-microservice-e2e-token}
export SPRING_PROFILES_ACTIVE=prod,remote
export LUMALIFE_IDENTITY_REMOTE_ENABLED=true
export LUMALIFE_IDENTITY_BACKFILL_COMPLETED=true
export LUMALIFE_MERCHANT_REMOTE_ENABLED=true
export LUMALIFE_MERCHANT_BACKFILL_COMPLETED=true
export LUMALIFE_ORDER_REMOTE_ENABLED=true
export LUMALIFE_ORDER_BACKFILL_COMPLETED=true
export LUMALIFE_ASSISTANT_REMOTE_ENABLED=true
export LUMALIFE_ASSISTANT_BACKFILL_COMPLETED=true
export LUMALIFE_COMPATIBILITY_STORE_ENABLED=false
export LUMALIFE_EVENTS_BROKER_ENABLED=true
export LUMALIFE_PERSISTENCE=mysql
export MYSQL_PORT=${MYSQL_PORT:-13306}
export BACKEND_PORT=${BACKEND_PORT:-18080}
export IDENTITY_SERVICE_PORT=${IDENTITY_SERVICE_PORT:-18081}
export MERCHANT_SERVICE_PORT=${MERCHANT_SERVICE_PORT:-18082}
export ORDER_SERVICE_PORT=${ORDER_SERVICE_PORT:-18083}
export ASSISTANT_SERVICE_PORT=${ASSISTANT_SERVICE_PORT:-18084}
export FRONTEND_PORT=${FRONTEND_PORT:-15173}

mkdir -p "$report_dir"
rm -f "$report_dir/environment-failure.txt"

collect_failure_evidence() {
  local rc=$?
  if [[ $rc -ne 0 ]]; then
    {
      echo "run_id=$run_id"
      echo "project=$project_name"
      echo "exit_code=$rc"
      echo "collected_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    } > "$report_dir/environment-failure.txt"
    "${compose[@]}" ps --all > "$report_dir/docker-compose-ps.txt" 2>&1 || true
    for service in backend identity-service merchant-service order-service assistant-service rabbitmq mysql; do
      "${compose[@]}" logs --no-color --timestamps "$service" > "$report_dir/${service}.log" 2>&1 || true
    done
    for endpoint in \
      "http://127.0.0.1:18080/actuator/health" \
      "http://127.0.0.1:18080/actuator/health/readiness" \
      "http://127.0.0.1:18081/actuator/health" \
      "http://127.0.0.1:18082/actuator/health" \
      "http://127.0.0.1:18083/actuator/health" \
      "http://127.0.0.1:18084/actuator/health"; do
      curl --silent --show-error --max-time 5 "$endpoint" >> "$report_dir/health-responses.txt" 2>&1 || true
      printf '\n' >> "$report_dir/health-responses.txt"
    done
  fi
  if [[ "$cleanup" != "1" ]]; then
    "${compose[@]} down --volumes --remove-orphans" >/dev/null 2>&1 || true
  fi
  return "$rc"
}
trap collect_failure_evidence EXIT

echo "Microservice E2E run_id=$run_id project=$project_name"
"${compose[@]} down --volumes --remove-orphans"
"${compose[@]}" up --detach --build --wait mysql rabbitmq
"${compose[@]}" --profile db-tools run --rm db-migrate
"${compose[@]}" --profile db-tools run --rm db-seed
"${compose[@]}" --profile db-tools run --rm db-backfill-services
"${compose[@]}" up --detach --build --wait identity-service merchant-service order-service assistant-service backend frontend

set +e
MS_E2E_BASE_URL=http://127.0.0.1:18080 \
MS_E2E_IDENTITY_URL=http://127.0.0.1:18081 \
MS_E2E_MERCHANT_URL=http://127.0.0.1:18082 \
MS_E2E_ORDER_URL=http://127.0.0.1:18083 \
MS_E2E_ASSISTANT_URL=http://127.0.0.1:18084 \
MS_E2E_COMPOSE_PROJECT="$project_name" \
MS_E2E_RUN_ID="$run_id" \
MS_E2E_REPORT_DIR="$report_dir" \
node "$repo_dir/e2e/microservice-runner.mjs"
runner_rc=$?
set -e

# A failed E2E run still retains its evidence until this script exits.
"${compose[@]}" ps --all > "$report_dir/docker-compose-ps.txt" 2>&1 || true
for service in backend identity-service merchant-service order-service assistant-service rabbitmq mysql; do
  "${compose[@]}" logs --no-color --timestamps "$service" > "$report_dir/${service}.log" 2>&1 || true
done

# Keep the successful path explicit as well as the EXIT trap.  This prevents
# a completed local/CI run from leaving its isolated containers and volume
# behind while MS_E2E_KEEP_ENV=1 remains available for failure debugging.
if [[ "$cleanup" != "1" ]]; then
  "${compose[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
fi
trap - EXIT

exit "$runner_rc"
