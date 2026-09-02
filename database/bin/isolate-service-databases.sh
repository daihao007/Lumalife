#!/bin/sh
set -eu

: "${MYSQL_HOST:?MYSQL_HOST is required}"
: "${MYSQL_DATABASE:?MYSQL_DATABASE is required}"
: "${MYSQL_USER:?MYSQL_USER is required}"
: "${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}"
: "${IDENTITY_MYSQL_HOST:?IDENTITY_MYSQL_HOST is required}"
: "${MERCHANT_MYSQL_HOST:?MERCHANT_MYSQL_HOST is required}"
: "${ORDER_MYSQL_HOST:?ORDER_MYSQL_HOST is required}"
: "${MYSQL_IDENTITY_DATABASE:?MYSQL_IDENTITY_DATABASE is required}"
: "${MYSQL_MERCHANT_DATABASE:?MYSQL_MERCHANT_DATABASE is required}"
: "${MYSQL_ORDER_DATABASE:?MYSQL_ORDER_DATABASE is required}"

wait_for_mysql() {
  host=$1
  database=$2
  attempts=0
  while ! MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP --host="$host" --user="$MYSQL_USER" --database="$database" --execute='SELECT 1' >/dev/null 2>&1; do
    attempts=$((attempts + 1))
    [ "$attempts" -lt 60 ] || { echo "Timed out waiting for $host/$database" >&2; exit 1; }
    sleep 2
  done
}

strip_foreign_keys() {
  # The legacy schema contains relationships owned by other services. Keep
  # columns, indexes and local CHECK constraints, but never recreate those
  # cross-service foreign keys in an isolated database. The pending-line
  # handling also removes a trailing comma when the removed constraint was the
  # final item in a CREATE TABLE statement.
  awk '
    function flush() {
      if (pending != "") print pending
    }
    tolower($0) ~ /foreign[[:space:]]+key/ { next }
    {
      if ($0 ~ /^[[:space:]]*\)/ && pending ~ /,[[:space:]]*$/) {
        sub(/,[[:space:]]*$/, "", pending)
      }
      flush()
      pending=$0
    }
    END { flush() }
  '
}

copy_owned_tables() {
  target_host=$1
  target_database=$2
  shift 2
  wait_for_mysql "$target_host" "$target_database"
  # The first isolation copies the legacy snapshot. Once a target has its own
  # migration ledger, it is the service's source of truth and must not be
  # overwritten by a later application rollout.
  target_migrations="$(MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP --host="$target_host" --user="$MYSQL_USER" \
    --database="$target_database" --batch --skip-column-names \
    --execute='SELECT COUNT(*) FROM schema_migration' 2>/dev/null || true)"
  if [ "${target_migrations:-0}" -gt 0 ] 2>/dev/null; then
    echo "Skipping initialized service database ${target_database}."
    return
  fi
  {
    printf '%s\n' 'SET FOREIGN_KEY_CHECKS=0;'
    MYSQL_PWD="$MYSQL_PASSWORD" mysqldump --protocol=TCP --host="$MYSQL_HOST" --user="$MYSQL_USER" \
      --no-tablespaces --no-data --skip-add-drop-table --skip-triggers --set-gtid-purged=OFF "$MYSQL_DATABASE" "$@" \
      | strip_foreign_keys
    printf '%s\n' 'SET FOREIGN_KEY_CHECKS=1;'
  } | MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP --host="$target_host" --user="$MYSQL_USER" --database="$target_database"
  {
    printf '%s\n' 'SET FOREIGN_KEY_CHECKS=0;'
    MYSQL_PWD="$MYSQL_PASSWORD" mysqldump --protocol=TCP --host="$MYSQL_HOST" --user="$MYSQL_USER" \
      --no-tablespaces --no-create-info --skip-triggers --set-gtid-purged=OFF "$MYSQL_DATABASE" "$@"
    printf '%s\n' 'SET FOREIGN_KEY_CHECKS=1;'
  } | MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP --host="$target_host" --user="$MYSQL_USER" --database="$target_database"
}

sync_latest_migration_marker() {
  target_host=$1
  target_database=$2
  migration_file=/database/migrations/V018__chat_sender_role_contract.sql
  migration_checksum=$(sha256sum "$migration_file" | awk '{print $1}')
  MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP --host="$target_host" --user="$MYSQL_USER" \
    --database="$target_database" --execute="INSERT INTO schema_migration(version,description,checksum) VALUES ('V018','chat_sender_role_contract','${migration_checksum}') ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum)"
}

upgrade_merchant_chat_constraint() {
  target_host=$1
  target_database=$2
  constraint_count=$(MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP --host="$target_host" --user="$MYSQL_USER" \
    --database="$target_database" --batch --skip-column-names \
    --execute="SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='${target_database}' AND table_name='chat_message' AND constraint_name='ck_chat_sender_role'")
  if [ "$constraint_count" -gt 0 ]; then
    MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP --host="$target_host" --user="$MYSQL_USER" \
      --database="$target_database" --execute='ALTER TABLE chat_message DROP CHECK ck_chat_sender_role'
  fi
  MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP --host="$target_host" --user="$MYSQL_USER" \
    --database="$target_database" --execute="ALTER TABLE chat_message ADD CONSTRAINT ck_chat_sender_role CHECK (sender_role IN ('USER','MERCHANT','MERCHANT_AI','MERCHANT_ADMIN','PLATFORM_ADMIN','ASSISTANT'))"
}

upgrade_order_saga_constraint() {
  target_host=$1
  target_database=$2
  order_saga_status_constraint_count=$(MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP --host="$target_host" --user="$MYSQL_USER" \
    --database="$target_database" --batch --skip-column-names \
    --execute="SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='${target_database}' AND table_name='order_inventory_saga' AND constraint_name='ck_order_inventory_saga_status'")
  if [ "$order_saga_status_constraint_count" -gt 0 ]; then
    MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP --host="$target_host" --user="$MYSQL_USER" \
      --database="$target_database" --execute='ALTER TABLE order_inventory_saga DROP CHECK ck_order_inventory_saga_status'
  fi
  MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP --host="$target_host" --user="$MYSQL_USER" \
    --database="$target_database" --execute="ALTER TABLE order_inventory_saga ADD CONSTRAINT ck_order_inventory_saga_status CHECK (status IN ('RESERVE_PENDING','RESERVED','RESERVE_FAILED','CONFIRM_PENDING','CONFIRMED','CONFIRM_FAILED','RELEASE_PENDING','RELEASED','FAILED'))"
}

wait_for_mysql "$MYSQL_HOST" "$MYSQL_DATABASE"
copy_owned_tables "$IDENTITY_MYSQL_HOST" "$MYSQL_IDENTITY_DATABASE" schema_migration user_account user_address auth_session
copy_owned_tables "$MERCHANT_MYSQL_HOST" "$MYSQL_MERCHANT_DATABASE" schema_migration category merchant merchant_catalog group_deal merchant_favorite chat_message inventory_reservation inventory_reservation_item merchant_inbox_event merchant_outbox_event
copy_owned_tables "$ORDER_MYSQL_HOST" "$MYSQL_ORDER_DATABASE" schema_migration order_record service_cart_item service_payment service_coupon service_review service_order_event service_order_line service_outbox_event order_inbox_event order_inventory_saga
upgrade_merchant_chat_constraint "$MERCHANT_MYSQL_HOST" "$MYSQL_MERCHANT_DATABASE"
sync_latest_migration_marker "$IDENTITY_MYSQL_HOST" "$MYSQL_IDENTITY_DATABASE"
sync_latest_migration_marker "$MERCHANT_MYSQL_HOST" "$MYSQL_MERCHANT_DATABASE"
upgrade_order_saga_constraint "$ORDER_MYSQL_HOST" "$MYSQL_ORDER_DATABASE"
sync_latest_migration_marker "$ORDER_MYSQL_HOST" "$MYSQL_ORDER_DATABASE"
echo "Copied owned tables from $MYSQL_HOST into three independent MySQL instances."
