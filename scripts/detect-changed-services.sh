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

declare -A selected=()
select_all=false

while IFS= read -r path; do
  case "${path}" in
    services/pom.xml|k8s/services/kustomization.yaml|k8s/healthcheck/*|.github/workflows/services-cd.yml|scripts/detect-changed-services.sh|scripts/smoke-services-k8s.sh)
      select_all=true
      ;;
    services/identity-service/*|k8s/services/identity-service.yaml)
      selected[identity-service]=1
      ;;
    services/merchant-service/*|k8s/services/merchant-service.yaml)
      selected[merchant-service]=1
      ;;
    services/order-service/*|k8s/services/order-service.yaml)
      selected[order-service]=1
      ;;
    services/assistant-service/*|k8s/services/assistant-service.yaml)
      selected[assistant-service]=1
      ;;
  esac
done < <(changed_paths "$@")

if [[ "${select_all}" == "true" ]]; then
  for service in "${ALL_SERVICES[@]}"; do
    selected["${service}"]=1
  done
fi

json="["
separator=""
for service in "${ALL_SERVICES[@]}"; do
  if [[ -n "${selected[${service}]:-}" ]]; then
    json+="${separator}\"${service}\""
    separator=","
  fi
done
json+="]"

printf '%s\n' "${json}"
