-- Durable order-domain state used by the order service during the final cutover.
-- This migration is intentionally additive so V004/V005 data remains readable.
ALTER TABLE order_record
  ADD COLUMN order_type VARCHAR(16) NOT NULL DEFAULT 'DELIVERY',
  ADD COLUMN client_request_id VARCHAR(128) NULL,
  ADD COLUMN coupon_code VARCHAR(32) NULL,
  ADD COLUMN address_id BIGINT NULL,
  ADD COLUMN reviewed BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX uq_order_client_request ON order_record(user_id, client_request_id);
CREATE INDEX idx_order_merchant_status ON order_record(merchant_id, status, id);

CREATE TABLE IF NOT EXISTS service_order_event (
  order_id BIGINT NOT NULL,
  version BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  actor_id BIGINT NOT NULL,
  occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (order_id, version)
);
