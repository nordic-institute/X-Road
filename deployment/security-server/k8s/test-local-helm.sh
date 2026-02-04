#!/usr/bin/env bash
# Local Helm OCI Registry Testing Script

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../../../" && pwd)"
source "${ROOT_DIR}/.scripts/base-script.sh"

REGISTRY="localhost:5555"
REGISTRY_URL="http://${REGISTRY}"
OCI_URL="oci://${REGISTRY}/helm"

echo "========================================"
log_info "Local Helm Chart Testing"
echo "========================================"
log_info "Registry: ${REGISTRY}"
log_info "OCI URL: ${OCI_URL}"
echo ""

# Check if local registry is running
log_info "Checking if local registry is running..."
if ! curl -sf "${REGISTRY_URL}/v2/" > /dev/null; then
    log_error "Local registry not running on ${REGISTRY}"
    echo ""
    log_info "Start it with:"
    echo "  docker run -d -p 5555:5000 --name local-registry registry:2"
    echo ""
    log_info "Or restart if it exists:"
    echo "  docker start local-registry"
    exit 1
fi

log_success "Local registry is running"
echo ""

# Test 1: Package charts (without push)
log_info "Step 1: Packaging Helm charts locally..."
./publish-charts.sh all

echo ""
log_success "Charts packaged successfully"
echo ""

# Test 2: Push charts to local registry
log_info "Step 2: Pushing charts to ${REGISTRY}..."
HELM_REGISTRY="${REGISTRY}/helm" \
  ./publish-charts.sh all --push

echo ""
log_success "Charts pushed successfully"
echo ""

# Test 3: Verify charts in registry
log_info "Step 3: Verifying charts in registry..."
echo ""

CHARTS=("security-server" "openbao-init" "external-service-bridge")

for chart in "${CHARTS[@]}"; do
    log_info "Checking: helm/${chart}"
    
    # Query the registry API
    RESPONSE=$(curl -sf "${REGISTRY_URL}/v2/helm/${chart}/tags/list" 2>/dev/null || echo '{"tags":[]}')
    TAGS=$(echo "$RESPONSE" | grep -o '"tags":\[[^]]*\]' | sed 's/"tags"://; s/\[//; s/\]//; s/"//g' || echo "")
    
    if [[ -n "$TAGS" && "$TAGS" != "null" ]]; then
        log_success "  Found tags: ${TAGS}"
    else
        log_warn "  No tags found"
    fi
done

echo ""
echo "========================================"
log_success "Testing Complete!"
echo "========================================"
echo ""
log_info "You can now test with Helm CLI:"
echo ""
echo "  # List available charts"
echo "  curl ${REGISTRY_URL}/v2/_catalog | jq"
echo ""
echo "  # Pull a chart"
echo "  helm pull ${OCI_URL}/security-server"
echo ""
echo "  # Show chart info"
echo "  helm show chart ${OCI_URL}/security-server"
echo ""
echo "  # Install a chart (requires k8s)"
echo "  helm install test-ss ${OCI_URL}/security-server"
echo ""
echo "  # Template chart (dry run)"
echo "  helm template test-ss ${OCI_URL}/security-server"
echo ""
