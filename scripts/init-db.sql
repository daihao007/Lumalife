-- Compatibility entry point for administrators creating the database manually.
-- MYSQL_DATABASE cannot be interpolated inside SQL; keep this database name aligned
-- with .env.example, then apply database/migrations through the migration runner.
CREATE DATABASE IF NOT EXISTS life_assistant
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;
