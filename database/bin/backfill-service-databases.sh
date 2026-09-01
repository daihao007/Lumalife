#!/bin/sh
set -eu

: "${MYSQL_HOST:?MYSQL_HOST is required}"
: "${MYSQL_DATABASE:?MYSQL_DATABASE is required}"
: "${MYSQL_USER:?MYSQL_USER is required}"
: "${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}"

MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_IDENTITY_DATABASE="${MYSQL_IDENTITY_DATABASE:-${MYSQL_DATABASE}_identity}"
MYSQL_MERCHANT_DATABASE="${MYSQL_MERCHANT_DATABASE:-${MYSQL_DATABASE}_merchant}"
MYSQL_ORDER_DATABASE="${MYSQL_ORDER_DATABASE:-${MYSQL_DATABASE}_order}"

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

mysql_exec() {
  MYSQL_PWD="$MYSQL_PASSWORD" mysql \
    --protocol=TCP \
    --host="$MYSQL_HOST" \
    --port="$MYSQL_PORT" \
    --user="$MYSQL_USER" \
    --default-character-set=utf8mb4 \
    "$@"
}

mysql_exec <<SQL
INSERT INTO ${MYSQL_IDENTITY_DATABASE}.user_account
  (id, phone, password_hash, nickname, avatar_url, role, merchant_id, created_at, updated_at, is_deleted)
SELECT id, phone, password_hash, nickname, avatar_url, role, merchant_id, created_at, updated_at, is_deleted
FROM ${MYSQL_DATABASE}.user_account
ON DUPLICATE KEY UPDATE phone=VALUES(phone), password_hash=VALUES(password_hash), nickname=VALUES(nickname), avatar_url=VALUES(avatar_url), role=VALUES(role), merchant_id=VALUES(merchant_id), updated_at=VALUES(updated_at), is_deleted=VALUES(is_deleted);

INSERT INTO ${MYSQL_IDENTITY_DATABASE}.user_address
  (id, user_id, contact_name, phone, detail, is_default, created_at, updated_at, is_deleted)
SELECT id, user_id, contact_name, phone, detail, is_default, created_at, updated_at, is_deleted
FROM ${MYSQL_DATABASE}.user_address
ON DUPLICATE KEY UPDATE user_id=VALUES(user_id), contact_name=VALUES(contact_name), phone=VALUES(phone), detail=VALUES(detail), is_default=VALUES(is_default), updated_at=VALUES(updated_at), is_deleted=VALUES(is_deleted);

INSERT INTO ${MYSQL_IDENTITY_DATABASE}.auth_session
  (id, user_id, token_hash, expires_at, revoked_at, created_at)
SELECT id, user_id, token_hash, expires_at, revoked_at, created_at
FROM ${MYSQL_DATABASE}.auth_session
ON DUPLICATE KEY UPDATE user_id=VALUES(user_id), token_hash=VALUES(token_hash), expires_at=VALUES(expires_at), revoked_at=VALUES(revoked_at);

INSERT INTO ${MYSQL_MERCHANT_DATABASE}.category
  (id, name, icon, created_at, updated_at, is_deleted)
SELECT id, name, icon, created_at, updated_at, is_deleted
FROM ${MYSQL_DATABASE}.category
ON DUPLICATE KEY UPDATE name=VALUES(name), icon=VALUES(icon), updated_at=VALUES(updated_at), is_deleted=VALUES(is_deleted);

INSERT INTO ${MYSQL_MERCHANT_DATABASE}.merchant
  (id, category_id, name, cover_url, avg_score, avg_price_cent, monthly_sales, distance_km, status, address, recommend_reason, created_at, updated_at, is_deleted)
SELECT id, category_id, name, cover_url, avg_score, avg_price_cent, monthly_sales, distance_km, status, address, recommend_reason, created_at, updated_at, is_deleted
FROM ${MYSQL_DATABASE}.merchant
ON DUPLICATE KEY UPDATE category_id=VALUES(category_id), name=VALUES(name), cover_url=VALUES(cover_url), avg_score=VALUES(avg_score), avg_price_cent=VALUES(avg_price_cent), monthly_sales=VALUES(monthly_sales), distance_km=VALUES(distance_km), status=VALUES(status), address=VALUES(address), recommend_reason=VALUES(recommend_reason), updated_at=VALUES(updated_at), is_deleted=VALUES(is_deleted);

INSERT INTO ${MYSQL_MERCHANT_DATABASE}.merchant_catalog
  (id, merchant_id, name, description, price_cent, stock, listed, version, updated_at)
SELECT id, merchant_id, name, description, price_cent, stock, listed, version, updated_at
FROM ${MYSQL_DATABASE}.merchant_catalog
ON DUPLICATE KEY UPDATE merchant_id=VALUES(merchant_id), name=VALUES(name), description=VALUES(description), price_cent=VALUES(price_cent), stock=VALUES(stock), listed=VALUES(listed), version=VALUES(version), updated_at=VALUES(updated_at);

INSERT INTO ${MYSQL_MERCHANT_DATABASE}.group_deal
  (id, merchant_id, title, description, price_cent, stock, is_active, version, created_at, updated_at, is_deleted)
SELECT id, merchant_id, title, description, price_cent, stock, is_active, version, created_at, updated_at, is_deleted
FROM ${MYSQL_DATABASE}.group_deal
ON DUPLICATE KEY UPDATE merchant_id=VALUES(merchant_id), title=VALUES(title), description=VALUES(description), price_cent=VALUES(price_cent), stock=VALUES(stock), is_active=VALUES(is_active), version=VALUES(version), updated_at=VALUES(updated_at), is_deleted=VALUES(is_deleted);

INSERT INTO ${MYSQL_MERCHANT_DATABASE}.merchant_favorite (user_id, merchant_id, created_at)
SELECT user_id, merchant_id, created_at FROM ${MYSQL_DATABASE}.merchant_favorite
ON DUPLICATE KEY UPDATE created_at=VALUES(created_at);

INSERT INTO ${MYSQL_MERCHANT_DATABASE}.chat_message
  (id, user_id, merchant_id, sender_role, sender_name, content, created_at)
SELECT id, user_id, merchant_id, sender_role, sender_name, content, created_at
FROM ${MYSQL_DATABASE}.chat_message
ON DUPLICATE KEY UPDATE sender_role=VALUES(sender_role), sender_name=VALUES(sender_name), content=VALUES(content), created_at=VALUES(created_at);

INSERT INTO ${MYSQL_ORDER_DATABASE}.order_record
  (id, user_id, merchant_id, merchant_name_snapshot, product_id, quantity, total_cent, status, created_at, order_type, client_request_id, coupon_code, address_id, address_snapshot, reviewed, version)
SELECT id, user_id, merchant_id, merchant_name_snapshot, product_id, quantity, total_cent, status, created_at, order_type, client_request_id, coupon_code, address_id, address_snapshot, reviewed, version
FROM ${MYSQL_DATABASE}.order_record
ON DUPLICATE KEY UPDATE user_id=VALUES(user_id), merchant_id=VALUES(merchant_id), merchant_name_snapshot=VALUES(merchant_name_snapshot), product_id=VALUES(product_id), quantity=VALUES(quantity), total_cent=VALUES(total_cent), status=VALUES(status), order_type=VALUES(order_type), client_request_id=VALUES(client_request_id), coupon_code=VALUES(coupon_code), address_id=VALUES(address_id), address_snapshot=VALUES(address_snapshot), reviewed=VALUES(reviewed), version=VALUES(version);

INSERT INTO ${MYSQL_ORDER_DATABASE}.service_cart_item (user_id, product_id, quantity, updated_at)
SELECT user_id, product_id, quantity, updated_at FROM ${MYSQL_DATABASE}.service_cart_item
ON DUPLICATE KEY UPDATE quantity=VALUES(quantity), updated_at=VALUES(updated_at);

INSERT INTO ${MYSQL_ORDER_DATABASE}.service_payment (user_id, order_id, client_request_id, amount_cent, status, paid_at)
SELECT user_id, order_id, client_request_id, amount_cent, status, paid_at FROM ${MYSQL_DATABASE}.service_payment
ON DUPLICATE KEY UPDATE order_id=VALUES(order_id), amount_cent=VALUES(amount_cent), status=VALUES(status), paid_at=VALUES(paid_at);

INSERT INTO ${MYSQL_ORDER_DATABASE}.service_coupon (code, order_id, merchant_id, status, redeemed_at)
SELECT code, order_id, merchant_id, status, redeemed_at FROM ${MYSQL_DATABASE}.service_coupon
ON DUPLICATE KEY UPDATE order_id=VALUES(order_id), merchant_id=VALUES(merchant_id), status=VALUES(status), redeemed_at=VALUES(redeemed_at);

INSERT INTO ${MYSQL_ORDER_DATABASE}.service_review
  (order_id, user_id, merchant_id, score, taste_score, service_score, content, created_at)
SELECT order_id, user_id, merchant_id, score, taste_score, service_score, content, created_at FROM ${MYSQL_DATABASE}.service_review
ON DUPLICATE KEY UPDATE user_id=VALUES(user_id), merchant_id=VALUES(merchant_id), score=VALUES(score), taste_score=VALUES(taste_score), service_score=VALUES(service_score), content=VALUES(content), created_at=VALUES(created_at);

INSERT INTO ${MYSQL_ORDER_DATABASE}.service_order_event (order_id, version, status, actor_id, occurred_at)
SELECT order_id, version, status, actor_id, occurred_at FROM ${MYSQL_DATABASE}.service_order_event
ON DUPLICATE KEY UPDATE status=VALUES(status), actor_id=VALUES(actor_id), occurred_at=VALUES(occurred_at);

INSERT INTO ${MYSQL_ORDER_DATABASE}.service_order_line (order_id, line_no, item_id, item_name, quantity, price_cent)
SELECT order_id, line_no, item_id, item_name, quantity, price_cent FROM ${MYSQL_DATABASE}.service_order_line
ON DUPLICATE KEY UPDATE item_id=VALUES(item_id), item_name=VALUES(item_name), quantity=VALUES(quantity), price_cent=VALUES(price_cent);

INSERT INTO ${MYSQL_ORDER_DATABASE}.service_outbox_event
  (id, aggregate_type, aggregate_id, event_type, payload, status, occurred_at, published_at)
SELECT id, aggregate_type, aggregate_id, event_type, payload, status, occurred_at, published_at FROM ${MYSQL_DATABASE}.service_outbox_event
ON DUPLICATE KEY UPDATE aggregate_type=VALUES(aggregate_type), aggregate_id=VALUES(aggregate_id), event_type=VALUES(event_type), payload=VALUES(payload), status=VALUES(status), occurred_at=VALUES(occurred_at), published_at=VALUES(published_at);
SQL

echo "Backfilled identity, merchant and order databases from ${MYSQL_DATABASE}."
