#!/usr/bin/env bash

# Unified Base Images Build Script
# Supports both local development and CI environments
# Compatible with Bash 3.2+ (macOS, Ubuntu, RHEL)
# Usage: ./build-base-images.sh [options]
#
# Options:
#   --registry REGISTRY     Registry URL (default: localhost:5555 for local, ghcr.io for CI)
#   --environment ENV       Environment: local|ci (default: local)
#   --platforms PLATFORMS   Build platforms (default: linux/amd64,linux/arm64)
#   --summary-file FILE     Write build summary to file (for CI)
#   --help                  Show this help

set -e

# Check if we're running with Bash
if [[ -z "${BASH_VERSION}" ]]; then
  echo "Error: This script requires Bash. Please run with: bash $0 $*"
  exit 1
fi

# Default configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../../../" && pwd)"
GRADLE_PROPERTIES="${ROOT_DIR}/src/gradle.properties"

# Default values
ENVIRONMENT="local"
REGISTRY=""
PLATFORMS="linux/amd64,linux/arm64"
SUMMARY_FILE=""
QUIET="false"
BUILD_START_TIME=$(date +%s)

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
  if [[ "$QUIET" != "true" ]]; then
    echo -e "${BLUE}[INFO]${NC} $1"
  fi
}

log_warn() {
  echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
  echo -e "${RED}[ERROR]${NC} $1"
}

log_success() {
  echo -e "${GREEN}[SUCCESS]${NC} $1"
}

# Set environment-specific defaults early (before argument parsing)
if [[ "$ENVIRONMENT" == "ci" ]]; then
  [[ -z "$REGISTRY" ]] && REGISTRY="ghcr.io"
else
  [[ -z "$REGISTRY" ]] && REGISTRY="localhost:5555"
fi

# Help function
show_help() {
  cat <<EOF
Unified Base Images Build Script

USAGE:
    ./build-base-images.sh [options]

OPTIONS:
    --registry REGISTRY     Registry URL (default: localhost:5555 for local, ghcr.io for CI)
    --environment ENV       Environment: local|ci (default: local)
    --platforms PLATFORMS   Build platforms (default: linux/amd64,linux/arm64)
    --summary-file FILE     Write build summary to file (for CI)
    --quiet                 Suppress info messages
    --help                  Show this help

EXAMPLES:
    # Local development build
    ./build-base-images.sh
    
    # Local build with custom registry
    ./build-base-images.sh --registry my-registry.local:5000
    
    # CI build
    ./build-base-images.sh --environment ci --registry ghcr.io/org/repo --summary-file summary.md

VERSIONING:
    The script reads xroadBaseImageTag from src/gradle.properties.
    The gradle.properties file is required and the script will fail if not found.
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
  --platforms)
    PLATFORMS="$2"
    shift 2
    ;;
  --summary-file)
    SUMMARY_FILE="$2"
    shift 2
    ;;
  --quiet)
    QUIET="true"
    shift
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

# Function to read gradle properties
read_gradle_property() {
  local property_name="$1"
  local property_file="$2"

  if [[ ! -f "$property_file" ]]; then
    log_error "Gradle properties file not found: $property_file"
    exit 1
  fi

  # Read property value, handling comments and empty lines
  grep "^${property_name}=" "$property_file" | cut -d'=' -f2- | tr -d ' \t'
}

# Function to format duration
format_duration() {
  local duration=$1
  local minutes=$((duration / 60))
  local seconds=$((duration % 60))
  printf "%dm %ds" $minutes $seconds
}

# Read version from gradle.properties (required)
if [[ ! -f "$GRADLE_PROPERTIES" ]]; then
  log_error "gradle.properties not found at: $GRADLE_PROPERTIES"
  log_error "This file is required to determine the base image tag."
  exit 1
fi

log_info "Reading version from gradle.properties..."
VERSION=$(read_gradle_property "xroadBaseImageTag" "$GRADLE_PROPERTIES")

if [[ -z "$VERSION" ]]; then
  log_error "xroadBaseImageTag not found in gradle.properties"
  exit 1
fi

# Determine image prefix based on registry and environment
if [[ "$ENVIRONMENT" == "ci" && "$REGISTRY" == "ghcr.io" ]]; then
  if [[ -n "$GITHUB_REPOSITORY" ]]; then
    # Convert repository name to lowercase for Docker registry compatibility
    REPO_LOWERCASE=$(echo "$GITHUB_REPOSITORY" | tr '[:upper:]' '[:lower:]')
    IMAGE_PREFIX="${REGISTRY}/${REPO_LOWERCASE}/base-images"
  else
    log_error "GITHUB_REPOSITORY environment variable not set for CI environment"
    exit 1
  fi
else
  IMAGE_PREFIX="$REGISTRY/base-images"
fi

# Define images to build: "image-name:dockerfile" pairs
# Each entry format: <image-name>:<dockerfile>
IMAGES=(
  "ss-baseline-runtime:Dockerfile-baseline"
  "ss-baseline-backup-manager-runtime:Dockerfile-backup-manager-baseline"
  "ss-baseline-signer-runtime:Dockerfile-signer-baseline"
)

# Build summary data
BUILD_TIMES=""
BUILD_SUMMARY=""

log_info "=== X-Road Base Images Build ==="
log_info "Environment: $ENVIRONMENT"
log_info "Registry: $REGISTRY"
log_info "Image Prefix: $IMAGE_PREFIX"
log_info "Version Tag: $VERSION"
log_info "Platforms: $PLATFORMS"
log_info "Working Directory: $SCRIPT_DIR"
echo

# Prepare build context
log_info "Preparing build context..."
rm -rf build/
mkdir -p build
cp "$ROOT_DIR/src/LICENSE.txt" build/
cp "$ROOT_DIR/src/3RD-PARTY-NOTICES.txt" build/

# Set up Docker Buildx
log_info "Setting up Docker Buildx..."
if ! docker buildx inspect xroad-builder &>/dev/null; then
  docker buildx create --name xroad-builder --driver docker-container --driver-opt network=host --bootstrap --use
else
  docker buildx use xroad-builder
fi

# Helper functions for managing build data
add_build_time() {
  local image_name="$1"
  local build_time="$2"
  BUILD_TIMES="${BUILD_TIMES}${image_name}:${build_time} "
}

get_build_time() {
  local image_name="$1"
  echo "$BUILD_TIMES" | tr ' ' '\n' | grep "^${image_name}:" | cut -d':' -f2
}

# Build images
log_info "Building base images..."
echo

for image_entry in "${IMAGES[@]}"; do
  image_name=$(echo "$image_entry" | cut -d':' -f1)
  dockerfile=$(echo "$image_entry" | cut -d':' -f2)
  full_image_name="${IMAGE_PREFIX}/${image_name}"

  log_info "Building $image_name..."
  build_start=$(date +%s)

  build_cmd=(
    docker buildx build
    --platform "$PLATFORMS"
    --file "$dockerfile"
  )

  # Add build args for images that need them
  if [[ "$image_name" == "ss-baseline-backup-manager-runtime" || "$image_name" == "ss-baseline-signer-runtime" ]]; then
    build_cmd+=(--build-arg "REGISTRY_URL=$IMAGE_PREFIX")
    # Pass the version tag so dependent images use the exact version just built
    build_cmd+=(--build-arg "BASE_IMAGE_TAG=$VERSION")
  fi

  # Add build context for signer
  if [[ "$image_name" == "ss-baseline-signer-runtime" ]]; then
    build_cmd+=(--build-context "pkcs11driver=${ROOT_DIR}/src/libs/pkcs11wrapper")
  fi

  build_cmd+=(
    --tag "${full_image_name}:${VERSION}"
    --push
    .
  )

  # Execute build
  if "${build_cmd[@]}"; then
    build_end=$(date +%s)
    build_duration=$((build_end - build_start))
    add_build_time "$image_name" "$build_duration"

    log_success "Built $image_name in $(format_duration $build_duration)"
  else
    log_error "Failed to build $image_name"
    exit 1
  fi
done

# Calculate total build time
BUILD_END_TIME=$(date +%s)
TOTAL_BUILD_TIME=$((BUILD_END_TIME - BUILD_START_TIME))

# Generate build summary
generate_build_summary() {
  local summary=""

  summary+="# Base Images Build Summary\n\n"
  summary+="**Environment:** \`$ENVIRONMENT\`\n"
  summary+="**Version Tag:** \`$VERSION\`\n"
  summary+="**Registry:** \`$IMAGE_PREFIX\`\n"
  summary+="**Platforms:** \`$PLATFORMS\`\n"
  summary+="**Total Build Time:** $(format_duration $TOTAL_BUILD_TIME)\n\n"

  summary+="## Images Built\n\n"
  summary+="| Image | Version Tag | Build Time |\n"
  summary+="|-------|-------------|------------|\n"

  for image_entry in "${IMAGES[@]}"; do
    local image_name=$(echo "$image_entry" | cut -d':' -f1)
    local build_time_seconds=$(get_build_time "$image_name")
    local build_time=$(format_duration $build_time_seconds)

    summary+="| \`$image_name\` | \`${VERSION}\` | $build_time |\n"
  done

  echo -e "$summary"
}

BUILD_SUMMARY=$(generate_build_summary)

# Output summary
log_success "=== Build Complete ==="
log_success "Total time: $(format_duration $TOTAL_BUILD_TIME)"
log_success "Images built: ${#IMAGES[@]}"

if [[ "$QUIET" != "true" ]]; then
  echo
  echo "=== Build Summary ==="
  if [[ -n "$SUMMARY_FILE" ]]; then
    echo "$BUILD_SUMMARY" >"$SUMMARY_FILE"
    log_info "Build summary written to: $SUMMARY_FILE"
    echo
  else
    echo -e "$BUILD_SUMMARY"
  fi
fi

# Final status
log_success "✅ All base images built successfully!"
