#!/bin/sh
set -eu

. /database/bin/common.sh
wait_for_mysql

if [ "${ALLOW_DATABASE_CLEAN:-}" != "true" ]; then
  echo "Refusing to delete business data. Set ALLOW_DATABASE_CLEAN=true explicitly." >&2
  exit 1
fi

mysql_exec < /database/cleanup/clean-data.sql

service_mysql_exec() {
  database=$1
  shift
  MYSQL_PWD="$MYSQL_PASSWORD" mysql \
    --protocol=TCP \
    --host="$MYSQL_HOST" \
    --port="$MYSQL_PORT" \
    --user="$MYSQL_USER" \
    --database="$database" \
    --default-character-set=utf8mb4 \
    "$@"
}

service_mysql_exec "$MYSQL_IDENTITY_DATABASE" <<'SQL'
DELETE FROM auth_session;
DELETE FROM user_address;
DELETE FROM user_account;
SQL

service_mysql_exec "$MYSQL_MERCHANT_DATABASE" <<'SQL'
DELETE FROM chat_message;
DELETE FROM merchant_favorite;
DELETE FROM inventory_reservation_item;
DELETE FROM inventory_reservation;
DELETE FROM group_deal;
DELETE FROM merchant_catalog;
DELETE FROM merchant;
DELETE FROM category;
SQL

service_mysql_exec "$MYSQL_ORDER_DATABASE" <<'SQL'
DELETE FROM service_outbox_event;
DELETE FROM service_order_event;
DELETE FROM service_order_line;
DELETE FROM service_review;
DELETE FROM service_coupon;
DELETE FROM service_payment;
DELETE FROM service_cart_item;
DELETE FROM order_record;
SQL

echo "Business data removed; migration history retained."
