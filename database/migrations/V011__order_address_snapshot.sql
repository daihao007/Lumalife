-- Keep the immutable delivery destination alongside the address reference.
ALTER TABLE order_record
  ADD COLUMN address_snapshot VARCHAR(512) NULL;
