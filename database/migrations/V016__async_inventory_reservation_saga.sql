-- Add the order-side state used while the merchant consumes the asynchronous
-- inventory.reserve.requested command. Kept as a new migration so V015 remains
-- checksum-stable for databases that already received the Inbox/Saga tables.

ALTER TABLE order_inventory_saga
  DROP CHECK ck_order_inventory_saga_status,
  ADD CONSTRAINT ck_order_inventory_saga_status CHECK (status IN (
    'RESERVE_PENDING', 'RESERVED', 'CONFIRM_PENDING', 'CONFIRMED', 'RELEASE_PENDING', 'RELEASED', 'FAILED'
  ));
