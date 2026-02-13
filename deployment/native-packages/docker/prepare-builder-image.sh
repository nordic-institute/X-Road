#!/bin/bash
# Unified script to prepare package builder images
# - Pull from registry if available
# - Build locally if pull fails
# - Push to registry after building
# Used by both build_packages.sh and CI workflows

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"

# Configuration from environment variables
IMAGE_REGISTRY="${IMAGE_REGISTRY:-localhost:5555}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
BUILD_PLATFORMS="${BUILD_PLATFORMS:-}"  # Empty = host platform only

# Available releases
ALL_RELEASES=(deb-jammy deb-noble rpm-el8 rpm-el9)

# Parse arguments
FORCE_BUILD=false
NO_CACHE=false
RELEASE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    -f|--force)
      FORCE_BUILD=true
      shift
      ;;
    --no-cache)
      NO_CACHE=true
      shift
      ;;
    -*)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
    *)
      RELEASE="$1"
      shift
      ;;
  esac
done

# Prepare mirror build args (only if mirror configured)
MIRROR_BUILD_ARGS_APT=()
MIRROR_BUILD_ARGS_RPM=()

# Add Docker Hub mirror build arg if configured
if [[ -n "${XROAD_MIRROR_DOCKER_URL:-}" ]]; then
  MIRROR_BUILD_ARGS_APT+=(--build-arg "DOCKER_REGISTRY=${XROAD_MIRROR_DOCKER_URL}")
  MIRROR_BUILD_ARGS_RPM+=(--build-arg "DOCKER_REGISTRY=${XROAD_MIRROR_DOCKER_URL}")
fi

if [[ -n "$XROAD_MIRROR_UBUNTU_URL" ]] && [[ -n "$XROAD_MIRROR_USERNAME" ]] && [[ -n "$XROAD_MIRROR_TOKEN" ]]; then
  # For APT-based builds
  MIRROR_BUILD_ARGS_APT+=(
    --build-arg XROAD_MIRROR_URL="$XROAD_MIRROR_UBUNTU_URL"
    --build-arg XROAD_MIRROR_USER="$XROAD_MIRROR_USERNAME"
    --secret "id=mirror_token,env=XROAD_MIRROR_TOKEN"
    --build-context "mirror-scripts=${ROOT_DIR}/deployment/.scripts"
  )
  # For YUM/DNF-based builds (uses BASE_URL - strip the specific mirror path)
  MIRROR_BASE_URL="${XROAD_MIRROR_UBUNTU_URL%/mirror-*}"
  MIRROR_BUILD_ARGS_RPM+=(
    --build-arg XROAD_MIRROR_BASE_URL="$MIRROR_BASE_URL"
    --build-arg XROAD_MIRROR_USER="$XROAD_MIRROR_USERNAME"
    --secret "id=mirror_token,env=XROAD_MIRROR_TOKEN"
    --build-context "mirror-scripts=${ROOT_DIR}/deployment/.scripts"
  )
fi

# Color codes
isTextColoringEnabled=$(command -v tput >/dev/null && tput setaf 1 &>/dev/null && echo true || echo false)

log_info() {
  if $isTextColoringEnabled; then
    echo "$(tput setaf 4)[INFO]$(tput sgr0) $1"
  else
    echo "[INFO] $1"
  fi
}

log_warn() {
  if $isTextColoringEnabled; then
    echo "$(tput setaf 3)[WARN]$(tput sgr0) $1"
  else
    echo "[WARN] $1"
  fi
}

log_error() {
  if $isTextColoringEnabled; then
    echo "$(tput setaf 1)[ERROR]$(tput sgr0) $1" >&2
  else
    echo "[ERROR] $1" >&2
  fi
}

log_success() {
  if $isTextColoringEnabled; then
    echo "$(tput setaf 2)[SUCCESS]$(tput sgr0) $1"
  else
    echo "[SUCCESS] $1"
  fi
}

# Validate arguments
if [[ -z "$RELEASE" ]]; then
  echo "Usage: $0 [options] <release|all>" >&2
  echo "" >&2
  echo "Options:" >&2
  echo "  -f, --force     Force build without checking registry" >&2
  echo "  --no-cache      Build without using cache" >&2
  echo "" >&2
  echo "Examples:" >&2
  echo "  $0 deb-noble              # Prepare single image (pull or build)" >&2
  echo "  $0 all                    # Prepare all images" >&2
  echo "  $0 -f all                 # Force rebuild all images" >&2
  echo "  $0 -f --no-cache rpm-el9  # Force rebuild without cache" >&2
  echo "" >&2
  echo "Available releases: ${ALL_RELEASES[*]}" >&2
  exit 1
fi

# Determine which releases to process
if [[ "$RELEASE" == "all" ]]; then
  RELEASES_TO_PROCESS=("${ALL_RELEASES[@]}")
else
  RELEASES_TO_PROCESS=("$RELEASE")
fi

# Set up Docker Buildx if building for multiple platforms
if [[ -n "$BUILD_PLATFORMS" ]] && [[ "$BUILD_PLATFORMS" == *","* ]]; then
  log_info "Setting up Docker Buildx for multi-platform build..."
  if ! docker buildx inspect xroad-builder &>/dev/null; then
    docker buildx create --name xroad-builder --driver docker-container --driver-opt network=host --bootstrap --use >/dev/null
  else
    docker buildx use xroad-builder
  fi
fi

# Process each release
FAILED_BUILDS=()
for release in "${RELEASES_TO_PROCESS[@]}"; do
  IMAGE_NAME="${IMAGE_REGISTRY}/package-builder-${release}:${IMAGE_TAG}"

  log_info "Processing ${release}..."
  log_info "  Image: ${IMAGE_NAME}"

  # Try to pull from registry (unless force build)
  if [[ "$FORCE_BUILD" != "true" ]]; then
    log_warn "Attempting to pull from registry..."
    if docker pull "$IMAGE_NAME" 2>/dev/null; then
      log_success "Pulled ${release} from registry"
      continue
    fi
    log_warn "Could not pull from registry, building..."
  else
    log_info "Force build enabled, skipping registry check..."
  fi

  if [[ ! -d "$SCRIPT_DIR/$release" ]]; then
    log_error "Dockerfile directory not found: $SCRIPT_DIR/$release"
    FAILED_BUILDS+=("$release")
    continue
  fi

  # Set appropriate mirror build args based on release type
  MIRROR_BUILD_ARGS=()
  if [[ "$release" == deb-* ]]; then
    MIRROR_BUILD_ARGS=("${MIRROR_BUILD_ARGS_APT[@]}")
  else
    MIRROR_BUILD_ARGS=("${MIRROR_BUILD_ARGS_RPM[@]}")
  fi

  BUILD_START=$(date +%s)

  # Build cache flag
  CACHE_FLAG=()
  if [[ "$NO_CACHE" == "true" ]]; then
    CACHE_FLAG=(--no-cache)
    log_info "  Cache: disabled"
  fi

  if [[ -n "$BUILD_PLATFORMS" ]] && [[ "$BUILD_PLATFORMS" == *","* ]]; then
    # Multi-platform build with buildx
    log_info "  Platforms: ${BUILD_PLATFORMS}"
    if docker buildx build --progress=plain \
      --platform "$BUILD_PLATFORMS" \
      --file "$SCRIPT_DIR/$release/Dockerfile" \
      --tag "$IMAGE_NAME" \
      "${CACHE_FLAG[@]}" \
      "${MIRROR_BUILD_ARGS[@]}" \
      --push \
      "$SCRIPT_DIR/$release/" >/dev/null; then

      BUILD_END=$(date +%s)
      DURATION=$((BUILD_END - BUILD_START))
      log_success "Built and pushed ${release} (${DURATION}s)"
    else
      log_error "Failed to build ${release}"
      FAILED_BUILDS+=("$release")
    fi
  else
    # Single platform build (host architecture)
    if [[ -n "$BUILD_PLATFORMS" ]]; then
      log_info "  Platform: ${BUILD_PLATFORMS}"
    else
      log_info "  Platform: host"
    fi

    if docker build --progress=plain -t "$IMAGE_NAME" "${CACHE_FLAG[@]}" "${MIRROR_BUILD_ARGS[@]}" "$SCRIPT_DIR/$release/"; then
      log_info "  Pushing to registry..."
      if docker push "$IMAGE_NAME" >/dev/null 2>&1; then
        BUILD_END=$(date +%s)
        DURATION=$((BUILD_END - BUILD_START))
        log_success "Built and pushed ${release} (${DURATION}s)"
      else
        log_warn "Built ${release} but push failed (image available locally)"
      fi
    else
      log_error "Failed to build ${release}"
      FAILED_BUILDS+=("$release")
    fi
  fi
  echo
done

# Summary
if [[ ${#FAILED_BUILDS[@]} -gt 0 ]]; then
  log_error "Failed: ${FAILED_BUILDS[*]}"
  exit 1
else
  if [[ ${#RELEASES_TO_PROCESS[@]} -gt 1 ]]; then
    log_success "All ${#RELEASES_TO_PROCESS[@]} images prepared successfully"
  fi
fi
