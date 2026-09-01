#!/bin/sh
set -eu

: "${MYSQL_HOST:?MYSQL_HOST is required}"
: "${MYSQL_DATABASE:?MYSQL_DATABASE is required}"
: "${MYSQL_USER:?MYSQL_USER is required}"
: "${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}"

MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_IDENTITY_DATABASE="${MYSQL_IDENTITY_DATABASE:-${MYSQL_DATABASE}_identity}"
MYSQL_MERCHANT_DATABASE="${MYSQL_MERCHANT_DATABASE:-${MYSQL_DATABASE}_merchant}"
MYSQL_ORDER_DATABASE="${MYSQL_ORDER_DATABASE:-${MYSQL_DATABASE}_order}"

validate_database_identifier() {
  value=$1
  label=$2
  case "$value" in
    ''|*[!A-Za-z0-9_]*)
      echo "${label} may contain only letters, digits and underscores." >&2
      exit 2
      ;;
  esac
}

validate_database_identifier "$MYSQL_DATABASE" MYSQL_DATABASE
validate_database_identifier "$MYSQL_IDENTITY_DATABASE" MYSQL_IDENTITY_DATABASE
validate_database_identifier "$MYSQL_MERCHANT_DATABASE" MYSQL_MERCHANT_DATABASE
validate_database_identifier "$MYSQL_ORDER_DATABASE" MYSQL_ORDER_DATABASE

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
