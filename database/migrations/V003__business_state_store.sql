-- MySQL-backed source of truth for the current monolith business state.
-- The JSON document is versioned by the application and replaces the local
-- state file in production while the normalized tables remain the migration
-- target for the three business services.

CREATE TABLE IF NOT EXISTS business_state (
  state_key VARCHAR(64) NOT NULL,
  payload JSON NOT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (state_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
