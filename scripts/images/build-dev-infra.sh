#!/usr/bin/env bash

set -e

# Source base script for common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../../" && pwd)"
source "${ROOT_DIR}/scripts/lib/base-script.sh"

# Paths
SRC_DIR="${ROOT_DIR}/src"
GRADLE_PROPERTIES="${SRC_DIR}/gradle.properties"
DEV_DOCKER_DIR="${ROOT_DIR}/development/docker"
SECRET_STORE_LOCAL="${ROOT_DIR}/deployment/native-packages/src/xroad/common/secret-store-local"
CA_CONTAINER="${ROOT_DIR}/development/docker/testca-dev"

# Show help
show_help() {
  cat <<EOF
Development Images Build Script

Builds OpenBao and TestCA images for local development and integration testing.

USAGE:
    ./build-dev-infra.sh [options]

OPTIONS:
    --push                  Push images to registry (default: true)
    --no-cache              Disable Docker build cache
    --no-mirror             Skip package mirror configuration
    --help                  Show this help

ENVIRONMENT VARIABLES:
    IMAGE_REGISTRY          Docker registry URL (default: localhost:5555)
    IMAGE_TAG               Image tag to use (default: xroadVersion-xroadBuildType)
    XROAD_MIRROR_UBUNTU_URL Package mirror URL (optional)
    XROAD_MIRROR_USERNAME   Mirror username (optional)
    XROAD_MIRROR_TOKEN      Mirror token (optional)

IMAGES BUILT:
    - openbao-dev:<tag>     OpenBao secret store for development
    - testca-dev:<tag>      Test CA with ACME/OCSP/TSA support
    - postgres-dev:<tag>    PostgreSQL 16 optimized for test workloads

EXAMPLES:
    # Build for local development
    ./build-dev-infra.sh

    # Build with custom tag
    IMAGE_TAG=7.8.0-TEST ./build-dev-infra.sh

    # Build and push to registry
    IMAGE_REGISTRY=ghcr.io/niis/x-road IMAGE_TAG=7.8.0 ./build-dev-infra.sh --push

EOF
  exit 0
}

# Parse arguments
PUSH="true"
NO_CACHE="false"
NO_MIRROR="false"

while [[ $# -gt 0 ]]; do
  case $1 in
  --help)
    show_help
    ;;
  --push)
    PUSH="true"
    shift
    ;;
  --no-cache)
    NO_CACHE="true"
    shift
    ;;
  --no-mirror)
    NO_MIRROR="true"
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

build_mirror_args "${ROOT_DIR}" "${NO_MIRROR}"

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

# Prepare cache flag
CACHE_FLAG=()
if [[ "$NO_CACHE" == "true" ]]; then
  CACHE_FLAG=(--no-cache)
fi

log_info "=== X-Road Development Images Build ==="
log_info "Registry: $REGISTRY"
log_info "Image Tag: $IMAGE_TAG"
log_info "Platform: host platform only"
log_info "Push: $PUSH"
log_info "Cache: $(if [[ "$NO_CACHE" == "true" ]]; then echo "disabled"; else echo "enabled"; fi)"
echo

# Setup Docker Buildx for multi-platform builds
log_info "Setting up Docker Buildx..."
if ! docker buildx inspect xroad-builder &>/dev/null; then
  docker buildx create --name xroad-builder --driver docker-container --driver-opt network=host --bootstrap --use
else
  docker buildx use xroad-builder
fi

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
echo
echo "================================================================================"
log_info ">>> STARTING BUILD: openbao-dev"
echo "================================================================================"
build_start=$(date +%s)

OPENBAO_IMAGE="${REGISTRY}/openbao-dev:${IMAGE_TAG}"
OPENBAO_DOCKERFILE="${DEV_DOCKER_DIR}/security-server/openbao/Dockerfile"
OPENBAO_CONTEXT="${DEV_DOCKER_DIR}/security-server/openbao"

build_cmd=(
  docker buildx build --progress=plain
  "${CACHE_FLAG[@]}"
  --file "$OPENBAO_DOCKERFILE"
  --build-context "openbao-init-ctx=${SECRET_STORE_LOCAL}"
  --tag "$OPENBAO_IMAGE"
  --tag "${OPENBAO_IMAGE%:*}:latest"
  "${MIRROR_BUILD_ARGS[@]}"
)

if [[ "$PUSH" == "true" ]]; then
  build_cmd+=(--push)
else
  build_cmd+=(--load)
fi

build_cmd+=("$OPENBAO_CONTEXT")

log_info "Dockerfile: $OPENBAO_DOCKERFILE"
log_info "Context: $OPENBAO_CONTEXT"
log_info "Command: ${build_cmd[*]}"
echo "--------------------------------------------------------------------------------"

if "${build_cmd[@]}"; then
  echo "--------------------------------------------------------------------------------"
  build_end=$(date +%s)
  build_duration=$((build_end - build_start))
  log_success "<<< FINISHED BUILD: openbao-dev in $(format_duration $build_duration)"
else
  log_error "<<< FAILED BUILD: openbao-dev"
  exit 1
fi

# =============================================================================
# Build TestCA Development Image
# =============================================================================
echo
echo "================================================================================"
log_info ">>> STARTING BUILD: testca-dev"
echo "================================================================================"
build_start=$(date +%s)

TESTCA_IMAGE="${REGISTRY}/testca-dev:${IMAGE_TAG}"
TESTCA_DOCKERFILE="${CA_CONTAINER}/Dockerfile"
TESTCA_CONTEXT="$CA_CONTAINER"

# Stage development/acme2certifier/ into ${CA_CONTAINER}/build/acme2certifier
(cd "$CA_CONTAINER" && XROAD_HOME="$ROOT_DIR" ./init_context.sh)

build_cmd=(
  docker buildx build --progress=plain
  "${CACHE_FLAG[@]}"
  --file "$TESTCA_DOCKERFILE"
  --tag "$TESTCA_IMAGE"
  --tag "${TESTCA_IMAGE%:*}:latest"
  "${MIRROR_BUILD_ARGS[@]}"
)

if [[ "$PUSH" == "true" ]]; then
  build_cmd+=(--push)
else
  build_cmd+=(--load)
fi

build_cmd+=("$TESTCA_CONTEXT")

log_info "Dockerfile: $TESTCA_DOCKERFILE"
log_info "Context: $TESTCA_CONTEXT"
log_info "Command: ${build_cmd[*]}"
echo "--------------------------------------------------------------------------------"

if "${build_cmd[@]}"; then
  echo "--------------------------------------------------------------------------------"
  build_end=$(date +%s)
  build_duration=$((build_end - build_start))
  log_success "<<< FINISHED BUILD: testca-dev in $(format_duration $build_duration)"
else
  log_error "<<< FAILED BUILD: testca-dev"
  exit 1
fi

# =============================================================================
# Build PostgreSQL Development Image
# =============================================================================
echo
echo "================================================================================"
log_info ">>> STARTING BUILD: postgres-dev"
echo "================================================================================"
build_start=$(date +%s)

POSTGRES_DEV_IMAGE="${REGISTRY}/postgres-dev:${IMAGE_TAG}"
POSTGRES_DEV_DOCKERFILE="${DEV_DOCKER_DIR}/postgres-dev/Dockerfile"
POSTGRES_DEV_CONTEXT="${DEV_DOCKER_DIR}/postgres-dev"

build_cmd=(
  docker buildx build --progress=plain
  "${CACHE_FLAG[@]}"
  --file "$POSTGRES_DEV_DOCKERFILE"
  --tag "$POSTGRES_DEV_IMAGE"
  --tag "${POSTGRES_DEV_IMAGE%:*}:latest"
  "${MIRROR_BUILD_ARGS[@]}"
)

if [[ "$PUSH" == "true" ]]; then
  build_cmd+=(--push)
else
  build_cmd+=(--load)
fi

build_cmd+=("$POSTGRES_DEV_CONTEXT")

log_info "Dockerfile: $POSTGRES_DEV_DOCKERFILE"
log_info "Context: $POSTGRES_DEV_CONTEXT"
log_info "Command: ${build_cmd[*]}"
echo "--------------------------------------------------------------------------------"

if "${build_cmd[@]}"; then
  echo "--------------------------------------------------------------------------------"
  build_end=$(date +%s)
  build_duration=$((build_end - build_start))
  log_success "<<< FINISHED BUILD: postgres-dev in $(format_duration $build_duration)"
else
  log_error "<<< FAILED BUILD: postgres-dev"
  exit 1
fi

# =============================================================================
# Build Nginx Configuration-proxy Image
# =============================================================================
echo
echo "================================================================================"
log_info ">>> STARTING BUILD: nginx-cp"
echo "================================================================================"
build_start=$(date +%s)

NGINX_CP_IMAGE="${REGISTRY}/nginx-cp:${IMAGE_TAG}"
NGINX_CP_DOCKERFILE="${DEV_DOCKER_DIR}/configuration-proxy/nginx/Dockerfile"
NGINX_CP_CONTEXT="${DEV_DOCKER_DIR}/configuration-proxy/nginx"

build_cmd=(
  docker buildx build --progress=plain
  "${CACHE_FLAG[@]}"
  --file "$NGINX_CP_DOCKERFILE"
  --tag "$NGINX_CP_IMAGE"
  --tag "${NGINX_CP_IMAGE%:*}:latest"
  "${MIRROR_BUILD_ARGS[@]}"
)

if [[ "$PUSH" == "true" ]]; then
  build_cmd+=(--push)
else
  build_cmd+=(--load)
fi

build_cmd+=("$NGINX_CP_CONTEXT")

log_info "Dockerfile: $NGINX_CP_DOCKERFILE"
log_info "Context: $NGINX_CP_CONTEXT"
log_info "Command: ${build_cmd[*]}"
echo "--------------------------------------------------------------------------------"

if "${build_cmd[@]}"; then
  echo "--------------------------------------------------------------------------------"
  build_end=$(date +%s)
  build_duration=$((build_end - build_start))
  log_success "<<< FINISHED BUILD: nginx-cp in $(format_duration $build_duration)"
else
  log_error "<<< FAILED BUILD: nginx-cp"
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
echo "  - $POSTGRES_DEV_IMAGE"
echo "  - $NGINX_CP_IMAGE"
echo
