#!/usr/bin/env sh
set -eu
. "$(dirname "$0")/common.sh"

echo "Backfilling service-owned catalog and order tables (idempotent)"
mysql_exec <<'SQL'
INSERT INTO merchant_catalog (id, merchant_id, name, description, price_cent, stock, listed)
SELECT p.id, p.merchant_id, p.name, COALESCE(p.description,''), p.price_cent, p.stock, p.listed
FROM product p
ON DUPLICATE KEY UPDATE name=VALUES(name), description=VALUES(description), price_cent=VALUES(price_cent), stock=VALUES(stock), listed=VALUES(listed);

INSERT INTO order_record (id, user_id, merchant_id, product_id, quantity, total_cent, status, created_at)
SELECT o.id, o.user_id, o.merchant_id, MIN(oi.item_id), MIN(oi.quantity), o.total_cent, o.status, o.created_at
FROM order_main o JOIN order_item oi ON oi.order_id=o.id
GROUP BY o.id, o.user_id, o.merchant_id, o.total_cent, o.status, o.created_at
ON DUPLICATE KEY UPDATE status=VALUES(status), total_cent=VALUES(total_cent);
SQL
echo "Backfill completed"
