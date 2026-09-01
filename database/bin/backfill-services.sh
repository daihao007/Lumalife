#!/usr/bin/env sh
set -eu
. "$(dirname "$0")/common.sh"

echo "Backfilling service-owned catalog and order tables (idempotent)"
mysql_exec <<'SQL'
INSERT INTO merchant_catalog (id, merchant_id, name, description, price_cent, stock, listed)
SELECT p.id, p.merchant_id, p.name, COALESCE(p.description,''), p.price_cent, p.stock, p.listed
FROM product p
ON DUPLICATE KEY UPDATE name=VALUES(name), description=VALUES(description), price_cent=VALUES(price_cent), stock=VALUES(stock), listed=VALUES(listed);

INSERT INTO order_record (id, user_id, merchant_id, product_id, quantity, total_cent, status, order_type, client_request_id, coupon_code, address_id, reviewed, version, created_at)
SELECT o.id, o.user_id, o.merchant_id, MIN(oi.item_id), MIN(oi.quantity), o.total_cent, o.status, o.order_type,
       (SELECT p.client_request_id FROM payment_record p WHERE p.order_id=o.id ORDER BY p.id DESC LIMIT 1),
       (SELECT c.code FROM coupon c WHERE c.order_id=o.id ORDER BY c.id DESC LIMIT 1),
       o.address_id, o.is_reviewed, o.version, o.created_at
FROM order_main o JOIN order_item oi ON oi.order_id=o.id
GROUP BY o.id, o.user_id, o.merchant_id, o.total_cent, o.status, o.created_at
ON DUPLICATE KEY UPDATE status=VALUES(status), total_cent=VALUES(total_cent), order_type=VALUES(order_type), client_request_id=VALUES(client_request_id), coupon_code=VALUES(coupon_code), address_id=VALUES(address_id), reviewed=VALUES(reviewed), version=VALUES(version);

-- The order service owns the rich order tables after cutover. Keep this
-- migration idempotent so an existing V004/V006 database can be switched
-- without losing item names, address snapshots, or status history.
INSERT INTO order_main (id, user_id, merchant_id, merchant_name_snapshot, order_type, status, total_cent, client_request_id, coupon_code, address_id, address_snapshot, is_reviewed, is_stock_deducted, version, created_at)
SELECT o.id, o.user_id, o.merchant_id, COALESCE(m.name, CONCAT('商家 #', o.merchant_id)), o.order_type, o.status, o.total_cent, o.client_request_id, o.coupon_code, o.address_id, NULL, o.reviewed, 0, o.version, o.created_at
FROM order_record o LEFT JOIN merchant m ON m.id=o.merchant_id
ON DUPLICATE KEY UPDATE status=VALUES(status), total_cent=VALUES(total_cent), client_request_id=VALUES(client_request_id), coupon_code=VALUES(coupon_code), address_id=VALUES(address_id), is_reviewed=VALUES(is_reviewed), version=VALUES(version);

INSERT INTO order_item (order_id, item_type, item_id, item_name_snapshot, quantity, unit_price_cent)
SELECT o.id, IF(o.order_type='GROUP_BUY','GROUP_DEAL','PRODUCT'), o.product_id,
       COALESCE(p.name, CONCAT('商品 #', o.product_id)), o.quantity, o.total_cent / GREATEST(o.quantity, 1)
FROM order_record o LEFT JOIN product p ON p.id=o.product_id
WHERE NOT EXISTS (SELECT 1 FROM order_item i WHERE i.order_id=o.id);

INSERT INTO service_order_event (order_id, version, status, actor_id, occurred_at)
SELECT t.order_id, t.id, t.status, 0, t.occurred_at
FROM order_status_timeline t
ON DUPLICATE KEY UPDATE status=VALUES(status), occurred_at=VALUES(occurred_at);

INSERT INTO service_cart_item (user_id, product_id, quantity)
SELECT user_id, product_id, quantity FROM cart_item
ON DUPLICATE KEY UPDATE quantity=VALUES(quantity);

INSERT INTO service_payment (user_id, order_id, client_request_id, amount_cent, status, paid_at)
SELECT user_id, order_id, client_request_id, amount_cent, status, paid_at FROM payment_record
ON DUPLICATE KEY UPDATE status=VALUES(status), paid_at=VALUES(paid_at);

INSERT INTO service_coupon (code, order_id, merchant_id, status, redeemed_at)
SELECT code, order_id, merchant_id, status, redeemed_at FROM coupon
ON DUPLICATE KEY UPDATE status=VALUES(status), redeemed_at=VALUES(redeemed_at);

INSERT INTO service_review (order_id, user_id, merchant_id, score, taste_score, service_score, content, created_at)
SELECT order_id, user_id, merchant_id, score, taste_score, service_score, content, created_at FROM review
ON DUPLICATE KEY UPDATE score=VALUES(score), content=VALUES(content);
SQL
echo "Backfill completed"
