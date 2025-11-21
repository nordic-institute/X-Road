#!/usr/bin/env bash

set -e

# Source base script for common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../../../" && pwd)"
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


# Step 2: Build ss images
log_info "=== Step 3: Building development image ==="
cd "${ROOT_DIR}"
./deployment/security-server/images/build-images.sh --push
if [[ $? -ne 0 ]]; then
    log_error "Failed to build development image"
    exit 1
fi
log_success "=== All steps completed successfully ==="

