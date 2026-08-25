#!/usr/bin/env bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../../" && pwd)"
source "${ROOT_DIR}/scripts/lib/base-script.sh"

SRC_DIR="${ROOT_DIR}/src"
GRADLE_PROPERTIES="${SRC_DIR}/gradle.properties"
HSM_CONTEXT="${ROOT_DIR}/development/docker/security-server/signer-with-hsm"

show_help() {
  cat <<EOF
Signer-with-HSM Image Build Script

TEMPORARY stop-gap image: layers SoftHSM2 + a PKCS#11 signer-devices.yaml
onto the already-built ss-signer image, so a Security Server release can
expose a pkcs11 HARDWARE token (see security-server chart's
signer.hsm.enabled toggle) instead of a plain softToken. Reused as-is from
the compose e2e-test HSM variant (src/security-server/e2e-test/.../
compose.ss-hsm.e2e.yaml) until SoftHSM provisioning is built natively into
the chart.

Requires ss-signer:<tag> to already exist in the target registry — run
build-security-server.sh (or build-images.sh) first.

USAGE:
    ./build-signer-with-hsm.sh [options]

OPTIONS:
    --push                  Push image to registry
                            Default: true in CI, false locally
    --no-cache              Disable Docker build cache
    --platforms <list>      Target platforms for buildx (e.g. linux/amd64,linux/arm64)
                            Default: host platform
    --help                  Show this help

ENVIRONMENT VARIABLES:
    IMAGE_REGISTRY          Docker registry URL (default: localhost:5555)
    IMAGE_TAG               Image tag to use (default: xroadVersion-xroadBuildType)

EXAMPLES:
    # Build for local dev, load into Docker
    ./build-signer-with-hsm.sh

    # Build and push (CI)
    IMAGE_REGISTRY=ghcr.io/niis/x-road IMAGE_TAG=7.8.0-123 \\
      ./build-signer-with-hsm.sh --push

EOF
  exit 0
}

PUSH=""
NO_CACHE="false"
PLATFORMS=""

while [[ $# -gt 0 ]]; do
  case $1 in
  --help) show_help ;;
  --push) PUSH="true"; shift ;;
  --no-cache) NO_CACHE="true"; shift ;;
  --platforms) PLATFORMS="$2"; shift 2 ;;
  *) log_error "Unknown option: $1"; show_help ;;
  esac
done

REGISTRY="${IMAGE_REGISTRY:-localhost:5555}"

if [[ -z "$PUSH" ]]; then
  if [[ "$REGISTRY" == "localhost:"* ]]; then
    PUSH="false"
  else
    PUSH="true"
  fi
fi

if [[ ! -f "$GRADLE_PROPERTIES" ]]; then
  log_error "gradle.properties not found at: $GRADLE_PROPERTIES"
  exit 1
fi

XROAD_VERSION=$(read_gradle_property "xroadVersion" "$GRADLE_PROPERTIES")
XROAD_BUILD_TYPE=$(read_gradle_property "xroadBuildType" "$GRADLE_PROPERTIES")

if [[ -z "$XROAD_VERSION" || -z "$XROAD_BUILD_TYPE" ]]; then
  log_error "xroadVersion/xroadBuildType not found in gradle.properties"
  exit 1
fi

if [[ -z "$IMAGE_TAG" ]]; then
  if [[ "$XROAD_BUILD_TYPE" == "RELEASE" ]]; then
    IMAGE_TAG="${XROAD_VERSION}"
  else
    IMAGE_TAG="${XROAD_VERSION}-${XROAD_BUILD_TYPE}"
  fi
fi

CACHE_FLAG=()
[[ "$NO_CACHE" == "true" ]] && CACHE_FLAG=(--no-cache)

BASE_SIGNER_IMAGE="${REGISTRY}/ss-signer:${IMAGE_TAG}"
HSM_IMAGE="${REGISTRY}/ss-signer-with-hsm:${IMAGE_TAG}"

log_info "=== Signer-with-HSM Image Build ==="
log_info "Registry: $REGISTRY"
log_info "Image Tag: $IMAGE_TAG"
log_info "Base signer image: $BASE_SIGNER_IMAGE"
log_info "Push: $PUSH"
echo

build_cmd=(
  docker buildx build --progress=plain
  "${CACHE_FLAG[@]}"
  --file "${HSM_CONTEXT}/Dockerfile"
  --build-arg "BASE_SIGNER_IMAGE=${BASE_SIGNER_IMAGE}"
  --tag "$HSM_IMAGE"
)

# The floating latest tag is a local-dev convention (kind inventories default
# to :latest). Never move it on a shared registry, where concurrent CI builds
# from different branches would clobber each other's latest.
if [[ "$REGISTRY" == "localhost:"* ]]; then
  build_cmd+=(--tag "${REGISTRY}/ss-signer-with-hsm:latest")
fi

if [[ -n "$PLATFORMS" ]]; then
  build_cmd+=(--platform "$PLATFORMS")
fi

if [[ "$PUSH" == "true" ]]; then
  build_cmd+=(--push)
else
  build_cmd+=(--load)
fi

build_cmd+=("$HSM_CONTEXT")

log_info "Command: ${build_cmd[*]}"
echo "--------------------------------------------------------------------------------"

if "${build_cmd[@]}"; then
  log_success "Built ss-signer-with-hsm"
  if [[ "$PUSH" == "true" ]]; then
    log_success "Pushed ${HSM_IMAGE} and ${REGISTRY}/ss-signer-with-hsm:latest"
  else
    log_success "Loaded into local Docker"
  fi
else
  log_error "Failed to build ss-signer-with-hsm"
  log_error "Does ${BASE_SIGNER_IMAGE} exist in the registry? Run build-security-server.sh first."
  exit 1
fi
