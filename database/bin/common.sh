#!/bin/sh
set -eu

: "${MYSQL_HOST:?MYSQL_HOST is required}"
: "${MYSQL_DATABASE:?MYSQL_DATABASE is required}"
: "${MYSQL_USER:?MYSQL_USER is required}"
: "${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}"

MYSQL_PORT="${MYSQL_PORT:-3306}"

mysql_exec() {
  MYSQL_PWD="$MYSQL_PASSWORD" mysql \
    --protocol=TCP \
    --host="$MYSQL_HOST" \
    --port="$MYSQL_PORT" \
    --user="$MYSQL_USER" \
    --database="$MYSQL_DATABASE" \
    --default-character-set=utf8mb4 \
    "$@"
}

wait_for_mysql() {
  attempts=0
  until mysql_exec --batch --skip-column-names --execute='SELECT 1' >/dev/null 2>&1; do
    attempts=$((attempts + 1))
    if [ "$attempts" -ge 60 ]; then
      echo "MySQL did not become ready at ${MYSQL_HOST}:${MYSQL_PORT}" >&2
      return 1
    fi
    sleep 2
  done
}
