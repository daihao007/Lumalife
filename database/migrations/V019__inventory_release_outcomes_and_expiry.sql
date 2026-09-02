-- Make inventory release outcomes explicit and add durable expiry reconciliation
-- bookkeeping. A deduplication key keeps a replay from creating a second
-- logically-identical outbox event with a new broker event id.

ALTER TABLE merchant_outbox_event
  ADD COLUMN deduplication_key VARCHAR(255) NULL,
  ADD UNIQUE KEY uk_merchant_outbox_deduplication (deduplication_key);

ALTER TABLE service_outbox_event
  ADD COLUMN deduplication_key VARCHAR(255) NULL,
  ADD UNIQUE KEY uk_service_outbox_deduplication (deduplication_key);

ALTER TABLE inventory_reservation
  ADD COLUMN expiry_attempts INT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN expiry_last_attempt_at DATETIME(3) NULL,
  ADD COLUMN expiry_last_error VARCHAR(1000) NULL;

ALTER TABLE order_inventory_saga
  DROP CHECK ck_order_inventory_saga_status,
  ADD CONSTRAINT ck_order_inventory_saga_status CHECK (status IN (
    'RESERVE_PENDING', 'RESERVED', 'RESERVE_FAILED',
    'CONFIRM_PENDING', 'CONFIRMED', 'CONFIRM_FAILED',
    'RELEASE_PENDING', 'RELEASED', 'CHECK_REQUIRED', 'RELEASE_FAILED', 'FAILED'
  ));
