#!/bin/sh
set -eu

. /database/bin/common.sh
wait_for_mysql

assert_query() {
  label=$1
  expected=$2
  query=$3
  actual=$(mysql_exec --batch --skip-column-names --execute="$query")
  if [ "$actual" != "$expected" ]; then
    echo "Verification failed: ${label}; expected ${expected}, got ${actual}" >&2
    exit 1
  fi
  echo "Verified: ${label} = ${actual}"
}

assert_query 'versioned migrations' '9' "SELECT COUNT(*) FROM schema_migration WHERE version IN ('V001', 'V002', 'V003', 'V004', 'V005', 'V006', 'V007', 'V008', 'V009')"
assert_query 'domain tables' '28' "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('category','merchant','user_account','user_address','auth_session','product','group_deal','cart_item','order_main','order_item','order_status_timeline','payment_record','coupon','review','merchant_favorite','chat_message','operation_log','business_state','merchant_catalog','order_record','service_cart_item','service_payment','service_coupon','service_review','service_order_event','service_order_line','service_outbox_event','schema_migration')"
assert_query 'payment idempotency index' 'user_id,client_request_id' "SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_record' AND index_name = 'uk_payment_request'"
assert_query 'service payment idempotency index' 'user_id,client_request_id' "SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'service_payment' AND index_name = 'uk_service_payment_request'"
assert_query 'payment processing state' '1' "SELECT COUNT(*) FROM information_schema.check_constraints WHERE constraint_schema = DATABASE() AND constraint_name = 'ck_payment_status' AND check_clause LIKE '%PROCESSING%'"
assert_query 'demo users' '6' 'SELECT COUNT(*) FROM user_account'
assert_query 'demo merchants' '4' 'SELECT COUNT(*) FROM merchant'
assert_query 'demo products' '7' 'SELECT COUNT(*) FROM product'
assert_query 'fixed product ids' '7' 'SELECT COUNT(*) FROM product WHERE id BETWEEN 1001 AND 1007'
assert_query 'demo group deals' '3' 'SELECT COUNT(*) FROM group_deal'
assert_query 'BCrypt hashes only' '6' "SELECT COUNT(*) FROM user_account WHERE password_hash REGEXP '^[$]2[aby][$][0-9]{2}[$]'"
assert_query 'avatar storage capacity' 'MEDIUMTEXT' "SELECT DATA_TYPE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name='user_account' AND column_name='avatar_url'"

echo 'Database schema and demo data verification passed.'
