#!/bin/sh

(
set -eu

schema_file=/database/migrations/V001__baseline_schema.sql

mysql_as_root() {
  MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql \
    --protocol=socket \
    --user=root \
    --database="$MYSQL_DATABASE" \
    --default-character-set=utf8mb4 \
    "$@"
}

mysql_as_root <<'SQL'
CREATE TABLE IF NOT EXISTS schema_migration (
  version VARCHAR(64) NOT NULL,
  description VARCHAR(255) NOT NULL,
  checksum CHAR(64) NOT NULL,
  installed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
SQL

mysql_as_root < "$schema_file"

schema_checksum=$(sha256sum "$schema_file" | awk '{print $1}')
mysql_as_root --execute="INSERT INTO schema_migration(version, description, checksum) VALUES ('V001', 'baseline_schema', '${schema_checksum}')"

echo "LumaLife schema initialized."
)
