#!/usr/bin/env bash
set -Eeuo pipefail

readonly BASE_URL="${ORDER_SERVICE_URL:-http://localhost:8083}"
readonly SERVICE_TOKEN="${LUMALIFE_INTERNAL_SERVICE_TOKEN:-compose-internal-token}"
readonly USER_ID="${ORDER_CONTRACT_TEST_USER_ID:-92001}"
readonly ORDER_DATABASE="${MYSQL_ORDER_DATABASE:-${MYSQL_DATABASE:-life_assistant}_order}"
readonly REQUEST_ID="ct-mysql-${GITHUB_RUN_ID:-local}-$(date +%s%N)"

request_json() {
  curl --fail --silent --show-error \
    -H "X-Internal-Service-Token: ${SERVICE_TOKEN}" \
    -H "X-User-Id: ${USER_ID}" \
    -H 'Content-Type: application/json' \
    -d "$1" \
    "${BASE_URL}$2"
}

create_order() {
  request_json "{\"userId\":${USER_ID},\"merchantId\":1,\"productId\":1001,\"quantity\":1,\"totalCent\":2680}" '/internal/v1/orders'
}

first_order="$(create_order)"
first_id="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "${first_order}")"
request_json "{\"amountCent\":2680,\"clientRequestId\":\"${REQUEST_ID}\"}" "/internal/v1/orders/${first_id}/pay" > /dev/null
replay="$(request_json "{\"amountCent\":2680,\"clientRequestId\":\"${REQUEST_ID}\"}" "/internal/v1/orders/${first_id}/pay")"
python3 -c 'import json,sys; assert json.load(sys.stdin)["status"] == "PAID"' <<< "${replay}"

second_order="$(create_order)"
second_id="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<< "${second_order}")"
response_file="$(mktemp)"
trap 'rm -f "${response_file}"' EXIT
status="$(curl --silent --show-error --output "${response_file}" --write-out '%{http_code}' \
  -H "X-Internal-Service-Token: ${SERVICE_TOKEN}" \
  -H "X-User-Id: ${USER_ID}" \
  -H 'Content-Type: application/json' \
  -d "{\"amountCent\":2680,\"clientRequestId\":\"${REQUEST_ID}\"}" \
  "${BASE_URL}/internal/v1/orders/${second_id}/pay")"
test "${status}" = 409

payment_rows="$(docker compose exec -T mysql sh -c "MYSQL_PWD=\"\$MYSQL_PASSWORD\" mysql --protocol=TCP --host=127.0.0.1 --user=\"\$MYSQL_USER\" --database=\"${ORDER_DATABASE}\" --batch --skip-column-names --execute=\"SELECT COUNT(*) FROM service_payment WHERE user_id=${USER_ID} AND client_request_id='${REQUEST_ID}'\"" | tr -d '\r')"
test "${payment_rows}" -eq 1
second_status="$(docker compose exec -T mysql sh -c "MYSQL_PWD=\"\$MYSQL_PASSWORD\" mysql --protocol=TCP --host=127.0.0.1 --user=\"\$MYSQL_USER\" --database=\"${ORDER_DATABASE}\" --batch --skip-column-names --execute=\"SELECT status FROM order_record WHERE id=${second_id}\"" | tr -d '\r')"
test "${second_status}" = 'PENDING_PAYMENT'

echo "MySQL payment idempotency contract passed for request ${REQUEST_ID}."
