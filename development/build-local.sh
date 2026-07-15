#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CORE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
SRC_DIR="${CORE_DIR}/src"
IMAGES_DIR="${CORE_DIR}/deployment/security-server/images"

source "${CORE_DIR}/.scripts/base-script.sh"

REGISTRY="${IMAGE_REGISTRY:-localhost:5555}"
SKIP_TESTS="false"
NO_CACHE="false"
NO_BUILD="false"
NO_REGISTRY="false"
SERVICES=()

show_help() {
  cat <<EOF
Build X-Road Security Server container images for local development.

Chains the two steps a local E2E run needs, so images in the registry
are always freshly compiled:

  1. ensure a local Docker registry is running (${REGISTRY})
  2. compile all modules            (core/src: ./gradlew build)
  3. build + push component images   (build-images.sh --push)

After it finishes, run the test tier you want, e.g. from core/src:
  ./gradlew :security-server:api-test:clean :security-server:api-test:intTest
  ./gradlew :security-server:e2e-test:clean :security-server:e2e-test:e2eTest

USAGE:
    ./build-local.sh [service...] [options]

SERVICES:
    <service_name>   One or more service names from service-config.csv.
                     Omit to build every image (default).

OPTIONS:
    -s, --skip-tests   Skip unit/integration tests during the Gradle build.
    -c, --no-cache     Disable the Docker build cache (passed to build-images.sh).
    -b, --no-build     Skip the Gradle compile; build images from existing artifacts.
    -r, --registry R   Target registry (default: ${REGISTRY}). Sets IMAGE_REGISTRY.
        --no-registry  Skip the local registry container guard (use for a remote registry).
    -h, --help         Show this help.

EXAMPLES:
    # Full clean-to-images build, then leave images in localhost:5555
    ./build-local.sh

    # Rebuild only proxy and signer images (skip tests for speed)
    ./build-local.sh proxy signer --skip-tests

    # Reuse already-compiled artifacts, just rebuild all images
    ./build-local.sh --no-build
EOF
  exit 0
}

while [[ $# -gt 0 ]]; do
  case $1 in
  -h | --help) show_help ;;
  -s | --skip-tests) SKIP_TESTS="true"; shift ;;
  -c | --no-cache) NO_CACHE="true"; shift ;;
  -b | --no-build) NO_BUILD="true"; shift ;;
  --no-registry) NO_REGISTRY="true"; shift ;;
  -r | --registry) REGISTRY="$2"; shift 2 ;;
  -*) log_error "Unknown option: $1"; show_help ;;
  *) SERVICES+=("$1"); shift ;;
  esac
done

export IMAGE_REGISTRY="$REGISTRY"


START_TIME=$(date +%s)

log_info "=== Local Security Server image build ==="
log_info "Registry:    $REGISTRY"
log_info "Compile:     $(if [[ "$NO_BUILD" == "true" ]]; then echo "skipped (--no-build)"; else echo "yes"; fi)"
log_info "Unit tests:  $(if [[ "$SKIP_TESTS" == "true" ]]; then echo "skipped"; else echo "run"; fi)"
log_info "Docker cache: $(if [[ "$NO_CACHE" == "true" ]]; then echo "disabled"; else echo "enabled"; fi)"
log_info "Services:    ${SERVICES[*]:-all}"
echo

if [[ "$NO_REGISTRY" == "true" ]]; then
  log_info "Skipping local registry guard (--no-registry)"
elif [[ "$REGISTRY" == "localhost:"* ]]; then
  log_info "--- Ensuring local registry ---"
  ensure_local_registry
else
  log_info "Remote registry $REGISTRY — skipping local registry guard"
fi

if [[ "$NO_BUILD" != "true" ]]; then
  log_info "--- Compiling modules (core/src) ---"
  GRADLE_ARGS=(build :tool:otel-javaagent-dist:assemble)
  if [[ "$SKIP_TESTS" == "true" ]]; then
    GRADLE_ARGS+=(-x test -x intTest)
  fi
  (cd "$SRC_DIR" && ./gradlew "${GRADLE_ARGS[@]}")
else
  log_info "--- Skipping compile (--no-build) ---"
fi

log_info "--- Building images (build-images.sh --push) ---"
BUILD_IMAGES_ARGS=(--push)
[[ "$NO_CACHE" == "true" ]] && BUILD_IMAGES_ARGS+=(--no-cache)
(cd "$IMAGES_DIR" && IMAGE_REGISTRY="$REGISTRY" ./build-images.sh "${SERVICES[@]+"${SERVICES[@]}"}" "${BUILD_IMAGES_ARGS[@]}")

END_TIME=$(date +%s)
echo
log_success "=== Done in $(format_duration $((END_TIME - START_TIME))) ==="
log_success "Images available in $REGISTRY"
log_success "Next: run a test tier from core/src, e.g."
log_success "  ./gradlew :security-server:api-test:clean :security-server:api-test:intTest"
