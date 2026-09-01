-- Keep the merchant display name with the order so historical order details
-- remain readable even when merchant-service is unavailable or the merchant
-- later changes its profile.
ALTER TABLE order_record
  ADD COLUMN merchant_name_snapshot VARCHAR(160) NOT NULL DEFAULT '';
