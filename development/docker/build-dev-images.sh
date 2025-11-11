#!/usr/bin/env bash

set -e

# Source base script for common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../../" && pwd)"
source "${ROOT_DIR}/.scripts/base-script.sh"

# Paths
SRC_DIR="${ROOT_DIR}/src"
GRADLE_PROPERTIES="${SRC_DIR}/gradle.properties"
SECRET_STORE_LOCAL="${ROOT_DIR}/deployment/native-packages/src/xroad/common/secret-store-local"
CA_CONTAINER="${SRC_DIR}/common/common-int-test/src/main/resources/META-INF/ca-container"

# Show help
show_help() {
  cat <<EOF
Development Images Build Script

Builds OpenBao and TestCA images for local development and integration testing.

USAGE:
    ./build-dev-images.sh [options]

OPTIONS:
    --push                  Push images to registry (default: false)
    --help                  Show this help

ENVIRONMENT VARIABLES:
    IMAGE_REGISTRY          Docker registry URL (default: localhost:5555)
    IMAGE_TAG               Image tag to use (default: xroadVersion-xroadBuildType)

IMAGES BUILT:
    - openbao-dev:<tag>     OpenBao secret store for development
    - testca-dev:<tag>      Test CA with ACME/OCSP/TSA support

EXAMPLES:
    # Build for local development
    ./build-dev-images.sh

    # Build with custom tag
    IMAGE_TAG=7.8.0-TEST ./build-dev-images.sh

    # Build and push to registry
    IMAGE_REGISTRY=ghcr.io/niis/x-road IMAGE_TAG=7.8.0 ./build-dev-images.sh --push

EOF
  exit 0
}

# Parse arguments
PUSH="true"

while [[ $# -gt 0 ]]; do
  case $1 in
  --help)
    show_help
    ;;
  --push)
    PUSH="true"
    shift
    ;;
  -*)
    log_error "Unknown option: $1"
    show_help
    ;;
  *)
    log_error "Unexpected argument: $1"
    show_help
    ;;
  esac
done

# Determine registry
REGISTRY="${IMAGE_REGISTRY:-localhost:5555}"

# Read version and build type from gradle.properties
if [[ ! -f "$GRADLE_PROPERTIES" ]]; then
  log_error "gradle.properties not found at: $GRADLE_PROPERTIES"
  exit 1
fi

XROAD_VERSION=$(read_gradle_property "xroadVersion" "$GRADLE_PROPERTIES")
XROAD_BUILD_TYPE=$(read_gradle_property "xroadBuildType" "$GRADLE_PROPERTIES")

if [[ -z "$XROAD_VERSION" ]]; then
  log_error "xroadVersion not found in gradle.properties"
  exit 1
fi

if [[ -z "$XROAD_BUILD_TYPE" ]]; then
  log_error "xroadBuildType not found in gradle.properties"
  exit 1
fi

# Construct image tags from version and build type (can be overridden via environment)
# For RELEASE builds, use version as-is (e.g., 8.0.0 or 8.0.0-beta1)
# For non-RELEASE builds, always append build type (e.g., 8.0.0-SNAPSHOT or 8.0.0-beta1-SNAPSHOT)
if [[ -z "$IMAGE_TAG" ]]; then
  if [[ "$XROAD_BUILD_TYPE" == "RELEASE" ]]; then
    IMAGE_TAG="${XROAD_VERSION}"
  else
    IMAGE_TAG="${XROAD_VERSION}-${XROAD_BUILD_TYPE}"
  fi
fi

log_info "=== X-Road Development Images Build ==="
log_info "Registry: $REGISTRY"
log_info "Image Tag: $IMAGE_TAG"
log_info "Platform: host platform only"
log_info "Push: $PUSH"
echo

# Set up Docker Buildx (use default driver for local builds)
log_info "Setting up Docker Buildx..."
docker buildx use default 2>/dev/null || docker buildx use orbstack || true

# Validate required paths exist
if [[ ! -d "$SECRET_STORE_LOCAL" ]]; then
  log_error "Secret store local directory not found: $SECRET_STORE_LOCAL"
  exit 1
fi

if [[ ! -d "$CA_CONTAINER" ]]; then
  log_error "CA container directory not found: $CA_CONTAINER"
  exit 1
fi

BUILD_START_TIME=$(date +%s)

# =============================================================================
# Build OpenBao Development Image
# =============================================================================
log_info "Building openbao-dev..."
build_start=$(date +%s)

OPENBAO_IMAGE="${REGISTRY}/openbao-dev:${IMAGE_TAG}"
OPENBAO_DOCKERFILE="${SCRIPT_DIR}/security-server/openbao/Dockerfile"
OPENBAO_CONTEXT="${SCRIPT_DIR}/security-server/openbao"

build_cmd=(
  docker buildx build
  --file "$OPENBAO_DOCKERFILE"
  --build-context "openbao-init-ctx=${SECRET_STORE_LOCAL}"
  --tag "$OPENBAO_IMAGE"
)

if [[ "$PUSH" == "true" ]]; then
  build_cmd+=(--push)
else
  build_cmd+=(--load)
fi

build_cmd+=("$OPENBAO_CONTEXT")

if "${build_cmd[@]}"; then
  build_end=$(date +%s)
  build_duration=$((build_end - build_start))
  log_success "Built openbao-dev in $(format_duration $build_duration)"
else
  log_error "Failed to build openbao-dev"
  exit 1
fi

# =============================================================================
# Build TestCA Development Image
# =============================================================================
log_info "Building testca-dev..."
build_start=$(date +%s)

TESTCA_IMAGE="${REGISTRY}/testca-dev:${IMAGE_TAG}"
TESTCA_DOCKERFILE="${CA_CONTAINER}/Dockerfile"
TESTCA_CONTEXT="$CA_CONTAINER"

build_cmd=(
  docker buildx build
  --file "$TESTCA_DOCKERFILE"
  --tag "$TESTCA_IMAGE"
)

if [[ "$PUSH" == "true" ]]; then
  build_cmd+=(--push)
else
  build_cmd+=(--load)
fi

build_cmd+=("$TESTCA_CONTEXT")

if "${build_cmd[@]}"; then
  build_end=$(date +%s)
  build_duration=$((build_end - build_start))
  log_success "Built testca-dev in $(format_duration $build_duration)"
else
  log_error "Failed to build testca-dev"
  exit 1
fi

# =============================================================================
# Summary
# =============================================================================
BUILD_END_TIME=$(date +%s)
TOTAL_DURATION=$((BUILD_END_TIME - BUILD_START_TIME))

echo
log_success "=== Build Complete ==="
log_info "Total time: $(format_duration $TOTAL_DURATION)"
log_info "Images built:"
echo "  - $OPENBAO_IMAGE"
echo "  - $TESTCA_IMAGE"
echo

