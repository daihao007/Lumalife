#!/bin/sh
set -eu

. /database/bin/common.sh
wait_for_mysql

mysql_exec <<'SQL'
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
  applied_checksum=$(mysql_exec --batch --skip-column-names --execute="SELECT checksum FROM schema_migration WHERE version = '${version}'")

  if [ -n "$applied_checksum" ]; then
    if [ "$applied_checksum" != "$checksum" ]; then
      echo "Checksum mismatch for applied migration ${filename}" >&2
      exit 1
    fi
    echo "Already applied: ${filename}"
    continue
  fi

  echo "Applying: ${filename}"
  mysql_exec < "$migration"
  mysql_exec --execute="INSERT INTO schema_migration(version, description, checksum) VALUES ('${version}', '${description}', '${checksum}')"
done
