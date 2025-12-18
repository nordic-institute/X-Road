#!/bin/bash
# Unified script to prepare package builder images
# - Pull from registry if available
# - Build locally if pull fails
# - Push to registry after building
# Used by both build_packages.sh and CI workflows

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Configuration from environment variables
IMAGE_REGISTRY="${IMAGE_REGISTRY:-localhost:5555}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
BUILD_PLATFORMS="${BUILD_PLATFORMS:-}"  # Empty = host platform only
RELEASE="${1:-}"

# Available releases
ALL_RELEASES=(deb-jammy deb-noble rpm-el8 rpm-el9)

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
  echo "Usage: $0 <release|all>" >&2
  echo "" >&2
  echo "Examples:" >&2
  echo "  $0 deb-noble          # Prepare single image" >&2
  echo "  $0 all                # Prepare all images" >&2
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
  
  # Try to pull from registry
  log_warn "Attempting to pull from registry..."
  if docker pull "$IMAGE_NAME" 2>/dev/null; then
    log_success "Pulled ${release} from registry"
    continue
  fi
  
  # Build if pull failed
  log_warn "Could not pull from registry, building..."
  
  if [[ ! -d "$SCRIPT_DIR/$release" ]]; then
    log_error "Dockerfile directory not found: $SCRIPT_DIR/$release"
    FAILED_BUILDS+=("$release")
    continue
  fi
  
  BUILD_START=$(date +%s)
  
  if [[ -n "$BUILD_PLATFORMS" ]] && [[ "$BUILD_PLATFORMS" == *","* ]]; then
    # Multi-platform build with buildx
    log_info "  Platforms: ${BUILD_PLATFORMS}"
    if docker buildx build \
      --platform "$BUILD_PLATFORMS" \
      --file "$SCRIPT_DIR/$release/Dockerfile" \
      --tag "$IMAGE_NAME" \
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
    
    if docker build -q -t "$IMAGE_NAME" "$SCRIPT_DIR/$release/"; then
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
    log_success "✅ All ${#RELEASES_TO_PROCESS[@]} images prepared successfully"
  fi
fi

