-- Broker integration state. Each consumer owns its Inbox and each producer
-- owns its own outbox; delivery is at-least-once and handlers must be idempotent.
CREATE TABLE IF NOT EXISTS merchant_inbox_event (
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
  KEY idx_merchant_inbox_status (status, received_at),
  CONSTRAINT ck_merchant_inbox_status CHECK (status IN ('RECEIVED', 'PROCESSED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
