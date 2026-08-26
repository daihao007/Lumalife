#!/bin/sh
set -eu

. /database/bin/common.sh
wait_for_mysql

if [ "${ALLOW_DEMO_SEED:-}" != "true" ]; then
  echo "Refusing to load demo accounts. Set ALLOW_DEMO_SEED=true explicitly." >&2
  exit 1
fi

mysql_exec < /database/seeds/demo-data.sql
echo "Demo data loaded."
