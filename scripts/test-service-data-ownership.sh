#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MANIFEST="${ROOT}/services/data-ownership.yml"

test -f "${MANIFEST}"
grep -q '^legacyDatabase: life_assistant$' "${MANIFEST}"

assert_manifest_tables() {
  local service="$1"
  shift
  local table
  for table in "$@"; do
    grep -q "^      - ${table}$" "${MANIFEST}"
  done
}

assert_no_cross_service_sql() {
  local service="$1"
  local forbidden_pattern="$2"
  local source_dir="${ROOT}/services/${service}/src/main"
  local matches
  local rg_pattern="(?:from|join|into|update|delete\\s+from|truncate\\s+table)\\s+(?:${forbidden_pattern})\\b"
  local grep_pattern="(from|join|into|update|delete[[:space:]]+from|truncate[[:space:]]+table)[[:space:]]+(${forbidden_pattern})([^[:alnum:]_]|$)"

  if command -v rg >/dev/null 2>&1; then
    matches="$(rg -n --pcre2 -i --glob '*.java' "${rg_pattern}" "${source_dir}" || true)"
  else
    matches="$(grep -RInE --include='*.java' -i "${grep_pattern}" "${source_dir}" || true)"
  fi
  if [[ -n "${matches}" ]]; then
    printf 'cross-service SQL access detected in %s:\n%s\n' "${service}" "${matches}" >&2
    return 1
  fi
}

assert_manifest_tables identity-service user_account user_address auth_session
assert_manifest_tables merchant-service category merchant merchant_catalog group_deal \
  merchant_favorite chat_message inventory_reservation inventory_reservation_item merchant_inbox_event merchant_outbox_event
assert_manifest_tables order-service order_record service_order_line service_order_event \
  service_cart_item service_payment service_coupon service_review service_outbox_event order_inbox_event order_inventory_saga

# A service may hold another service's identifier, but it must not query or
# mutate the other service's tables. Keep this gate close to the source so a
# new repository cannot silently cross the boundary.
assert_no_cross_service_sql identity-service \
  'merchant_catalog|merchant_favorite|group_deal|inventory_reservation(_item)?|order_record|order_main|order_item|payment_record|coupon|cart_item|product|merchant|category|business_state|service_[a-z_]+'
assert_no_cross_service_sql merchant-service \
  'user_account|user_address|auth_session|order_record|order_main|order_item|payment_record|coupon|cart_item|product|business_state|service_[a-z_]+'
assert_no_cross_service_sql order-service \
  'user_account|user_address|auth_session|merchant_catalog|merchant_favorite|group_deal|inventory_reservation(_item)?|merchant|category|product|chat_message|business_state'

# The deployment wiring must point each service at its own logical database.
grep -q 'key: identity-database' "${ROOT}/k8s/services.yaml"
grep -q 'key: merchant-database' "${ROOT}/k8s/services.yaml"
grep -q 'key: order-database' "${ROOT}/k8s/services.yaml"
grep -q 'MYSQL_DATABASE:.*MYSQL_IDENTITY_DATABASE' "${ROOT}/docker-compose.yml"
grep -q 'MYSQL_DATABASE:.*MYSQL_MERCHANT_DATABASE' "${ROOT}/docker-compose.yml"
grep -q 'MYSQL_DATABASE:.*MYSQL_ORDER_DATABASE' "${ROOT}/docker-compose.yml"

test -f "${ROOT}/k8s/service-databases.yaml"
grep -q 'name: mysql-identity' "${ROOT}/k8s/service-databases.yaml"
grep -q 'name: mysql-merchant' "${ROOT}/k8s/service-databases.yaml"
grep -q 'name: mysql-order' "${ROOT}/k8s/service-databases.yaml"
grep -q 'value: mysql-identity' "${ROOT}/k8s/services.yaml"
grep -q 'value: mysql-merchant' "${ROOT}/k8s/services.yaml"
grep -q 'value: mysql-order' "${ROOT}/k8s/services.yaml"
grep -q 'IDENTITY_MYSQL_HOST: mysql-identity' "${ROOT}/docker-compose.physical-db.yml"
grep -q 'MERCHANT_MYSQL_HOST: mysql-merchant' "${ROOT}/docker-compose.physical-db.yml"
grep -q 'ORDER_MYSQL_HOST: mysql-order' "${ROOT}/docker-compose.physical-db.yml"

echo "Service data ownership boundary checks passed."
