#!/usr/bin/env bash
set -Eeuo pipefail

readonly ALL_SERVICES=(identity-service merchant-service order-service assistant-service)

changed_paths() {
  if [[ "${1:-}" == "--stdin" ]]; then
    cat
    return
  fi

  local base_sha="${1:-}"
  local head_sha="${2:-HEAD}"
  if [[ -z "${base_sha}" || "${base_sha}" =~ ^0+$ ]]; then
    git ls-tree -r --name-only "${head_sha}"
  else
    git diff --name-only "${base_sha}" "${head_sha}"
  fi
}

select_all=false
identity_selected=false
merchant_selected=false
order_selected=false
assistant_selected=false

select_service() {
  case "$1" in
    identity-service) identity_selected=true ;;
    merchant-service) merchant_selected=true ;;
    order-service) order_selected=true ;;
    assistant-service) assistant_selected=true ;;
    *) echo "Unknown service: $1" >&2; return 2 ;;
  esac
}

while IFS= read -r path; do
  case "${path}" in
    services/pom.xml|k8s/services.yaml|k8s/kustomization.yaml|k8s/healthcheck/*|.github/workflows/services-cd.yml|scripts/detect-changed-services.sh|scripts/smoke-services-k8s.sh)
      select_all=true
      ;;
    services/identity-service/*)
      select_service identity-service
      ;;
    services/merchant-service/*)
      select_service merchant-service
      ;;
    services/order-service/*)
      select_service order-service
      ;;
    services/assistant-service/*)
      select_service assistant-service
      ;;
  esac
done < <(changed_paths "$@")

if [[ "${select_all}" == "true" ]]; then
  identity_selected=true
  merchant_selected=true
  order_selected=true
  assistant_selected=true
fi

json="["
separator=""
if [[ "${identity_selected}" == "true" ]]; then
  json+="${separator}\"identity-service\""
  separator=","
fi
if [[ "${merchant_selected}" == "true" ]]; then
  json+="${separator}\"merchant-service\""
  separator=","
fi
if [[ "${order_selected}" == "true" ]]; then
  json+="${separator}\"order-service\""
  separator=","
fi
if [[ "${assistant_selected}" == "true" ]]; then
  json+="${separator}\"assistant-service\""
fi
json+="]"

printf '%s\n' "${json}"
