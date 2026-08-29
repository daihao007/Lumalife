-- Service-owned tables.  The services may dual-read their in-memory seed during
-- rollout, but all new catalog/order writes have a durable relational home.
CREATE TABLE IF NOT EXISTS merchant_catalog (
  id BIGINT PRIMARY KEY,
  merchant_id BIGINT NOT NULL,
  name VARCHAR(160) NOT NULL,
  description VARCHAR(500) NOT NULL,
  price_cent BIGINT NOT NULL,
  stock INT NOT NULL,
  listed BOOLEAN NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS order_record (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  total_cent BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
