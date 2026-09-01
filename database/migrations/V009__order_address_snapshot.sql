-- Preserve the immutable delivery address supplied by the identity boundary.
-- The order service validates ownership before storing this snapshot.
ALTER TABLE order_record
  ADD COLUMN address_snapshot VARCHAR(512) NULL;
