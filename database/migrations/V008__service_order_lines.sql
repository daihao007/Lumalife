-- Preserve every item in a delivery order instead of collapsing a multi-item
-- order into the first product stored on order_record.
CREATE TABLE IF NOT EXISTS service_order_line (
  order_id BIGINT NOT NULL,
  line_no INT NOT NULL,
  item_id BIGINT NOT NULL,
  item_name VARCHAR(160) NOT NULL,
  quantity INT NOT NULL,
  price_cent BIGINT NOT NULL,
  PRIMARY KEY (order_id, line_no),
  CONSTRAINT ck_service_order_line_quantity CHECK (quantity > 0),
  CONSTRAINT ck_service_order_line_price CHECK (price_cent > 0)
);
