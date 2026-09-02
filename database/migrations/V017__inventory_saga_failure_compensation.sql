-- Make every asynchronous inventory outcome observable on the order side.
-- A confirm failure is first recorded as CONFIRM_FAILED, then the order is
-- cancelled/payment-failed and a release command moves the Saga to
-- RELEASE_PENDING. A later release result completes it as RELEASED.

ALTER TABLE order_inventory_saga
  DROP CHECK ck_order_inventory_saga_status,
  ADD CONSTRAINT ck_order_inventory_saga_status CHECK (status IN (
    'RESERVE_PENDING', 'RESERVED', 'RESERVE_FAILED',
    'CONFIRM_PENDING', 'CONFIRMED', 'CONFIRM_FAILED',
    'RELEASE_PENDING', 'RELEASED', 'FAILED'
  ));
