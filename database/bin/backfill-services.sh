#!/usr/bin/env sh
set -eu
. "$(dirname "$0")/common.sh"

echo "Backfilling service-owned catalog and order tables (idempotent)"
mysql_exec < /database/backfill-services.sql
echo "Backfill completed"
