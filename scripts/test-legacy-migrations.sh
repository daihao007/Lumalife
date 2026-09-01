#!/usr/bin/env bash
set -Eeuo pipefail

source scripts/lib/legacy-migrations.sh

mock_migration_count=0
mock_baseline_table_count=17
mock_payment_index_columns="user_id,client_request_id"
mock_payment_processing_check_count=1
mock_business_state_count=1
mock_service_catalog_count=2
mock_service_domain_count=4
mock_order_state_column_count=6
mock_order_request_index_columns="user_id,client_request_id"
mock_order_status_index_columns="merchant_id,status,id"
mock_service_order_event_count=1
mock_insert_sql=""

mysql_exec_remote() {
  local argument query=""
  for argument in "$@"; do
    case "${argument}" in
      --execute=*) query="${argument#--execute=}" ;;
    esac
  done

  case "${query}" in
    'SELECT COUNT(*) FROM schema_migration;') printf '%s\n' "${mock_migration_count}" ;;
    *"table_name IN ('category','merchant'"*) printf '%s\n' "${mock_baseline_table_count}" ;;
    *"table_name='payment_record'"*"index_name='uk_payment_request'"*) printf '%s\n' "${mock_payment_index_columns}" ;;
    *"constraint_name='ck_payment_status'"*) printf '%s\n' "${mock_payment_processing_check_count}" ;;
    *"table_name='business_state'"*) printf '%s\n' "${mock_business_state_count}" ;;
    *"table_name IN ('merchant_catalog','order_record')"*) printf '%s\n' "${mock_service_catalog_count}" ;;
    *"table_name IN ('service_cart_item','service_payment','service_coupon','service_review')"*) printf '%s\n' "${mock_service_domain_count}" ;;
    *"column_name IN ('order_type','client_request_id','coupon_code','address_id','reviewed','version')"*) printf '%s\n' "${mock_order_state_column_count}" ;;
    *"index_name='uq_order_client_request'"*) printf '%s\n' "${mock_order_request_index_columns}" ;;
    *"index_name='idx_order_merchant_status'"*) printf '%s\n' "${mock_order_status_index_columns}" ;;
    *"table_name='service_order_event'"*) printf '%s\n' "${mock_service_order_event_count}" ;;
    'INSERT INTO schema_migration'* ) mock_insert_sql="${query}" ;;
    *) echo "Unexpected mock query: ${query}" >&2; return 1 ;;
  esac
}

adopt_legacy_migrations database/migrations
for version in V001 V002 V003 V004 V005 V006; do
  [[ "${mock_insert_sql}" == *"('${version}'"* ]]
done

mock_insert_sql=""
mock_migration_count=6
adopt_legacy_migrations database/migrations
test -z "${mock_insert_sql}"

mock_migration_count=0
mock_baseline_table_count=0
adopt_legacy_migrations database/migrations
test -z "${mock_insert_sql}"

mock_baseline_table_count=16
if adopt_legacy_migrations database/migrations; then
  echo "Partial V001 schema should not be adopted." >&2
  exit 1
fi
test -z "${mock_insert_sql}"

mock_baseline_table_count=17
mock_order_state_column_count=5
if adopt_legacy_migrations database/migrations; then
  echo "Partial V006 schema should not be adopted." >&2
  exit 1
fi
test -z "${mock_insert_sql}"

echo "Legacy migration adoption checks passed."
