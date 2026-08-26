-- LumaLife MySQL 8.4 baseline schema.
-- Versioned files are immutable after they have been applied.

CREATE TABLE category (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  icon VARCHAR(64) NOT NULL DEFAULT '',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_category_name (name),
  CONSTRAINT ck_category_deleted CHECK (is_deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE merchant (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  category_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(128) NOT NULL,
  cover_url VARCHAR(1024) NOT NULL DEFAULT '',
  avg_score DECIMAL(3,2) NOT NULL DEFAULT 0.00,
  avg_price_cent BIGINT UNSIGNED NOT NULL DEFAULT 0,
  monthly_sales INT UNSIGNED NOT NULL DEFAULT 0,
  distance_km DECIMAL(8,2) NOT NULL DEFAULT 0.00,
  status VARCHAR(32) NOT NULL,
  address VARCHAR(255) NOT NULL,
  recommend_reason VARCHAR(255) NOT NULL DEFAULT '',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_merchant_category_status (category_id, status, is_deleted),
  KEY idx_merchant_score (avg_score DESC, monthly_sales DESC),
  CONSTRAINT fk_merchant_category FOREIGN KEY (category_id) REFERENCES category (id),
  CONSTRAINT ck_merchant_score CHECK (avg_score BETWEEN 0 AND 5),
  CONSTRAINT ck_merchant_deleted CHECK (is_deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_account (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  phone VARCHAR(32) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  nickname VARCHAR(64) NOT NULL,
  avatar_url VARCHAR(1024) NOT NULL DEFAULT '',
  role VARCHAR(32) NOT NULL,
  merchant_id BIGINT UNSIGNED NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_account_phone (phone),
  KEY idx_user_account_merchant (merchant_id),
  CONSTRAINT fk_user_account_merchant FOREIGN KEY (merchant_id) REFERENCES merchant (id),
  CONSTRAINT ck_user_account_role CHECK (role IN ('USER', 'MERCHANT_ADMIN', 'PLATFORM_ADMIN')),
  CONSTRAINT ck_user_account_deleted CHECK (is_deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_address (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  contact_name VARCHAR(64) NOT NULL,
  phone VARCHAR(32) NOT NULL,
  detail VARCHAR(255) NOT NULL,
  is_default TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user_address_user (user_id, is_deleted),
  CONSTRAINT fk_user_address_user FOREIGN KEY (user_id) REFERENCES user_account (id),
  CONSTRAINT ck_user_address_default CHECK (is_default IN (0, 1)),
  CONSTRAINT ck_user_address_deleted CHECK (is_deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auth_session (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  token_hash CHAR(64) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  revoked_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_auth_session_token_hash (token_hash),
  KEY idx_auth_session_user_expiry (user_id, expires_at),
  CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  merchant_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(128) NOT NULL,
  description VARCHAR(512) NOT NULL DEFAULT '',
  price_cent BIGINT UNSIGNED NOT NULL,
  stock INT UNSIGNED NOT NULL DEFAULT 0,
  is_listed TINYINT(1) NOT NULL DEFAULT 1,
  version BIGINT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_product_merchant_listed (merchant_id, is_listed, is_deleted),
  CONSTRAINT fk_product_merchant FOREIGN KEY (merchant_id) REFERENCES merchant (id),
  CONSTRAINT ck_product_listed CHECK (is_listed IN (0, 1)),
  CONSTRAINT ck_product_deleted CHECK (is_deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE group_deal (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  merchant_id BIGINT UNSIGNED NOT NULL,
  title VARCHAR(128) NOT NULL,
  description VARCHAR(512) NOT NULL DEFAULT '',
  price_cent BIGINT UNSIGNED NOT NULL,
  stock INT UNSIGNED NOT NULL DEFAULT 0,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  version BIGINT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_group_deal_merchant_active (merchant_id, is_active, is_deleted),
  CONSTRAINT fk_group_deal_merchant FOREIGN KEY (merchant_id) REFERENCES merchant (id),
  CONSTRAINT ck_group_deal_active CHECK (is_active IN (0, 1)),
  CONSTRAINT ck_group_deal_deleted CHECK (is_deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cart_item (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  quantity INT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_cart_item_user_product (user_id, product_id),
  KEY idx_cart_item_product (product_id),
  CONSTRAINT fk_cart_item_user FOREIGN KEY (user_id) REFERENCES user_account (id),
  CONSTRAINT fk_cart_item_product FOREIGN KEY (product_id) REFERENCES product (id),
  CONSTRAINT ck_cart_item_quantity CHECK (quantity BETWEEN 1 AND 99)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE order_main (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  merchant_id BIGINT UNSIGNED NOT NULL,
  merchant_name_snapshot VARCHAR(128) NOT NULL,
  order_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  total_cent BIGINT UNSIGNED NOT NULL,
  address_id BIGINT UNSIGNED NULL,
  address_snapshot VARCHAR(512) NULL,
  is_reviewed TINYINT(1) NOT NULL DEFAULT 0,
  is_stock_deducted TINYINT(1) NOT NULL DEFAULT 0,
  version BIGINT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_order_user_created (user_id, created_at DESC),
  KEY idx_order_merchant_status (merchant_id, status, created_at DESC),
  KEY idx_order_address (address_id),
  CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES user_account (id),
  CONSTRAINT fk_order_merchant FOREIGN KEY (merchant_id) REFERENCES merchant (id),
  CONSTRAINT fk_order_address FOREIGN KEY (address_id) REFERENCES user_address (id) ON DELETE SET NULL,
  CONSTRAINT ck_order_type CHECK (order_type IN ('DELIVERY', 'GROUP_BUY')),
  CONSTRAINT ck_order_status CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'ACCEPTED', 'DELIVERING', 'RECEIVED', 'COMPLETED', 'USED', 'EXPIRED', 'CANCELLED')),
  CONSTRAINT ck_order_reviewed CHECK (is_reviewed IN (0, 1)),
  CONSTRAINT ck_order_stock CHECK (is_stock_deducted IN (0, 1)),
  CONSTRAINT ck_order_deleted CHECK (is_deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE order_item (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id BIGINT UNSIGNED NOT NULL,
  item_type VARCHAR(32) NOT NULL,
  item_id BIGINT UNSIGNED NULL,
  item_name_snapshot VARCHAR(128) NOT NULL,
  quantity INT UNSIGNED NOT NULL,
  unit_price_cent BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_order_item_order (order_id),
  CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES order_main (id),
  CONSTRAINT ck_order_item_type CHECK (item_type IN ('PRODUCT', 'GROUP_DEAL')),
  CONSTRAINT ck_order_item_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE order_status_timeline (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id BIGINT UNSIGNED NOT NULL,
  status VARCHAR(32) NOT NULL,
  occurred_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_timeline_status (order_id, status),
  CONSTRAINT fk_order_timeline_order FOREIGN KEY (order_id) REFERENCES order_main (id),
  CONSTRAINT ck_order_timeline_status CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'ACCEPTED', 'DELIVERING', 'RECEIVED', 'COMPLETED', 'USED', 'EXPIRED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payment_record (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  order_id BIGINT UNSIGNED NOT NULL,
  client_request_id VARCHAR(128) NOT NULL,
  amount_cent BIGINT UNSIGNED NOT NULL,
  status VARCHAR(32) NOT NULL,
  failure_reason VARCHAR(255) NULL,
  paid_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_payment_request (user_id, order_id, client_request_id),
  KEY idx_payment_order (order_id),
  CONSTRAINT fk_payment_user FOREIGN KEY (user_id) REFERENCES user_account (id),
  CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES order_main (id),
  CONSTRAINT ck_payment_status CHECK (status IN ('SUCCESS', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE coupon (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id BIGINT UNSIGNED NOT NULL,
  merchant_id BIGINT UNSIGNED NOT NULL,
  code VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'UNUSED',
  redeemed_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_coupon_order (order_id),
  UNIQUE KEY uk_coupon_code (code),
  KEY idx_coupon_merchant_status (merchant_id, status),
  CONSTRAINT fk_coupon_order FOREIGN KEY (order_id) REFERENCES order_main (id),
  CONSTRAINT fk_coupon_merchant FOREIGN KEY (merchant_id) REFERENCES merchant (id),
  CONSTRAINT ck_coupon_status CHECK (status IN ('UNUSED', 'USED', 'EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE review (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  merchant_id BIGINT UNSIGNED NOT NULL,
  user_name_snapshot VARCHAR(64) NOT NULL,
  score TINYINT UNSIGNED NOT NULL,
  taste_score TINYINT UNSIGNED NOT NULL,
  service_score TINYINT UNSIGNED NOT NULL,
  content VARCHAR(1000) NOT NULL DEFAULT '',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_review_order (order_id),
  KEY idx_review_merchant_created (merchant_id, created_at DESC),
  KEY idx_review_user (user_id),
  CONSTRAINT fk_review_order FOREIGN KEY (order_id) REFERENCES order_main (id),
  CONSTRAINT fk_review_user FOREIGN KEY (user_id) REFERENCES user_account (id),
  CONSTRAINT fk_review_merchant FOREIGN KEY (merchant_id) REFERENCES merchant (id),
  CONSTRAINT ck_review_score CHECK (score BETWEEN 1 AND 5 AND taste_score BETWEEN 1 AND 5 AND service_score BETWEEN 1 AND 5),
  CONSTRAINT ck_review_deleted CHECK (is_deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE merchant_favorite (
  user_id BIGINT UNSIGNED NOT NULL,
  merchant_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (user_id, merchant_id),
  KEY idx_favorite_merchant (merchant_id),
  CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES user_account (id),
  CONSTRAINT fk_favorite_merchant FOREIGN KEY (merchant_id) REFERENCES merchant (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE chat_message (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  merchant_id BIGINT UNSIGNED NOT NULL,
  sender_role VARCHAR(32) NOT NULL,
  sender_name VARCHAR(64) NOT NULL,
  content TEXT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_chat_conversation (user_id, merchant_id, created_at),
  CONSTRAINT fk_chat_user FOREIGN KEY (user_id) REFERENCES user_account (id),
  CONSTRAINT fk_chat_merchant FOREIGN KEY (merchant_id) REFERENCES merchant (id),
  CONSTRAINT ck_chat_sender_role CHECK (sender_role IN ('USER', 'MERCHANT_ADMIN', 'PLATFORM_ADMIN', 'ASSISTANT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE operation_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  actor VARCHAR(128) NOT NULL,
  action VARCHAR(255) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_operation_log_created (created_at DESC),
  KEY idx_operation_log_actor (actor, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
