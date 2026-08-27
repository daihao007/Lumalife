#!/bin/sh

(
set -eu

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

for migration in /database/migrations/V[0-9][0-9][0-9]__*.sql; do
  [ -f "$migration" ] || continue
  filename=$(basename "$migration")
  version=${filename%%__*}
  description=${filename#*__}
  description=${description%.sql}
  checksum=$(sha256sum "$migration" | awk '{print $1}')

  mysql_as_root < "$migration"
  mysql_as_root --execute="INSERT INTO schema_migration(version, description, checksum) VALUES ('${version}', '${description}', '${checksum}')"
  echo "Applied during initialization: ${filename}"
done

echo "LumaLife schema initialized with all versioned migrations."
)
