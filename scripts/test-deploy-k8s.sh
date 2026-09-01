#!/usr/bin/env bash
set -Eeuo pipefail

readonly SCRIPT_PATH="${1:-scripts/deploy-k8s.sh}"

bash -n "${SCRIPT_PATH}"
test -f scripts/lib/legacy-migrations.sh
test -f database/backfill-services.sql

grep -q 'readonly IMAGE_PULL_TIMEOUT="${IMAGE_PULL_TIMEOUT:-1800s}"' "${SCRIPT_PATH}"
grep -q 'readonly ROLLOUT_TIMEOUT="${ROLLOUT_TIMEOUT:-900s}"' "${SCRIPT_PATH}"
grep -q '^prefetch_images() {' "${SCRIPT_PATH}"
grep -q '^apply_versioned_manifests() {' "${SCRIPT_PATH}"
grep -Fq 's|value: main|value: ${IMAGE_TAG}|g' "${SCRIPT_PATH}"
test "$(grep -c 'name: SERVICE_VERSION' k8s/services.yaml)" -eq 3
grep -q '^source scripts/lib/legacy-migrations.sh$' "${SCRIPT_PATH}"
grep -q '^readonly BACKFILL_PATH="database/backfill-services.sql"$' "${SCRIPT_PATH}"
grep -q '^if \[\[ ! -f "\${BACKFILL_PATH}" \]\]; then$' "${SCRIPT_PATH}"
grep -q '< "\${BACKFILL_PATH}"$' "${SCRIPT_PATH}"
grep -q '^adopt_legacy_migrations database/migrations$' "${SCRIPT_PATH}"
grep -q -- '--from-file=10-bootstrap.sh=database/init/10-bootstrap.sh' "${SCRIPT_PATH}"
grep -q '^kubectl -n "\${NAMESPACE}" create configmap lumalife-mysql-migrations' "${SCRIPT_PATH}"
grep -q -- '--from-file=V008__service_order_lines.sql=database/migrations/V008__service_order_lines.sql' "${SCRIPT_PATH}"
grep -q -- '--from-file=V009__microservice_durability_fixes.sql=database/migrations/V009__microservice_durability_fixes.sql' "${SCRIPT_PATH}"
grep -q -- '--from-file=V010__order_main_payment_projection.sql=database/migrations/V010__order_main_payment_projection.sql' "${SCRIPT_PATH}"
grep -q 'version IN (.*V010' .github/workflows/ci.yml
grep -q '^            database/init$' .github/workflows/ci.yml
grep -q '^            scripts/lib/legacy-migrations.sh$' .github/workflows/ci.yml
grep -q '^            database/backfill-services.sql$' .github/workflows/ci.yml
grep -q 'database/init/10-bootstrap.sh' .github/workflows/deploy-ecs-k3s.yml
grep -q 'scripts/lib/legacy-migrations.sh' .github/workflows/deploy-ecs-k3s.yml
grep -q 'database/backfill-services.sql' .github/workflows/deploy-ecs-k3s.yml
grep -q 'api/v1/auth/login' "${SCRIPT_PATH}"

grep -q 'mountPath: /database/migrations' k8s/mysql.yaml
grep -q 'name: lumalife-mysql-migrations' k8s/mysql.yaml

prefetch_call_line="$(grep -n '^prefetch_images$' "${SCRIPT_PATH}" | cut -d: -f1)"
apply_call_line="$(grep -n '^apply_versioned_manifests$' "${SCRIPT_PATH}" | cut -d: -f1)"
mysql_apply_line="$(grep -n '^kubectl apply -f k8s/mysql.yaml$' "${SCRIPT_PATH}" | cut -d: -f1)"
adopt_call_line="$(grep -n '^adopt_legacy_migrations database/migrations$' "${SCRIPT_PATH}" | cut -d: -f1)"

test -n "${prefetch_call_line}"
test -n "${apply_call_line}"
test -n "${mysql_apply_line}"
test -n "${adopt_call_line}"
test "${prefetch_call_line}" -lt "${mysql_apply_line}"
test "${mysql_apply_line}" -lt "${adopt_call_line}"
test "${adopt_call_line}" -lt "${apply_call_line}"

if grep -q 'kubectl apply -k k8s' "${SCRIPT_PATH}"; then
  echo "The base manifests must not trigger a transient :main rollout." >&2
  exit 1
fi

echo "Deployment script prefetch and timeout checks passed."
