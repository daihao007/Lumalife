#!/bin/sh
set -eu

: "${MYSQL_DATABASE:?MYSQL_DATABASE is required}"
: "${MYSQL_USER:?MYSQL_USER is required}"
: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_IDENTITY_DATABASE="${MYSQL_IDENTITY_DATABASE:-${MYSQL_DATABASE}_identity}"
MYSQL_MERCHANT_DATABASE="${MYSQL_MERCHANT_DATABASE:-${MYSQL_DATABASE}_merchant}"
MYSQL_ORDER_DATABASE="${MYSQL_ORDER_DATABASE:-${MYSQL_DATABASE}_order}"

if [ -n "${MYSQL_SOCKET:-}" ] && [ ! -S "$MYSQL_SOCKET" ]; then
  for candidate_socket in /var/lib/mysql/mysql.sock /var/run/mysqld/mysqld.sock; do
    if [ -S "$candidate_socket" ]; then
      MYSQL_SOCKET="$candidate_socket"
      break
    fi
  done
fi

validate_identifier() {
  value=$1
  label=$2
  case "$value" in
    ''|*[!A-Za-z0-9_]*)
      echo "${label} may contain only letters, digits and underscores." >&2
      exit 2
      ;;
  esac
}

validate_identifier "$MYSQL_DATABASE" MYSQL_DATABASE
validate_identifier "$MYSQL_IDENTITY_DATABASE" MYSQL_IDENTITY_DATABASE
validate_identifier "$MYSQL_MERCHANT_DATABASE" MYSQL_MERCHANT_DATABASE
validate_identifier "$MYSQL_ORDER_DATABASE" MYSQL_ORDER_DATABASE
validate_identifier "$MYSQL_USER" MYSQL_USER

mysql_root() {
  MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql \
    --protocol=TCP \
    --host="$MYSQL_HOST" \
    --port="$MYSQL_PORT" \
    --user=root \
    --default-character-set=utf8mb4 \
    "$@"
}

if [ -n "${MYSQL_SOCKET:-}" ]; then
  mysql_root() {
    MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql \
      --protocol=socket \
      --socket="$MYSQL_SOCKET" \
      --user=root \
      --default-character-set=utf8mb4 \
      "$@"
  }
fi

mysql_root --execute="
CREATE DATABASE IF NOT EXISTS ${MYSQL_IDENTITY_DATABASE} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS ${MYSQL_MERCHANT_DATABASE} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS ${MYSQL_ORDER_DATABASE} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
GRANT ALL PRIVILEGES ON ${MYSQL_IDENTITY_DATABASE}.* TO '${MYSQL_USER}'@'%';
GRANT ALL PRIVILEGES ON ${MYSQL_MERCHANT_DATABASE}.* TO '${MYSQL_USER}'@'%';
GRANT ALL PRIVILEGES ON ${MYSQL_ORDER_DATABASE}.* TO '${MYSQL_USER}'@'%';
FLUSH PRIVILEGES;
"

copy_table() {
  target_database=$1
  table_name=$2
  mysql_root --execute="CREATE TABLE IF NOT EXISTS ${target_database}.${table_name} LIKE ${MYSQL_DATABASE}.${table_name}"
}

# Each service receives only the tables it owns. CREATE TABLE ... LIKE copies
# columns and indexes without copying cross-service foreign keys.
for table_name in schema_migration user_account user_address auth_session; do
  copy_table "$MYSQL_IDENTITY_DATABASE" "$table_name"
done

for table_name in schema_migration category merchant merchant_catalog group_deal merchant_favorite chat_message inventory_reservation inventory_reservation_item merchant_inbox_event merchant_outbox_event; do
  copy_table "$MYSQL_MERCHANT_DATABASE" "$table_name"
done

# CREATE TABLE ... LIKE is intentionally idempotent, but it does not evolve an
# already provisioned service table.  V012 adds this column to the merchant
# catalog, so upgrade old service databases before the backfill runs.
merchant_catalog_version_count=$(mysql_root --batch --skip-column-names --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${MYSQL_MERCHANT_DATABASE}' AND table_name='merchant_catalog' AND column_name='version'")
if [ "$merchant_catalog_version_count" -eq 0 ]; then
  mysql_root --execute="ALTER TABLE ${MYSQL_MERCHANT_DATABASE}.merchant_catalog ADD COLUMN version BIGINT NOT NULL DEFAULT 0"
fi

for table_name in schema_migration order_record service_cart_item service_payment service_coupon service_review service_order_event service_order_line service_outbox_event order_inbox_event order_inventory_saga; do
  copy_table "$MYSQL_ORDER_DATABASE" "$table_name"
done

# CREATE TABLE ... LIKE is not a schema migration for an already existing
# service database. Keep the order-owned historical merchant snapshot additive
# for databases provisioned before V013.
order_merchant_name_snapshot_count=$(mysql_root --batch --skip-column-names --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${MYSQL_ORDER_DATABASE}' AND table_name='order_record' AND column_name='merchant_name_snapshot'")
if [ "$order_merchant_name_snapshot_count" -eq 0 ]; then
  mysql_root --execute="ALTER TABLE ${MYSQL_ORDER_DATABASE}.order_record ADD COLUMN merchant_name_snapshot VARCHAR(160) NOT NULL DEFAULT ''"
fi

# V019 adds stable outbox deduplication and expiry reconciliation bookkeeping.
merchant_outbox_dedup_count=$(mysql_root --batch --skip-column-names --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${MYSQL_MERCHANT_DATABASE}' AND table_name='merchant_outbox_event' AND column_name='deduplication_key'")
if [ "$merchant_outbox_dedup_count" -eq 0 ]; then
  mysql_root --execute="ALTER TABLE ${MYSQL_MERCHANT_DATABASE}.merchant_outbox_event ADD COLUMN deduplication_key VARCHAR(255) NULL"
fi
merchant_outbox_index_count=$(mysql_root --batch --skip-column-names --execute="SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='${MYSQL_MERCHANT_DATABASE}' AND table_name='merchant_outbox_event' AND index_name='uk_merchant_outbox_deduplication'")
if [ "$merchant_outbox_index_count" -eq 0 ]; then
  mysql_root --execute="ALTER TABLE ${MYSQL_MERCHANT_DATABASE}.merchant_outbox_event ADD UNIQUE KEY uk_merchant_outbox_deduplication (deduplication_key)"
fi
for column_definition in \
  'expiry_attempts INT UNSIGNED NOT NULL DEFAULT 0' \
  'expiry_last_attempt_at DATETIME(3) NULL' \
  'expiry_last_error VARCHAR(1000) NULL'; do
  column_name=${column_definition%% *}
  expiry_column_count=$(mysql_root --batch --skip-column-names --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${MYSQL_MERCHANT_DATABASE}' AND table_name='inventory_reservation' AND column_name='${column_name}'")
  if [ "$expiry_column_count" -eq 0 ]; then
    mysql_root --execute="ALTER TABLE ${MYSQL_MERCHANT_DATABASE}.inventory_reservation ADD COLUMN ${column_definition}"
  fi
done
service_outbox_dedup_count=$(mysql_root --batch --skip-column-names --execute="SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${MYSQL_ORDER_DATABASE}' AND table_name='service_outbox_event' AND column_name='deduplication_key'")
if [ "$service_outbox_dedup_count" -eq 0 ]; then
  mysql_root --execute="ALTER TABLE ${MYSQL_ORDER_DATABASE}.service_outbox_event ADD COLUMN deduplication_key VARCHAR(255) NULL"
fi
service_outbox_index_count=$(mysql_root --batch --skip-column-names --execute="SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='${MYSQL_ORDER_DATABASE}' AND table_name='service_outbox_event' AND index_name='uk_service_outbox_deduplication'")
if [ "$service_outbox_index_count" -eq 0 ]; then
  mysql_root --execute="ALTER TABLE ${MYSQL_ORDER_DATABASE}.service_outbox_event ADD UNIQUE KEY uk_service_outbox_deduplication (deduplication_key)"
fi

# The public conversation projection uses MERCHANT and MERCHANT_AI. Upgrade an
# already provisioned merchant database as well as freshly copied tables.
merchant_chat_sender_constraint_count=$(mysql_root --batch --skip-column-names --execute="SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='${MYSQL_MERCHANT_DATABASE}' AND table_name='chat_message' AND constraint_name='ck_chat_sender_role'")
if [ "$merchant_chat_sender_constraint_count" -gt 0 ]; then
  mysql_root --execute="ALTER TABLE ${MYSQL_MERCHANT_DATABASE}.chat_message DROP CHECK ck_chat_sender_role"
fi
mysql_root --execute="ALTER TABLE ${MYSQL_MERCHANT_DATABASE}.chat_message ADD CONSTRAINT ck_chat_sender_role CHECK (sender_role IN ('USER','MERCHANT','MERCHANT_AI','MERCHANT_ADMIN','PLATFORM_ADMIN','ASSISTANT'))"

# Keep an already provisioned order database on the same Saga state machine as
# the canonical schema. This is additive and idempotent, so an upgrade does
# not silently retain the pre-compensation constraint from V016.
order_saga_status_constraint_count=$(mysql_root --batch --skip-column-names --execute="SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='${MYSQL_ORDER_DATABASE}' AND table_name='order_inventory_saga' AND constraint_name='ck_order_inventory_saga_status'")
if [ "$order_saga_status_constraint_count" -gt 0 ]; then
  mysql_root --execute="ALTER TABLE ${MYSQL_ORDER_DATABASE}.order_inventory_saga DROP CHECK ck_order_inventory_saga_status"
fi
mysql_root --execute="ALTER TABLE ${MYSQL_ORDER_DATABASE}.order_inventory_saga ADD CONSTRAINT ck_order_inventory_saga_status CHECK (status IN ('RESERVE_PENDING','RESERVED','RESERVE_FAILED','CONFIRM_PENDING','CONFIRMED','CONFIRM_FAILED','RELEASE_PENDING','RELEASED','CHECK_REQUIRED','RELEASE_FAILED','FAILED'))"

for target_database in "$MYSQL_IDENTITY_DATABASE" "$MYSQL_MERCHANT_DATABASE" "$MYSQL_ORDER_DATABASE"; do
  mysql_root --execute="INSERT INTO ${target_database}.schema_migration(version,description,checksum,installed_at) SELECT version,description,checksum,installed_at FROM ${MYSQL_DATABASE}.schema_migration ON DUPLICATE KEY UPDATE description=VALUES(description),checksum=VALUES(checksum),installed_at=VALUES(installed_at)"
done

echo "Provisioned isolated service databases: ${MYSQL_IDENTITY_DATABASE}, ${MYSQL_MERCHANT_DATABASE}, ${MYSQL_ORDER_DATABASE}."
