-- Keep the immutable delivery destination alongside the address reference.
-- V001 already contains this column in a fresh database. Older databases may
-- still need it, so check the catalog before issuing the ALTER statement.
SET @lumalife_add_address_snapshot = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE order_record ADD COLUMN address_snapshot VARCHAR(512) NULL',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'order_record'
    AND column_name = 'address_snapshot'
);
PREPARE lumalife_address_snapshot_stmt FROM @lumalife_add_address_snapshot;
EXECUTE lumalife_address_snapshot_stmt;
DEALLOCATE PREPARE lumalife_address_snapshot_stmt;
