#!/usr/bin/env bash

set -e

# Source base script for common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Calculate repository root: from intTest/ go up 6 levels to reach repo root
# Script location: src/central-server/admin-service/int-test/src/intTest/
ROOT_DIR="$(cd "${SCRIPT_DIR}/../../../../../../" && pwd)"
source "${ROOT_DIR}/.scripts/base-script.sh"

# Paths
SRC_DIR="${ROOT_DIR}/src"

# Step 1: Build the project without tests
log_info "=== Step 1: Building project without tests ==="
cd "${SRC_DIR}"
./gradlew build -xtest -xintTest -xcheckstyleMain -xcheckstyleTest -xcheckstyleIntTest
if [[ $? -ne 0 ]]; then
    log_error "Failed to build project"
    exit 1
fi
log_success "Project built successfully"

# Step 2: Build packages
log_info "=== Step 2: Building packages ==="
cd "${ROOT_DIR}"
./src/build_packages.sh -d -r noble --package-only
if [[ $? -ne 0 ]]; then
    log_error "Failed to build packages"
    exit 1
fi
log_success "Packages built successfully"

# Step 3: Build development image
log_info "=== Step 3: Building development image ==="
cd "${ROOT_DIR}"
./development/docker/central-server/build-cs-dev-image.sh
if [[ $? -ne 0 ]]; then
    log_error "Failed to build development image"
    exit 1
fi
log_success "Development image built successfully"

log_success "=== All steps completed successfully ==="

