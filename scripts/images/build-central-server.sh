#!/usr/bin/env bash

# Central Server Development Image Build Script
# Supports both local development and CI environments
# Usage: ./build-central-server.sh [options]
#
# Options:
#   --registry REGISTRY     Registry URL (default: localhost:5555 for local, ghcr.io for CI)
#   --environment ENV       Environment: local|ci (default: local)
#   --version VERSION       Image version tag (default: read from gradle.properties)
#   --packages-path PATH    Path to Ubuntu 26.04 packages (default: auto-detect)
#   --platforms PLATFORMS   Build platforms (default: linux/amd64,linux/arm64)
#   --help                  Show this help

set -e

# Source base script for common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../../" && pwd)"
CS_DEV_DIR="${ROOT_DIR}/development/docker/central-server"
source "${ROOT_DIR}/scripts/lib/base-script.sh"

# Default configuration
GRADLE_PROPERTIES="${XROAD_HOME}/src/gradle.properties"

# Default values
ENVIRONMENT="local"
REGISTRY=""
VERSION=""
PACKAGES_PATH=""
PLATFORMS=""  # Empty by default = build for host platform only
BUILD_START_TIME=$(date +%s)
# Set default password for build secrets if not provided
export USER_PASSWD="${USER_PASSWD:-secret123!}"

# Help function
show_help() {
  cat <<EOF
Central Server Development Image Build Script

USAGE:
    ./build-central-server.sh [options]

OPTIONS:
    --registry REGISTRY     Registry URL (default: localhost:5555 for local, ghcr.io for CI)
    --environment ENV       Environment: local|ci (default: local)
    --version VERSION       Image version tag (default: read from gradle.properties)
    --packages-path PATH    Path to Ubuntu 26.04 packages (default: auto-detect)
    --platforms PLATFORMS   Build platforms (default: host platform, e.g. --platforms linux/amd64,linux/arm64)
    --help                  Show this help

ENVIRONMENT VARIABLES:
    XROAD_MIRROR_UBUNTU_URL Package mirror URL (optional)
    XROAD_MIRROR_USERNAME   Mirror username (optional)
    XROAD_MIRROR_TOKEN      Mirror token (optional)

EXAMPLES:
    # Local development build for host platform (after building packages)
    ./build-central-server.sh

    # Multi-platform build
    ./build-central-server.sh --platforms linux/amd64,linux/arm64

    # Local build with custom registry
    ./build-central-server.sh --registry my-registry.local:5000

    # CI build with specific version
    ./build-central-server.sh --environment ci --registry ghcr.io/org/repo --version 8.0.0-SNAPSHOT --platforms linux/amd64,linux/arm64

VERSIONING:
    For local builds, the script constructs version from xroadVersion-xroadBuildType in src/gradle.properties.
    For CI builds, pass --version with the SERVICE_IMAGE_TAG value from the CI workflow.

PACKAGE REQUIREMENTS:
    This script requires Ubuntu 26.04 packages to be built first.
    Default location: deployment/native-packages/build/ubuntu26.04/
EOF
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
  case $1 in
  --registry)
    REGISTRY="$2"
    shift 2
    ;;
  --environment)
    ENVIRONMENT="$2"
    shift 2
    ;;
  --version)
    VERSION="$2"
    shift 2
    ;;
  --packages-path)
    PACKAGES_PATH="$2"
    shift 2
    ;;
  --platforms)
    PLATFORMS="$2"
    shift 2
    ;;
  --help)
    show_help
    exit 0
    ;;
  *)
    log_error "Unknown option: $1"
    show_help
    exit 1
    ;;
  esac
done

# Set environment-specific defaults
if [[ "$ENVIRONMENT" == "ci" ]]; then
  [[ -z "$REGISTRY" ]] && REGISTRY="ghcr.io"
else
  [[ -z "$REGISTRY" ]] && REGISTRY="localhost:5555"
fi

# Auto-detect packages path if not provided
if [[ -z "$PACKAGES_PATH" ]]; then
  PACKAGES_PATH="${XROAD_HOME}/deployment/native-packages/build/ubuntu26.04"
fi

PERF_PATH="${XROAD_HOME}/development/docker/postgres-dev/files/"

# Validate packages directory
if [[ ! -d "$PACKAGES_PATH" ]] || [[ ! "$(ls -A "$PACKAGES_PATH")" ]]; then
  log_error "Cannot find packages in $PACKAGES_PATH"
  log_error "Please build Ubuntu 26.04 packages first using: ./scripts/packages/build-deb.sh resolute"
  exit 1
fi

# Determine version if not provided
if [[ -z "$VERSION" ]]; then
  if [[ ! -f "$GRADLE_PROPERTIES" ]]; then
    log_error "gradle.properties not found at: $GRADLE_PROPERTIES"
    log_error "This file is required to determine the version."
    exit 1
  fi

  log_info "Reading version from gradle.properties..."
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

  VERSION="${XROAD_VERSION}-${XROAD_BUILD_TYPE}"
  log_info "Using version from gradle.properties: ${VERSION}"
fi

# Determine image name - registry already includes the full path
IMAGE_NAME="${REGISTRY}/central-server-dev"

log_info "=== X-Road Central Server Dev Image Build ==="
log_info "Environment: $ENVIRONMENT"
log_info "Registry: $REGISTRY"
log_info "Image Name: $IMAGE_NAME"
log_info "Version Tag: $VERSION"
log_info "Platforms: ${PLATFORMS:-host platform}"
log_info "Packages Path: $PACKAGES_PATH"
log_info "Build Context: $CS_DEV_DIR"
echo

# Setup Docker Buildx for multi-platform builds
log_info "Setting up Docker Buildx..."
if ! docker buildx inspect xroad-builder &>/dev/null; then
  docker buildx create --name xroad-builder --driver docker-container --driver-opt network=host --bootstrap --use
else
  docker buildx use xroad-builder
fi

# Build the image
log_info "Building central-server-dev image..."
build_start=$(date +%s)

build_mirror_args "${XROAD_HOME}" "false"

build_cmd=(
  docker buildx build --progress=plain
  --file "$CS_DEV_DIR/Dockerfile"
  --build-arg PACKAGE_SOURCE=internal
  --build-context "perf=$PERF_PATH"
  --build-context "packages=$PACKAGES_PATH"
  --secret "id=user_passwd,env=USER_PASSWD"
  "${MIRROR_BUILD_ARGS[@]}"
)

# Add platform flag only if specified
if [[ -n "$PLATFORMS" ]]; then
  build_cmd+=(--platform "$PLATFORMS")
fi

build_cmd+=(
  --tag "${IMAGE_NAME}:${VERSION}"
)

# The floating latest tag is a local-dev convention (kind inventories default
# to :latest). Never move it on a shared registry, where concurrent CI builds
# from different branches would clobber each other's latest.
if [[ "$REGISTRY" == "localhost:"* ]]; then
  build_cmd+=(--tag "${IMAGE_NAME}:latest")
fi

build_cmd+=(
  --push
  "$CS_DEV_DIR"
)

# Execute build

if "${build_cmd[@]}"; then
  build_end=$(date +%s)
  build_duration=$((build_end - build_start))

  log_success "Built central-server-dev in $(format_duration $build_duration)"
else
  log_error "Failed to build central-server-dev image"
  exit 1
fi

# Calculate total build time
BUILD_END_TIME=$(date +%s)
TOTAL_BUILD_TIME=$((BUILD_END_TIME - BUILD_START_TIME))

# Output summary
log_success "=== Build Complete ==="
log_success "Total time: $(format_duration $TOTAL_BUILD_TIME)"
log_success "Image: ${IMAGE_NAME}:${VERSION}"
echo
log_success "Central Server dev image built successfully!"
