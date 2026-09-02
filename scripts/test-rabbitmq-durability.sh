#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

grep -q 'rabbitmq_data:/var/lib/rabbitmq' "${ROOT_DIR}/docker-compose.yml"
grep -q 'kind: PersistentVolumeClaim' "${ROOT_DIR}/k8s/rabbitmq.yaml"
grep -q 'name: rabbitmq-data' "${ROOT_DIR}/k8s/rabbitmq.yaml"
grep -q 'mountPath: /var/lib/rabbitmq' "${ROOT_DIR}/k8s/rabbitmq.yaml"
grep -q 'setDeliveryMode' "${ROOT_DIR}/services/order-service/src/main/java/com/lumalife/order/RabbitOrderOutboxPublisher.java"
grep -q 'setDeliveryMode' "${ROOT_DIR}/services/merchant-service/src/main/java/com/lumalife/merchant/RabbitMerchantOutboxPublisher.java"

echo "RabbitMQ durability configuration checks passed."
