INSERT INTO merchant_catalog (id, merchant_id, name, description, price_cent, stock, listed, version)
SELECT p.id, p.merchant_id, p.name, COALESCE(p.description,''), p.price_cent, p.stock, p.is_listed, p.version
FROM product p
ON DUPLICATE KEY UPDATE name=VALUES(name), description=VALUES(description), price_cent=VALUES(price_cent), stock=VALUES(stock), listed=VALUES(listed), version=VALUES(version);

INSERT INTO service_order_line (order_id, line_no, item_id, item_name, quantity, price_cent)
SELECT source.order_id, source.line_no, COALESCE(source.item_id, 0), source.item_name, source.quantity, source.price_cent
FROM (
  SELECT oi.order_id, oi.id AS line_no, oi.item_id, oi.item_name_snapshot AS item_name,
         oi.quantity, oi.unit_price_cent AS price_cent
  FROM order_item oi
) source
ON DUPLICATE KEY UPDATE item_id=VALUES(item_id), item_name=VALUES(item_name), quantity=VALUES(quantity), price_cent=VALUES(price_cent);

INSERT INTO order_record (id, user_id, merchant_id, merchant_name_snapshot, product_id, quantity, total_cent, status, order_type, client_request_id, coupon_code, address_id, address_snapshot, reviewed, version, created_at)
SELECT o.id, o.user_id, o.merchant_id, o.merchant_name_snapshot, MIN(oi.item_id), SUM(oi.quantity), o.total_cent, o.status, o.order_type,
       (SELECT p.client_request_id FROM payment_record p WHERE p.order_id=o.id ORDER BY p.id DESC LIMIT 1),
       (SELECT c.code FROM coupon c WHERE c.order_id=o.id ORDER BY c.id DESC LIMIT 1),
       o.address_id, o.address_snapshot, o.is_reviewed, o.version, o.created_at
FROM order_main o JOIN order_item oi ON oi.order_id=o.id
GROUP BY o.id, o.user_id, o.merchant_id, o.merchant_name_snapshot, o.total_cent, o.status, o.order_type, o.address_id, o.address_snapshot, o.is_reviewed, o.version, o.created_at
ON DUPLICATE KEY UPDATE merchant_name_snapshot=VALUES(merchant_name_snapshot), status=VALUES(status), total_cent=VALUES(total_cent), order_type=VALUES(order_type), client_request_id=VALUES(client_request_id), coupon_code=VALUES(coupon_code), address_id=VALUES(address_id), address_snapshot=VALUES(address_snapshot), reviewed=VALUES(reviewed), version=VALUES(version);

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
