CREATE TABLE IF NOT EXISTS service_cart_item (
  user_id BIGINT NOT NULL, product_id BIGINT NOT NULL, quantity INT NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, product_id)
);
CREATE TABLE IF NOT EXISTS service_payment (
  user_id BIGINT NOT NULL, order_id BIGINT NOT NULL, client_request_id VARCHAR(128) NOT NULL,
  amount_cent BIGINT NOT NULL, status VARCHAR(16) NOT NULL, paid_at TIMESTAMP NULL,
  PRIMARY KEY (user_id, order_id, client_request_id)
);
CREATE TABLE IF NOT EXISTS service_coupon (
  code VARCHAR(32) PRIMARY KEY, order_id BIGINT NOT NULL, merchant_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'UNUSED', redeemed_at TIMESTAMP NULL
);
CREATE TABLE IF NOT EXISTS service_review (
  order_id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, merchant_id BIGINT NOT NULL,
  score TINYINT NOT NULL, taste_score TINYINT NOT NULL, service_score TINYINT NOT NULL,
  content VARCHAR(1000) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
