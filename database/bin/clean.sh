#!/bin/sh
set -eu

. /database/bin/common.sh
wait_for_mysql

if [ "${ALLOW_DATABASE_CLEAN:-}" != "true" ]; then
  echo "Refusing to delete business data. Set ALLOW_DATABASE_CLEAN=true explicitly." >&2
  exit 1
fi

mysql_exec < /database/cleanup/clean-data.sql
echo "Business data removed; migration history retained."
