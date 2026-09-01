-- Complete the canonical order projection used by order-service for payment
-- idempotency. This is additive and leaves legacy order_record untouched.
ALTER TABLE order_main
  ADD COLUMN client_request_id VARCHAR(128) NULL,
  ADD COLUMN coupon_code VARCHAR(32) NULL;

CREATE UNIQUE INDEX uq_order_main_client_request
  ON order_main(user_id, client_request_id);
