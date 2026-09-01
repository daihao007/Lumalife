#!/usr/bin/env bash

# Adopt the schema created by the pre-registry Kubernetes bootstrap. That
# bootstrap ran V001-V006 directly from /docker-entrypoint-initdb.d but did not
# record them in schema_migration. The caller must provide mysql_exec_remote.

legacy_migration_scalar() {
  mysql_exec_remote --batch --skip-column-names --execute="$1"
}

legacy_migration_integer() {
  local label="$1"
  local value="$2"

  if [[ ! "${value}" =~ ^[0-9]+$ ]]; then
    echo "Unable to inspect legacy schema (${label} returned '${value}')." >&2
    return 1
  fi
}

adopt_legacy_migrations() {
  local migration_directory="${1:?migration directory is required}"
  local migration_count baseline_table_count
  local payment_index_columns payment_processing_check_count
  local business_state_count service_catalog_count service_domain_count
  local order_state_column_count order_request_index_columns
  local order_status_index_columns service_order_event_count

  migration_count="$(legacy_migration_scalar 'SELECT COUNT(*) FROM schema_migration;')"
  legacy_migration_integer "schema_migration count" "${migration_count}"
  if ((migration_count > 0)); then
    return 0
  fi

  baseline_table_count="$(legacy_migration_scalar "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('category','merchant','user_account','user_address','auth_session','product','group_deal','cart_item','order_main','order_item','order_status_timeline','payment_record','coupon','review','merchant_favorite','chat_message','operation_log');")"
  legacy_migration_integer "V001 table count" "${baseline_table_count}"
  if ((baseline_table_count == 0)); then
    return 0
  fi
  if ((baseline_table_count != 17)); then
    echo "Refusing to adopt a partial legacy schema: found ${baseline_table_count}/17 V001 tables." >&2
    return 1
  fi

  payment_index_columns="$(legacy_migration_scalar "SELECT COALESCE(GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ','),'') FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='payment_record' AND index_name='uk_payment_request' AND non_unique=0;")"
  payment_processing_check_count="$(legacy_migration_scalar "SELECT COUNT(*) FROM information_schema.check_constraints WHERE constraint_schema=DATABASE() AND constraint_name='ck_payment_status' AND check_clause LIKE '%PROCESSING%';")"
  legacy_migration_integer "V002 payment status check count" "${payment_processing_check_count}"
  if [[ "${payment_index_columns}" != "user_id,client_request_id" ]] || ((payment_processing_check_count != 1)); then
    echo "Refusing to adopt legacy schema: V002 payment idempotency signature is incomplete." >&2
    return 1
  fi

  business_state_count="$(legacy_migration_scalar "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='business_state';")"
  service_catalog_count="$(legacy_migration_scalar "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('merchant_catalog','order_record');")"
  service_domain_count="$(legacy_migration_scalar "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('service_cart_item','service_payment','service_coupon','service_review');")"
  legacy_migration_integer "V003 table count" "${business_state_count}"
  legacy_migration_integer "V004 table count" "${service_catalog_count}"
  legacy_migration_integer "V005 table count" "${service_domain_count}"
  if ((business_state_count != 1 || service_catalog_count != 2 || service_domain_count != 4)); then
    echo "Refusing to adopt legacy schema: V003-V005 service tables are incomplete." >&2
    return 1
  fi

  order_state_column_count="$(legacy_migration_scalar "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='order_record' AND column_name IN ('order_type','client_request_id','coupon_code','address_id','reviewed','version');")"
  order_request_index_columns="$(legacy_migration_scalar "SELECT COALESCE(GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ','),'') FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='order_record' AND index_name='uq_order_client_request' AND non_unique=0;")"
  order_status_index_columns="$(legacy_migration_scalar "SELECT COALESCE(GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ','),'') FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='order_record' AND index_name='idx_order_merchant_status';")"
  service_order_event_count="$(legacy_migration_scalar "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='service_order_event';")"
  legacy_migration_integer "V006 column count" "${order_state_column_count}"
  legacy_migration_integer "V006 event table count" "${service_order_event_count}"
  if ((order_state_column_count != 6 || service_order_event_count != 1)) ||
    [[ "${order_request_index_columns}" != "user_id,client_request_id" ]] ||
    [[ "${order_status_index_columns}" != "merchant_id,status,id" ]]; then
    echo "Refusing to adopt legacy schema: V006 order-domain signature is incomplete." >&2
    return 1
  fi

  local version file filename description checksum values=""
  local -a versions=(V001 V002 V003 V004 V005 V006)
  local -a migration_files
  for version in "${versions[@]}"; do
    migration_files=("${migration_directory}/${version}__"*.sql)
    if ((${#migration_files[@]} != 1)) || [[ ! -f "${migration_files[0]}" ]]; then
      echo "Expected exactly one migration file for ${version}." >&2
      return 1
    fi
    file="${migration_files[0]}"
    filename="$(basename "${file}")"
    description="${filename#*__}"
    description="${description%.sql}"
    checksum="$(sha256sum "${file}" | awk '{print $1}')"
    if [[ ! "${description}" =~ ^[a-zA-Z0-9_]+$ ]] || [[ ! "${checksum}" =~ ^[0-9a-f]{64}$ ]]; then
      echo "Unsafe migration metadata for ${filename}." >&2
      return 1
    fi
    if [[ -n "${values}" ]]; then
      values+=","
    fi
    values+="('${version}','${description}','${checksum}')"
  done

  mysql_exec_remote --execute="INSERT INTO schema_migration(version,description,checksum) VALUES ${values};"
  echo "Adopted legacy migration history for V001-V006 after validating the existing schema."
}
