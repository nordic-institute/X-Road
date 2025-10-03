#!/usr/bin/env bash

set -e

# Source base script for common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../../../" && pwd)"
source "${ROOT_DIR}/.scripts/base-script.sh"

# Additional paths
SRC_DIR="${ROOT_DIR}/src"
GRADLE_PROPERTIES="${SRC_DIR}/gradle.properties"
SERVICE_CONFIG_CSV="${SCRIPT_DIR}/service-config.csv"

# Show help
show_help() {
  cat <<EOF
Security Server Images Build Script

USAGE:
    ./build-images.sh [service...] [options]

SERVICES:
    <service_name>          Service name from service-config.csv
    (no arguments)          Build all services (default)

OPTIONS:
    --platforms PLATFORMS   Build platforms (e.g., linux/amd64,linux/arm64)
                            Default: host platform only
    --push                  Push images to registry
                            Default: true in CI, false locally
    --help                  Show this help

ENVIRONMENT VARIABLES:
    IMAGE_REGISTRY          Docker registry URL (default: localhost:5555)
    IMAGE_TAG               Image tag to use (default: xroadVersion-xroadBuildType)

EXAMPLES:
    # Build all services for host platform (local dev)
    ./build-images.sh all

    # Build specific services
    ./build-images.sh proxy signer

    # Build and push multi-platform images (CI)
    IMAGE_REGISTRY=ghcr.io/niis/x-road \\
    IMAGE_TAG=7.8.0-123 \\
      ./build-images.sh all --platforms linux/amd64,linux/arm64 --push

EOF
  exit 0
}

# Parse arguments
SERVICES=()
PLATFORMS=""
PUSH=""

while [[ $# -gt 0 ]]; do
  case $1 in
  --help)
    show_help
    ;;
  --platforms)
    PLATFORMS="$2"
    shift 2
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
    # Assume it's a service name - will validate against CSV later
    SERVICES+=("$1")
    shift
    ;;
  esac
done

# If no services specified, read all from CSV
if [[ ${#SERVICES[@]} -eq 0 ]]; then
  line_number=0
  while IFS= read -r line || [[ -n "$line" ]]; do
    line_number=$((line_number + 1))
    [[ $line_number -eq 1 ]] && continue # Skip header
    [[ -z "${line// /}" ]] && continue
    IFS=',' read -r svc_name _ _ _ _ _ <<<"$line"
    SERVICES+=("$svc_name")
  done <"$SERVICE_CONFIG_CSV"
fi

# Determine environment and defaults
REGISTRY="${IMAGE_REGISTRY:-localhost:5555}"

if [[ -z "$PUSH" ]]; then
  if [[ "$REGISTRY" == "localhost:"* ]]; then
    PUSH="false"
  else
    PUSH="true"
  fi
fi

# Read version and build type from gradle.properties to construct image tags
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
IMAGE_TAG="${IMAGE_TAG:-${XROAD_VERSION}-${XROAD_BUILD_TYPE}}"

log_info "=== X-Road Security Server Images Build ==="
log_info "Registry: $REGISTRY"
log_info "Image Tag: $IMAGE_TAG"
log_info "Platforms: ${PLATFORMS:-host platform}"
log_info "Push: $PUSH"
log_info "Services: ${SERVICES[*]}"
echo

# Set up Docker Buildx
log_info "Setting up Docker Buildx..."
if [[ -n "$PLATFORMS" ]]; then
  # Multi-platform build - use docker-container driver
  if ! docker buildx inspect xroad-builder &>/dev/null; then
    docker buildx create --name xroad-builder --driver docker-container --driver-opt network=host --bootstrap --use
  else
    docker buildx use xroad-builder
  fi
else
  # Local build - use default docker driver for better image sharing
  docker buildx use default 2>/dev/null || docker buildx use orbstack
fi

# Service definitions loaded from service-config.csv (defined at top of script)
get_service_def() {
  local service=$1
  local line_number=0

  # Find and return the requested service
  while IFS= read -r line || [[ -n "$line" ]]; do
    line_number=$((line_number + 1))

    # Skip header row (first line)
    [[ $line_number -eq 1 ]] && continue
    [[ -z "${line// /}" ]] && continue

    # Parse CSV: service_name,dockerfile,gradle_path,image_name,build_artifact,base_image
    IFS=',' read -r svc_name dockerfile gradle_path img_name build_artifact base_img <<<"$line"

    if [[ "$svc_name" == "$service" ]]; then
      # Return in expected order: image_name gradle_path dockerfile build_artifact base_image
      echo "$img_name $gradle_path $dockerfile $build_artifact $base_img"
      return 0
    fi
  done <"$SERVICE_CONFIG_CSV"

  # Service not found
  return 1
}

BUILD_START_TIME=$(date +%s)

# Prepare all build contexts upfront (unused contexts are silently ignored by Docker)
BUILD_DIR="${SCRIPT_DIR}/build"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

# License files (for base and DB images)
cp "${ROOT_DIR}/LICENSE" "$BUILD_DIR/LICENSE.txt"
cp "${ROOT_DIR}/src/3RD-PARTY-NOTICES.txt" "$BUILD_DIR/"

# PKCS#11 wrapper (for signer)
PKCS11_DIR="${SRC_DIR}/libs/pkcs11wrapper"

log_info "Build contexts prepared"

# Build each service
for service in "${SERVICES[@]}"; do
  service_def=$(get_service_def "$service")
  if [[ -z "$service_def" ]]; then
    log_error "Unknown service: $service"
    continue
  fi

  read -r image_name gradle_path dockerfile build_artifact base_image <<<"$service_def"

  log_info "Building $service ($image_name)..."
  build_start=$(date +%s)

  # Image name from CSV already includes full path (e.g., ss-proxy or base-images/ss-baseline-runtime)
  full_image_name="${REGISTRY}/${image_name}"

  # Prepare build artifact context if specified in CSV
  artifact_context_args=()
  if [[ "$build_artifact" != "-" && -n "$build_artifact" ]]; then
    artifact_path="${SRC_DIR}/${gradle_path}/${build_artifact}"

    # Validate artifact exists
    if [[ ! -d "$artifact_path" ]]; then
      log_error "Build artifact not found: $artifact_path"
      log_error "Ensure the project is built first"
      exit 1
    fi

    # Mount artifact directory directly as build-artifacts context
    artifact_context_args+=(--build-context "build-artifacts=${artifact_path}")
  fi

  # Build context directory is always the dockerfile's directory
  context_dir="${SCRIPT_DIR}/$(dirname "$dockerfile")"

  # Build command with ALL contexts
  build_cmd=(
    docker buildx build
    --file "${SCRIPT_DIR}/${dockerfile}"
    --build-arg "REGISTRY=${REGISTRY}"
    --build-arg "IMAGE_TAG=${IMAGE_TAG}"
    --build-context "build=${BUILD_DIR}"
    --build-context "license=${BUILD_DIR}"
    --build-context "pkcs11driver=${PKCS11_DIR}"
    --build-context "entrypoint=${context_dir}"
  )

  # Add BASE_IMAGE build arg if specified in CSV (pass as-is, Dockerfile constructs full path)
  if [[ "$base_image" != "-" && -n "$base_image" ]]; then
    build_cmd+=(--build-arg "BASE_IMAGE=${base_image}")
  fi

  # Add CHANGELOG_FILE build arg for DB init services
  if [[ "$service" == db-* ]]; then
    # Extract changelog name from service name: db-messagelog-init -> messagelog
    changelog_name="${service#db-}"      # Remove "db-" prefix
    changelog_name="${changelog_name%-init}"  # Remove "-init" suffix
    build_cmd+=(--build-arg "CHANGELOG_FILE=${changelog_name}-changelog.xml")
  fi

  # Add platform if specified
  [[ -n "$PLATFORMS" ]] && build_cmd+=(--platform "$PLATFORMS")

  # Add service-specific artifact context (from CSV config)
  [[ ${#artifact_context_args[@]} -gt 0 ]] && build_cmd+=("${artifact_context_args[@]}")

  # Add tag and push/load
  build_cmd+=(
    --tag "${full_image_name}:${IMAGE_TAG}"
  )

  if [[ "$PUSH" == "true" ]]; then
    build_cmd+=(--push)
  else
    build_cmd+=(--load)
  fi

  # Add build context directory
  build_cmd+=("$context_dir")

  # Execute build
  if "${build_cmd[@]}"; then
    build_end=$(date +%s)
    build_duration=$((build_end - build_start))
    log_success "Built $service in $(format_duration $build_duration)"
  else
    log_error "Failed to build $service"
    exit 1
  fi
done

# Summary
BUILD_END_TIME=$(date +%s)
TOTAL_BUILD_TIME=$((BUILD_END_TIME - BUILD_START_TIME))

echo
log_success "=== Build Complete ==="
log_success "Total time: $(format_duration $TOTAL_BUILD_TIME)"
log_success "Built ${#SERVICES[@]} service(s)"
if [[ "$PUSH" == "true" ]]; then
  log_success "Images pushed to $REGISTRY"
else
  log_success "Images loaded to local Docker"
fi
