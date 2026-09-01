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

copy_owned_tables() {
  target_host=$1
  target_database=$2
  shift 2
  wait_for_mysql "$target_host" "$target_database"
  MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP --host="$target_host" --user="$MYSQL_USER" \
    --database="$target_database" --execute='SET FOREIGN_KEY_CHECKS=0'
  MYSQL_PWD="$MYSQL_PASSWORD" mysqldump --protocol=TCP --host="$MYSQL_HOST" --user="$MYSQL_USER" \
    --no-data --skip-add-drop-table --skip-triggers --set-gtid-purged=OFF "$MYSQL_DATABASE" "$@" \
    | MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP --host="$target_host" --user="$MYSQL_USER" --database="$target_database"
  MYSQL_PWD="$MYSQL_PASSWORD" mysqldump --protocol=TCP --host="$MYSQL_HOST" --user="$MYSQL_USER" \
    --no-create-info --skip-triggers --set-gtid-purged=OFF "$MYSQL_DATABASE" "$@" \
    | MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP --host="$target_host" --user="$MYSQL_USER" --database="$target_database"
  MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP --host="$target_host" --user="$MYSQL_USER" \
    --database="$target_database" --execute='SET FOREIGN_KEY_CHECKS=1'
}

wait_for_mysql "$MYSQL_HOST" "$MYSQL_DATABASE"
copy_owned_tables "$IDENTITY_MYSQL_HOST" "$MYSQL_IDENTITY_DATABASE" schema_migration user_account user_address auth_session
copy_owned_tables "$MERCHANT_MYSQL_HOST" "$MYSQL_MERCHANT_DATABASE" schema_migration category merchant merchant_catalog group_deal merchant_favorite chat_message inventory_reservation inventory_reservation_item merchant_inbox_event merchant_outbox_event
copy_owned_tables "$ORDER_MYSQL_HOST" "$MYSQL_ORDER_DATABASE" schema_migration order_record service_cart_item service_payment service_coupon service_review service_order_event service_order_line service_outbox_event order_inbox_event order_inventory_saga
echo "Copied owned tables from $MYSQL_HOST into three independent MySQL instances."
