#!/usr/bin/env bash

# Unified Base Images Build Script
# Supports both local development and CI environments
# Compatible with Bash 3.2+ (macOS default)
# Usage: ./build-base-images.sh [options]
#
# Options:
#   --registry REGISTRY     Registry URL (default: localhost:5555 for local, ghcr.io for CI)
#   --version VERSION       Override version (default: read from gradle.properties)
#   --environment ENV       Environment: local|ci (default: local)
#   --platforms PLATFORMS   Build platforms (default: linux/amd64 for local, linux/amd64,linux/arm64 for CI)
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
VERSION=""
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
    cat << EOF
Unified Base Images Build Script

USAGE:
    ./build-base-images.sh [options]

OPTIONS:
    --registry REGISTRY     Registry URL (default: localhost:5555 for local, ghcr.io for CI)
    --version VERSION       Override version (default: read from gradle.properties)
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
    
    # Manual version override
    ./build-base-images.sh --version 7.8.1-hotfix

VERSIONING:
    The script reads xroadVersion and xroadBuildType from src/gradle.properties and constructs 
    versions following the same logic as the Java Version class:
    
    - RELEASE: {version} (e.g., 7.8.0)
    - SNAPSHOT: {version}-SNAPSHOT-{commitHash} (e.g., 7.8.0-SNAPSHOT-abc12345)
EOF
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --registry)
            REGISTRY="$2"
            shift 2
            ;;
        --version)
            VERSION="$2"
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

# Function to get git information
get_git_commit_hash() {
    git rev-parse --short HEAD 2>/dev/null || echo "unknown"
}

get_git_commit_date() {
    git log -1 --format="%Y%m%d" 2>/dev/null || echo "$(date +%Y%m%d)"
}

# Function to construct version following X-Road logic
construct_version() {
    local base_version="$1"
    local build_type="$2"
    
    if [[ "$build_type" == "RELEASE" ]]; then
        echo "$base_version"
    else
        local commit_hash=$(get_git_commit_hash)
        echo "${base_version}-SNAPSHOT-${commit_hash}"
    fi
}


# Function to format duration
format_duration() {
    local duration=$1
    local minutes=$((duration / 60))
    local seconds=$((duration % 60))
    printf "%dm %ds" $minutes $seconds
}

# Read version from gradle.properties if not overridden
if [[ -z "$VERSION" ]]; then
    if [[ -f "$GRADLE_PROPERTIES" ]]; then
        log_info "Reading version from gradle.properties..."
        XROAD_VERSION=$(read_gradle_property "xroadVersion" "$GRADLE_PROPERTIES")
        XROAD_BUILD_TYPE=$(read_gradle_property "xroadBuildType" "$GRADLE_PROPERTIES")
        
        if [[ -n "$XROAD_VERSION" && -n "$XROAD_BUILD_TYPE" ]]; then
            VERSION=$(construct_version "$XROAD_VERSION" "$XROAD_BUILD_TYPE")
            BASE_VERSION="$XROAD_VERSION"
            BUILD_TYPE="$XROAD_BUILD_TYPE"
        else
            log_info "Could not read version from gradle.properties, using 'latest'"
            VERSION="latest"
            BASE_VERSION="latest"
            BUILD_TYPE="MANUAL"
        fi
    else
        log_info "gradle.properties not found, using 'latest'"
        VERSION="latest"
        BASE_VERSION="latest"
        BUILD_TYPE="MANUAL"
    fi
else
    BASE_VERSION="$VERSION"
    BUILD_TYPE="MANUAL"
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
    IMAGE_PREFIX="$REGISTRY"
fi

# Define image names and dockerfiles
IMAGES="ss-baseline-runtime:Dockerfile-baseline ss-baseline-backup-manager-runtime:Dockerfile-backup-manager-baseline ss-baseline-signer-runtime:Dockerfile-signer-baseline"

# Build summary data
BUILD_TIMES=""
BUILD_SUMMARY=""

log_info "=== X-Road Base Images Build ==="
log_info "Environment: $ENVIRONMENT"
log_info "Registry: $REGISTRY"
log_info "Image Prefix: $IMAGE_PREFIX"
log_info "Version: $VERSION"
log_info "Base Version: $BASE_VERSION"
log_info "Build Type: $BUILD_TYPE"
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

for image_entry in $IMAGES; do
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
    
    # Add tags
    if [[ "$BUILD_TYPE" == "RELEASE" ]]; then
        build_cmd+=(
            --tag "${full_image_name}:${VERSION}"
            --tag "${full_image_name}:latest"
        )
    else
        build_cmd+=(
            --tag "${full_image_name}:${VERSION}"
            --tag "${full_image_name}:${BASE_VERSION}-SNAPSHOT"
        )
    fi

    build_cmd+=(--push)
    build_cmd+=(.)
    
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
    summary+="**Version:** \`$VERSION\`\n"
    summary+="**Base Version:** \`$BASE_VERSION\`\n"
    summary+="**Build Type:** \`$BUILD_TYPE\`\n"
    summary+="**Registry:** \`$IMAGE_PREFIX\`\n"
    summary+="**Platforms:** \`$PLATFORMS\`\n"
    summary+="**Total Build Time:** $(format_duration $TOTAL_BUILD_TIME)\n\n"
    
    summary+="## Images Built\n\n"
    summary+="| Image | Version Tags | Build Time |\n"
    summary+="|-------|-------------|------------|\n"
    
    for image_entry in $IMAGES; do
        local image_name=$(echo "$image_entry" | cut -d':' -f1)
        local tags=""
        if [[ "$BUILD_TYPE" == "RELEASE" ]]; then
            tags="\`${VERSION}\`, \`latest\`"
        else
            tags="\`${VERSION}\`, \`${BASE_VERSION}-SNAPSHOT\`"
        fi
        
        local build_time_seconds=$(get_build_time "$image_name")
        local build_time=$(format_duration $build_time_seconds)
        
        summary+="| \`$image_name\` | $tags | $build_time |\n"
    done
    
    echo -e "$summary"
}

BUILD_SUMMARY=$(generate_build_summary)

# Output summary
log_success "=== Build Complete ==="
log_success "Total time: $(format_duration $TOTAL_BUILD_TIME)"
log_success "Images built: $(echo $IMAGES | wc -w)"

if [[ "$QUIET" != "true" ]]; then
    echo
    echo "=== Build Summary ==="
    if [[ -n "$SUMMARY_FILE" ]]; then
        echo "$BUILD_SUMMARY" > "$SUMMARY_FILE"
        log_info "Build summary written to: $SUMMARY_FILE"
        echo
    else
        echo -e "$BUILD_SUMMARY"
    fi
fi

# Final status
log_success "✅ All base images built successfully!"