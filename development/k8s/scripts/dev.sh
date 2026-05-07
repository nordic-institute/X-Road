#!/bin/bash
# shellcheck source=./_common.sh
set -e

source "${BASH_SOURCE%/*}/_common.sh"

# service-config.csv row → container image tag. Identity for every service
# except proxy-ui-api (image is ss-proxy-ui-api, source row is admin-service).
SUPPORTED_SERVICES=(
  proxy
  signer
  proxy-ui-api
  configuration-client
  monitor
  op-monitor
  auxiliary-service
  softtoken-signer
  ds-control-plane
  ds-data-plane
  ds-identity-hub
  ds-issuer-service
)

usage() {
  cat <<EOF
Usage: $(basename "$0") -m <service> [-b] [-d] [-e <env>] [-n <namespace>] [-h]

Rebuild and redeploy a single X-Road service in the k8s dev cluster.
Mirrors core/development/native-lxd-stack/dev.sh for the k8s workflow.

Options:
  -m <service>   Service name (required). One of:
                   ${SUPPORTED_SERVICES[*]}
  -b             Build image via build-images.sh + push to localhost:5555
  -d             Deploy: kind load docker-image + kubectl rollout restart
  -e <env>       Target env (default: dev). Only dev is kind-backed.
  -n <namespace> Security server namespace (default: ss)
  -h             Show help

Examples:
  $(basename "$0") -bdm proxy          # rebuild + redeploy proxy
  $(basename "$0") -dm proxy-ui-api    # redeploy latest image only
  $(basename "$0") -bm signer          # rebuild only, deploy later
EOF
}

BUILD=false
DEPLOY=false
SERVICE=""
ENV_NAME="dev"
NAMESPACE="ss"

while getopts ":m:bde:n:h" opt; do
  case "${opt}" in
    m) SERVICE="${OPTARG}" ;;
    b) BUILD=true ;;
    d) DEPLOY=true ;;
    e) ENV_NAME="${OPTARG}" ;;
    n) NAMESPACE="${OPTARG}" ;;
    h) usage; exit 0 ;;
    \?) log_error "Unknown flag: -${OPTARG}"; usage; exit 1 ;;
    :)  log_error "Flag -${OPTARG} requires an argument"; usage; exit 1 ;;
  esac
done
shift $((OPTIND - 1))

if [[ -z "${SERVICE}" ]]; then
  log_error "Service name is required (-m)"
  usage
  exit 1
fi

if [[ "${BUILD}" != true && "${DEPLOY}" != true ]]; then
  log_error "Pass at least one of -b (build) or -d (deploy)"
  usage
  exit 1
fi

validate_service() {
  local s
  for s in "${SUPPORTED_SERVICES[@]}"; do
    [[ "${s}" == "${SERVICE}" ]] && return 0
  done
  log_error "Unknown service: ${SERVICE}"
  log_info  "Allowed: ${SUPPORTED_SERVICES[*]}"
  exit 1
}

service_to_image() {
  case "${SERVICE}" in
    proxy-ui-api) echo "ss-proxy-ui-api" ;;
    ds-*)         echo "${SERVICE}" ;;
    *)            echo "ss-${SERVICE}" ;;
  esac
}

# service-config.csv uses `admin-service` as the build target whose output is
# tagged `ss-proxy-ui-api`. Every other service name matches its csv row.
service_to_build_target() {
  case "${SERVICE}" in
    proxy-ui-api) echo "admin-service" ;;
    *)            echo "${SERVICE}" ;;
  esac
}

# Returns the gradle_path column from service-config.csv for the given build target.
gradle_path_for_target() {
  local target="$1" csv line svc_name gradle_path
  csv="${CORE_ROOT}/deployment/security-server/images/service-config.csv"
  while IFS=',' read -r svc_name _ gradle_path _; do
    [[ "${svc_name}" == "${target}" ]] && { echo "${gradle_path}"; return 0; }
  done < <(tail -n +2 "${csv}")
  return 1
}

handleBuild() {
  [[ "${BUILD}" != true ]] && return
  local target gradle_path gradle_module
  target="$(service_to_build_target)"

  gradle_path="$(gradle_path_for_target "${target}")"
  if [[ -n "${gradle_path}" && "${gradle_path}" != "-" ]]; then
    gradle_module=":${gradle_path//\//:}"
    log_info "Gradle build: ${gradle_module}"
    (
      cd "${CORE_ROOT}/src"
      ./gradlew "${gradle_module}:build"
    )
  fi

  log_info "Building image '${SERVICE}' (build target: ${target})"
  require_bin docker "brew install --cask docker"
  (
    cd "${CORE_ROOT}/deployment/security-server/images"
    IMAGE_REGISTRY="localhost:5555" ./build-images.sh "${target}" --push
  )
  log_success "Image built: localhost:5555/$(service_to_image):latest"
}

handleDeploy() {
  [[ "${DEPLOY}" != true ]] && return
  require_bin kubectl "brew install kubectl"
  require_bin kind    "brew install kind"

  local image cluster_name context
  image="localhost:5555/$(service_to_image):latest"

  # kubectl current-context gives `kind-<clustername>`; strip the prefix.
  context="$(kubectl config current-context)"
  if [[ "${context}" != kind-* ]]; then
    log_error "Current kubectl context is '${context}', not a kind cluster."
    log_info  "Switch: kubectl config use-context kind-xroad-${ENV_NAME}-cluster"
    exit 1
  fi
  cluster_name="${context#kind-}"

  log_kv "  Env"          "${ENV_NAME}"     2 5
  log_kv "  Namespace"    "${NAMESPACE}"    2 5
  log_kv "  Service"      "${SERVICE}"      2 5
  log_kv "  Image"        "${image}"        2 5
  log_kv "  Kind cluster" "${cluster_name}" 2 5

  log_info "Loading image into kind nodes (bypasses containerd pull cache)"
  kind load docker-image "${image}" --name "${cluster_name}"

  log_info "Rolling deployment/${SERVICE} in namespace ${NAMESPACE}"
  kubectl -n "${NAMESPACE}" rollout restart "deployment/${SERVICE}"
  kubectl -n "${NAMESPACE}" rollout status  "deployment/${SERVICE}" --timeout=5m

  log_success "Redeployed ${SERVICE}"
}

main() {
  local start end
  start=$(date +%s)

  validate_service
  handleBuild
  handleDeploy

  end=$(date +%s)
  log_success "dev.sh: ${SERVICE} complete in $(format_duration $((end - start)))"
}

main "$@"
