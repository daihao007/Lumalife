-- Merchant-owned stock reservations.  Order-service may request a reservation,
-- but only merchant-service changes catalog stock and reservation state.
ALTER TABLE merchant_catalog
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS inventory_reservation (
  order_id BIGINT UNSIGNED NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'RESERVED',
  expires_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (order_id),
  UNIQUE KEY uk_inventory_reservation_idempotency (idempotency_key),
  KEY idx_inventory_reservation_expiry (status, expires_at),
  CONSTRAINT ck_inventory_reservation_status CHECK (status IN ('RESERVED', 'CONFIRMED', 'RELEASED', 'CHECK_REQUIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS inventory_reservation_item (
  order_id BIGINT UNSIGNED NOT NULL,
  item_type VARCHAR(16) NOT NULL,
  item_id BIGINT UNSIGNED NOT NULL,
  quantity INT UNSIGNED NOT NULL,
  expected_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (order_id, item_type, item_id),
  CONSTRAINT ck_inventory_reservation_item_type CHECK (item_type IN ('PRODUCT', 'GROUP_DEAL')),
  CONSTRAINT ck_inventory_reservation_item_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
