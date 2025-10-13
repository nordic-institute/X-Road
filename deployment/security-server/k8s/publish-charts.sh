#!/usr/bin/env bash

set -e

# Source base script for common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../../../" && pwd)"
source "${ROOT_DIR}/.scripts/base-script.sh"

# Additional paths
SRC_DIR="${ROOT_DIR}/src"
GRADLE_PROPERTIES="${SRC_DIR}/gradle.properties"
CHARTS_DIR="${ROOT_DIR}/deployment/security-server/k8s/charts"

# Show help
show_help() {
    cat <<EOF
Helm Chart Publishing Script for X-Road

USAGE:
    ./publish-charts.sh [chart...] [options]

CHARTS:
    security-server         Security Server chart
    openbao-init           OpenBao initialization chart
    external-service-bridge External service bridge chart
    all                    Package and publish all charts (default)

OPTIONS:
    --version VERSION      Chart version (defaults to xroadVersion-xroadBuildType)
    --app-version VERSION  Application version (defaults to --version)
    --push                 Push charts to OCI registry
                          Default: true in CI, false for localhost
    --help                 Show this help

ENVIRONMENT VARIABLES:
    HELM_REGISTRY          OCI registry URL (default: localhost:5555/helm)
    CHART_VERSION          Chart version to use
    APP_VERSION            Application version to use

EXAMPLES:
    # Package all charts locally (no push)
    ./publish-charts.sh

    # Package and push to Artifactory
    HELM_REGISTRY=oci://artifactory.niis.org/xroad8-helm \\
      ./publish-charts.sh all --push

    # Specify versions explicitly
    ./publish-charts.sh all \\
      --version 8.0.0 \\
      --app-version 8.0.0-20251010 \\
      --push

EOF
    exit 0
}

# Parse arguments
CHART_VERSION=""
APP_VERSION=""
PUSH=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --help|-h)
            show_help
            ;;
        --version)
            CHART_VERSION="$2"
            shift 2
            ;;
        --app-version)
            APP_VERSION="$2"
            shift 2
            ;;
        --push)
            PUSH="true"
            shift
            ;;
        all)
            # Kept for backwards compatibility, but ignored
            shift
            ;;
        -*)
            log_error "Unknown option: $1"
            show_help
            ;;
        *)
            log_error "Unknown argument: $1"
            show_help
            ;;
    esac
done

# Always build all charts
CHARTS=("security-server" "openbao-init" "external-service-bridge")

# Determine registry and defaults
REGISTRY="${HELM_REGISTRY:-localhost:5555/helm}"

# Auto-detect push behavior (same logic as build-images.sh)
if [[ -z "$PUSH" ]]; then
    if [[ "$REGISTRY" == "localhost:"* ]]; then
        PUSH="false"
    else
        PUSH="true"
    fi
fi

# Read version and build type from gradle.properties if not specified
if [[ -z "$CHART_VERSION" ]]; then
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

    # Construct version from gradle properties
    # For RELEASE builds, use version as-is (e.g., 8.0.0 or 8.0.0-beta1)
    # For non-RELEASE builds, always append build type (e.g., 8.0.0-SNAPSHOT or 8.0.0-beta1-SNAPSHOT)
    if [[ "$XROAD_BUILD_TYPE" == "RELEASE" ]]; then
        CHART_VERSION="${XROAD_VERSION}"
    else
        CHART_VERSION="${XROAD_VERSION}-${XROAD_BUILD_TYPE}"
    fi
fi

# Default app version to chart version if not specified
if [[ -z "$APP_VERSION" ]]; then
    APP_VERSION="$CHART_VERSION"
fi

# Check if helm is available
if ! command -v helm &> /dev/null; then
    log_error "helm command not found. Please install Helm 3.8+ or run in a container with Helm"
    exit 1
fi

# Verify Helm version supports OCI
# Use awk for macOS/Linux compatibility (grep -P not available on BSD)
HELM_VERSION=$(helm version --short 2>/dev/null | awk -F'[ +]' '{print $1}' | cut -d'.' -f1,2 || echo "v0.0")
if [[ -z "$HELM_VERSION" ]]; then
    HELM_VERSION="v0.0"
fi
HELM_MAJOR=$(echo "$HELM_VERSION" | cut -d'.' -f1 | tr -d 'v')
HELM_MINOR=$(echo "$HELM_VERSION" | cut -d'.' -f2)

if [[ "$HELM_MAJOR" -lt 3 ]] || { [[ "$HELM_MAJOR" -eq 3 ]] && [[ "$HELM_MINOR" -lt 8 ]]; }; then
    log_error "Helm 3.8+ is required for OCI support. Found: $HELM_VERSION"
    exit 1
fi

# Print configuration
log_info "=== X-Road Helm Charts Publishing ==="
log_info "Helm Version: $HELM_VERSION"
log_info "Registry: $REGISTRY"
log_info "Chart Version: $CHART_VERSION"
log_info "App Version: $APP_VERSION"
log_info "Push: $PUSH"
log_info "Charts: ${CHARTS[*]}"
echo

# Create temp directory for packaged charts
PACKAGE_DIR=$(mktemp -d)
trap "rm -rf $PACKAGE_DIR" EXIT

# Track success/failure
SUCCESSFUL_CHARTS=0
FAILED_CHARTS=0

# Process each chart
for chart in "${CHARTS[@]}"; do
    CHART_PATH="${CHARTS_DIR}/${chart}"
    
    log_info "Processing chart: $chart"
    
    if [[ ! -d "$CHART_PATH" ]]; then
        log_error "  Chart directory not found: $CHART_PATH"
        FAILED_CHARTS=$((FAILED_CHARTS + 1))
        continue
    fi
    
    if [[ ! -f "$CHART_PATH/Chart.yaml" ]]; then
        log_error "  Chart.yaml not found in: $CHART_PATH"
        FAILED_CHARTS=$((FAILED_CHARTS + 1))
        continue
    fi
    
    # Update Chart.yaml with new versions
    log_info "  Updating Chart.yaml versions..."
    sed -i.bak \
        -e "s/^version:.*/version: ${CHART_VERSION}/" \
        -e "s/^appVersion:.*/appVersion: \"${APP_VERSION}\"/" \
        "$CHART_PATH/Chart.yaml"
    
    # Update dependencies if Chart.lock exists
    if [[ -f "$CHART_PATH/Chart.lock" ]]; then
        log_info "  Updating chart dependencies..."
        if ! helm dependency update "$CHART_PATH" 2>&1; then
            log_error "  Failed to update dependencies"
            mv "$CHART_PATH/Chart.yaml.bak" "$CHART_PATH/Chart.yaml" 2>/dev/null || true
            FAILED_CHARTS=$((FAILED_CHARTS + 1))
            continue
        fi
    fi
    
    # Package the chart
    log_info "  Packaging chart..."
    if ! helm package "$CHART_PATH" \
        --destination "$PACKAGE_DIR" \
        --version "$CHART_VERSION" \
        --app-version "$APP_VERSION" 2>&1; then
        log_error "  Failed to package chart"
        mv "$CHART_PATH/Chart.yaml.bak" "$CHART_PATH/Chart.yaml" 2>/dev/null || true
        FAILED_CHARTS=$((FAILED_CHARTS + 1))
        continue
    fi
    
    PACKAGE_FILE="${PACKAGE_DIR}/${chart}-${CHART_VERSION}.tgz"
    
    if [[ ! -f "$PACKAGE_FILE" ]]; then
        log_error "  Package file not created: $PACKAGE_FILE"
        mv "$CHART_PATH/Chart.yaml.bak" "$CHART_PATH/Chart.yaml" 2>/dev/null || true
        FAILED_CHARTS=$((FAILED_CHARTS + 1))
        continue
    fi
    
    PACKAGE_SIZE=$(du -h "$PACKAGE_FILE" | cut -f1)
    log_success "  Packaged: $PACKAGE_FILE ($PACKAGE_SIZE)"
    
    # Push to OCI registry
    if [[ "$PUSH" == "true" ]]; then
        log_info "  Pushing to OCI registry..."
        
        # Add oci:// prefix if not present
        REGISTRY_URL="$REGISTRY"
        if [[ ! "$REGISTRY_URL" =~ ^oci:// ]]; then
            REGISTRY_URL="oci://${REGISTRY_URL}"
        fi
        
        if ! helm push "$PACKAGE_FILE" "$REGISTRY_URL" 2>&1; then
            log_error "  Failed to push to $REGISTRY_URL"
            mv "$CHART_PATH/Chart.yaml.bak" "$CHART_PATH/Chart.yaml" 2>/dev/null || true
            FAILED_CHARTS=$((FAILED_CHARTS + 1))
            continue
        fi
        
        log_success "  Pushed to: ${REGISTRY_URL}/${chart}:${CHART_VERSION}"
    fi
    
    # Restore original Chart.yaml
    mv "$CHART_PATH/Chart.yaml.bak" "$CHART_PATH/Chart.yaml" 2>/dev/null || true
    
    SUCCESSFUL_CHARTS=$((SUCCESSFUL_CHARTS + 1))
    echo
done

# Summary
echo "========================================"
if [[ $FAILED_CHARTS -eq 0 ]]; then
    log_success "All charts processed successfully!"
else
    log_warn "Processing completed with errors"
fi
echo "========================================"
log_info "Successful: $SUCCESSFUL_CHARTS"
if [[ $FAILED_CHARTS -gt 0 ]]; then
    log_error "Failed: $FAILED_CHARTS"
fi
log_info "Package directory: $PACKAGE_DIR"
if [[ "$PUSH" == "true" ]]; then
    log_info "Charts pushed to: $REGISTRY"
else
    log_info "Charts packaged locally (not pushed)"
fi
echo "========================================"

# Exit with error if any charts failed
if [[ $FAILED_CHARTS -gt 0 ]]; then
    exit 1
fi
