#!/usr/bin/env bash
set -Eeuo pipefail

# Smoke tests render the same canonical service Deployments used by the
# production-like deployment. The temporary Kustomize overlay only disables
# external dependencies and scales unselected services to zero.
# Refuse non-Kind contexts so smoke tests cannot touch a non-disposable cluster.
readonly CURRENT_CONTEXT="$(kubectl config current-context)"
if [[ ! "${CURRENT_CONTEXT}" =~ ^kind- ]]; then
  echo "Refusing to apply smoke manifests to non-Kind context: ${CURRENT_CONTEXT}" >&2
  echo "Production deployment is managed by scripts/deploy-k8s.sh." >&2
  exit 2
fi

readonly NAMESPACE="lumalife"
readonly IMAGE_TAG="${1:?usage: smoke-services-k8s.sh <image-tag> <service> [service...]}"
shift
if [[ "$#" -eq 0 ]]; then
  echo "At least one service is required." >&2
  exit 2
fi

readonly IMAGE_REGISTRY="${IMAGE_REGISTRY:-ghcr.io/daihao007}"
readonly ROLLOUT_TIMEOUT="${ROLLOUT_TIMEOUT:-300s}"
readonly HEALTHCHECK_IMAGE="${HEALTHCHECK_IMAGE:-curlimages/curl:8.12.1}"
readonly SERVICES=("$@")
readonly ALL_SERVICES=(identity-service merchant-service order-service assistant-service)

if [[ ! "${IMAGE_TAG}" =~ ^[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}$ ]]; then
  echo "Invalid image tag: ${IMAGE_TAG}" >&2
  exit 2
fi

if [[ ! "${IMAGE_REGISTRY}" =~ ^[a-zA-Z0-9][a-zA-Z0-9./:_-]*$ ]]; then
  echo "Invalid image registry: ${IMAGE_REGISTRY}" >&2
  exit 2
fi

render_root="$(mktemp -d)"
cleanup() {
  rm -rf -- "${render_root}"
}
trap cleanup EXIT

service_port() {
  case "$1" in
    identity-service) printf '8081' ;;
    merchant-service) printf '8082' ;;
    order-service) printf '8083' ;;
    assistant-service) printf '8084' ;;
    *) echo "Unknown service: $1" >&2; return 2 ;;
  esac
}

diagnostics() {
  echo "::group::Kubernetes microservice smoke-test diagnostics"
  for service in "${SERVICES[@]}"; do
    kubectl -n "${NAMESPACE}" describe deployment "${service}" || true
    kubectl -n "${NAMESPACE}" get pods -l "app.kubernetes.io/name=${service}" -o wide || true
    kubectl -n "${NAMESPACE}" describe pods -l "app.kubernetes.io/name=${service}" || true
    kubectl -n "${NAMESPACE}" logs deployment/"${service}" --all-containers --tail=200 || true
    kubectl -n "${NAMESPACE}" logs deployment/"${service}" --all-containers --previous --tail=200 || true
  done
  echo "::endgroup::"
}
trap diagnostics ERR

kubectl apply -f k8s/namespace.yaml
kubectl -n "${NAMESPACE}" create secret generic lumalife-mysql \
  --from-literal=identity-database=life_assistant_identity \
  --from-literal=merchant-database=life_assistant_merchant \
  --from-literal=order-database=life_assistant_order \
  --from-literal=username=smoke \
  --from-literal=password=smoke \
  --dry-run=client -o yaml | kubectl apply -f -

for service in "${SERVICES[@]}"; do
  port="$(service_port "${service}")"
  image="${IMAGE_REGISTRY}/lumalife-${service}:${IMAGE_TAG}"
  render_dir="${render_root}/${service}"
  mkdir -p "${render_dir}"
  cp k8s/services.yaml "${render_dir}/services.yaml"
  cat > "${render_dir}/kustomization.yaml" <<EOF
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - services.yaml
images:
  - name: ghcr.io/daihao007/lumalife-${service}
    newName: ${IMAGE_REGISTRY}/lumalife-${service}
    newTag: ${IMAGE_TAG}
patches:
EOF
  for candidate in "${ALL_SERVICES[@]}"; do
    replica=0
    if [[ "${candidate}" == "${service}" ]]; then
      replica=1
    fi
    cat >> "${render_dir}/kustomization.yaml" <<EOF
  - target:
      group: apps
      version: v1
      kind: Deployment
      name: ${candidate}
    patch: |-
      - op: replace
        path: /spec/replicas
        value: ${replica}
EOF
  done
  cat >> "${render_dir}/kustomization.yaml" <<EOF
  - target:
      group: apps
      version: v1
      kind: Deployment
      name: ${service}
    patch: |-
      apiVersion: apps/v1
      kind: Deployment
      metadata:
        name: ${service}
      spec:
        template:
          spec:
            containers:
              - name: ${service}
                env:
                  - name: SPRING_PROFILES_ACTIVE
                    value: default
                  - name: LUMALIFE_EVENTS_BROKER_ENABLED
                    value: "false"
EOF

  rendered_manifest="$(kubectl kustomize "${render_dir}")"
  if ! grep -Fq "image: ${image}" <<<"${rendered_manifest}"; then
    echo "Rendered image mismatch for ${service}; expected ${image}." >&2
    exit 1
  fi

  kubectl apply -k "${render_dir}"
  kubectl -n "${NAMESPACE}" rollout status "deployment/${service}" --timeout="${ROLLOUT_TIMEOUT}"

  healthcheck_name="health-${service}-${GITHUB_RUN_ID:-local}-${GITHUB_RUN_ATTEMPT:-1}"
  healthcheck_name="${healthcheck_name:0:63}"
  kubectl -n "${NAMESPACE}" delete pod "${healthcheck_name}" --ignore-not-found
  kubectl -n "${NAMESPACE}" run "${healthcheck_name}" \
    --rm --attach --restart=Never \
    --image="${HEALTHCHECK_IMAGE}" \
    --command -- sh -ec \
    "curl --fail --silent --show-error --retry 12 --retry-delay 2 http://${service}:${port}/actuator/health/readiness | grep -q '\"status\":\"UP\"'"

  kubectl -n "${NAMESPACE}" get deployment,service "${service}" \
    -o custom-columns='KIND:.kind,NAME:.metadata.name,VERSION:.metadata.labels.app\.kubernetes\.io/version,IMAGE:.spec.template.spec.containers[*].image'
done

echo "Kind smoke test passed for: ${SERVICES[*]} (${IMAGE_TAG})."
