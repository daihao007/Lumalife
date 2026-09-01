#!/usr/bin/env bash
set -Eeuo pipefail

# These manifests deliberately use ephemeral storage and smoke-test settings.
# Refuse non-Kind contexts so they cannot overwrite the production deployments
# managed by scripts/deploy-k8s.sh and k8s/services.yaml.
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
readonly SOURCE_SHA="${SOURCE_SHA:-${GITHUB_SHA:-unknown}}"
readonly SERVICES=("$@")

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

for service in "${SERVICES[@]}"; do
  port="$(service_port "${service}")"
  manifest="k8s/services/${service}.yaml"
  image="${IMAGE_REGISTRY}/lumalife-${service}:${IMAGE_TAG}"
  render_dir="${render_root}/${service}"
  mkdir -p "${render_dir}"
  cp "${manifest}" "${render_dir}/resources.yaml"
  cat > "${render_dir}/kustomization.yaml" <<EOF
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - resources.yaml
images:
  - name: ghcr.io/daihao007/lumalife-${service}
    newName: ${IMAGE_REGISTRY}/lumalife-${service}
    newTag: ${IMAGE_TAG}
patches:
  - target:
      kind: Service
      name: ${service}
    patch: |-
      - op: replace
        path: /metadata/labels/app.kubernetes.io~1version
        value: ${IMAGE_TAG}
  - target:
      kind: Deployment
      name: ${service}
    patch: |-
      - op: replace
        path: /metadata/labels/app.kubernetes.io~1version
        value: ${IMAGE_TAG}
      - op: replace
        path: /spec/template/metadata/labels/app.kubernetes.io~1version
        value: ${IMAGE_TAG}
      - op: replace
        path: /spec/template/spec/containers/0/env/0/value
        value: ${IMAGE_TAG}
      - op: replace
        path: /spec/template/spec/containers/0/env/1/value
        value: ${SOURCE_SHA}
EOF

  rendered_image="$(kubectl kustomize "${render_dir}" | awk '/image:/{print $2; exit}')"
  if [[ "${rendered_image}" != "${image}" ]]; then
    echo "Rendered image mismatch for ${service}: ${rendered_image}" >&2
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
    --env="SERVICE=${service}" \
    --env="PORT=${port}" \
    --env="EXPECTED_VERSION=${IMAGE_TAG}" \
    --env="EXPECTED_SHA=${SOURCE_SHA}" \
    --command -- sh -ec '
      curl --fail --silent --show-error --retry 12 --retry-delay 2 "http://${SERVICE}:${PORT}/actuator/health/readiness" | grep -Fq "\"status\":\"UP\""
      curl --fail --silent --show-error --retry 12 --retry-delay 2 "http://${SERVICE}:${PORT}/actuator/info" | grep -Fq "\"version\":\"${EXPECTED_VERSION}\""
      curl --fail --silent --show-error --retry 12 --retry-delay 2 "http://${SERVICE}:${PORT}/actuator/info" | grep -Fq "\"commit\":\"${EXPECTED_SHA}\""
    '

  kubectl -n "${NAMESPACE}" get deployment,service "${service}" \
    -o custom-columns='KIND:.kind,NAME:.metadata.name,VERSION:.metadata.labels.app\.kubernetes\.io/version,IMAGE:.spec.template.spec.containers[*].image'
done

echo "Kind smoke test passed for: ${SERVICES[*]} (${IMAGE_TAG})."
