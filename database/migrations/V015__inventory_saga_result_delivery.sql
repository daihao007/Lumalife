-- Close the inventory Saga loop. Commands are produced by order-service and
-- consumed by merchant-service; the result is produced from a merchant-owned
-- outbox and consumed by an order-owned Inbox. Both sides are at-least-once.

CREATE TABLE IF NOT EXISTS merchant_outbox_event (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id BIGINT UNSIGNED NOT NULL,
  event_type VARCHAR(128) NOT NULL,
  payload JSON NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  published_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  KEY idx_merchant_outbox_status (status, id),
  CONSTRAINT ck_merchant_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS order_inbox_event (
  event_id VARCHAR(128) NOT NULL,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id BIGINT UNSIGNED NOT NULL,
  event_type VARCHAR(128) NOT NULL,
  payload JSON NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'RECEIVED',
  received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  processed_at DATETIME(3) NULL,
  last_error VARCHAR(1000) NULL,
  PRIMARY KEY (event_id),
  KEY idx_order_inbox_status (status, received_at),
  CONSTRAINT ck_order_inbox_status CHECK (status IN ('RECEIVED', 'PROCESSED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS order_inventory_saga (
  order_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  client_request_id VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  last_error VARCHAR(1000) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (order_id),
  UNIQUE KEY uk_order_inventory_saga_request (user_id, client_request_id),
  CONSTRAINT ck_order_inventory_saga_status CHECK (status IN (
    'RESERVED', 'CONFIRM_PENDING', 'CONFIRMED', 'RELEASE_PENDING', 'RELEASED', 'FAILED'
  ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
