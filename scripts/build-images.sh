#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CORE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
SRC_DIR="${CORE_DIR}/src"
BUILD_DEV_INFRA="${SCRIPT_DIR}/images/build-dev-infra.sh"
BUILD_SECURITY_SERVER="${SCRIPT_DIR}/images/build-security-server.sh"
BUILD_SIGNER_WITH_HSM="${SCRIPT_DIR}/images/build-signer-with-hsm.sh"
BUILD_CENTRAL_SERVER="${SCRIPT_DIR}/images/build-central-server.sh"
PACKAGE_SCRIPT="${SCRIPT_DIR}/build-native-packages.sh"

source "${CORE_DIR}/scripts/lib/base-script.sh"

REGISTRY="${IMAGE_REGISTRY:-localhost:5555}"
SKIP_INFRA="false"
SKIP_SS="false"
SKIP_CS="false"
SKIP_SIGNER_HSM="false"
SKIP_TESTS="false"
NO_BUILD="false"
NO_REGISTRY="false"

show_help() {
  cat <<EOF
Build all local X-Road tiers: dev-infra, Security Server, and Central Server.

A bare run builds all three tiers a local E2E environment needs, so you never
have to know the sequence of underlying scripts:

  1. ensure a local Docker registry is running (${REGISTRY})
  2. build dev-infra images        (build-dev-infra.sh --push)
  3. compile all modules           (src: ./gradlew build)
  4. build + push SS images        (build-security-server.sh --push)
  5. build + push the signer-with-hsm stop-gap image (build-signer-with-hsm.sh --push)
  6. build native DEB packages     (build-native-packages.sh -r resolute)
  7. build + push the CS image     (build-central-server.sh)

Trim tiers with --skip-infra / --skip-ss / --skip-cs / --skip-signer-hsm.
After it finishes, run the test tier you want, e.g. from src:
  ./gradlew :security-server:api-test:clean :security-server:api-test:intTest
  ./gradlew :security-server:e2e-test:clean :security-server:e2e-test:e2eTest

USAGE:
    ./build-images.sh [options]

OPTIONS:
    --skip-infra       Skip the dev-infra tier (openbao, testca, postgres-dev, nginx-cp).
    --skip-ss          Skip the Security Server tier (Gradle compile + SS images).
    --skip-cs          Skip the Central Server tier (DEB build + CS image).
    --skip-signer-hsm  Skip the signer-with-hsm stop-gap image (needs ss-signer from the
                       Security Server tier; auto-skipped when --skip-ss is passed).
    -s, --skip-tests   Skip unit/integration tests during the Gradle build.
    -b, --no-build     Skip the Gradle compile and the DEB build; rebuild images
                       only from existing artifacts/packages.
    -r, --registry R   Target registry (default: ${REGISTRY}). Sets IMAGE_REGISTRY.
        --no-registry  Skip the local registry container guard (use for a remote registry).
    -h, --help         Show this help.

EXAMPLES:
    # Full local build: infra + Security Server + Central Server
    ./build-images.sh

    # Security Server only, skip tests for speed
    ./build-images.sh --skip-infra --skip-cs --skip-tests

    # Reuse already-compiled artifacts and packages, just rebuild images
    ./build-images.sh --no-build

Granular single-image selection is done by calling
images/build-security-server.sh <service...> directly.
EOF
  exit 0
}

while [[ $# -gt 0 ]]; do
  case $1 in
  -h | --help) show_help ;;
  --skip-infra) SKIP_INFRA="true"; shift ;;
  --skip-ss) SKIP_SS="true"; shift ;;
  --skip-cs) SKIP_CS="true"; shift ;;
  --skip-signer-hsm) SKIP_SIGNER_HSM="true"; shift ;;
  -s | --skip-tests) SKIP_TESTS="true"; shift ;;
  -b | --no-build) NO_BUILD="true"; shift ;;
  --no-registry) NO_REGISTRY="true"; shift ;;
  -r | --registry) REGISTRY="$2"; shift 2 ;;
  *) log_error "Unknown option: $1"; show_help ;;
  esac
done

if [[ "$SKIP_INFRA" == "true" && "$SKIP_SS" == "true" && "$SKIP_CS" == "true" ]]; then
  log_error "--skip-infra, --skip-ss and --skip-cs given together — nothing to build."
  exit 1
fi

export IMAGE_REGISTRY="$REGISTRY"

# Build the "Tiers: ..." banner line, annotating any skipped tiers.
SELECTED_TIERS=()
SKIPPED_TIERS=()
[[ "$SKIP_INFRA" == "true" ]] && SKIPPED_TIERS+=("infra") || SELECTED_TIERS+=("infra")
[[ "$SKIP_SS" == "true" ]] && SKIPPED_TIERS+=("security-server") || SELECTED_TIERS+=("security-server")
[[ "$SKIP_CS" == "true" ]] && SKIPPED_TIERS+=("central-server") || SELECTED_TIERS+=("central-server")

join_by_comma() {
  local IFS=','
  echo "$*"
}

tiers_line="Tiers: $(join_by_comma "${SELECTED_TIERS[@]}" | sed 's/,/, /g')"
if [[ ${#SKIPPED_TIERS[@]} -gt 0 ]]; then
  skipped_joined="$(join_by_comma "${SKIPPED_TIERS[@]}" | sed 's/,/, /g')"
  tiers_line="${tiers_line} (${skipped_joined} skipped)"
fi

echo -e "${CYAN}=== Local build → ${REGISTRY} ===${NC}"
echo -e "${CYAN}${tiers_line}${NC}"
echo -e "${CYAN}Tip: trim with --skip-infra / --skip-ss / --skip-cs   (--help for more)${NC}"
echo

START_TIME=$(date +%s)
INFRA_TIME=0
GRADLE_TIME=0
SS_TIME=0
SIGNER_HSM_TIME=0
DEB_TIME=0
CS_TIME=0
GRADLE_BUILT="false"

if [[ "$NO_REGISTRY" == "true" ]]; then
  log_info "Skipping local registry guard (--no-registry)"
elif [[ "$REGISTRY" == "localhost:"* ]]; then
  log_info "--- Ensuring local registry ---"
  ensure_local_registry
else
  log_info "Remote registry $REGISTRY — skipping local registry guard"
fi

# --- Infra tier -------------------------------------------------------------
if [[ "$SKIP_INFRA" != "true" ]]; then
  log_info "--- Building dev-infra images (build-dev-infra.sh --push) ---"
  tier_start=$(date +%s)
  IMAGE_REGISTRY="$REGISTRY" "${BUILD_DEV_INFRA}" --push
  INFRA_TIME=$(( $(date +%s) - tier_start ))
else
  log_info "--- Skipping infra tier (--skip-infra) ---"
fi

# --- Gradle compile (feeds the Security Server tier) ------------------------
if [[ "$SKIP_SS" != "true" && "$NO_BUILD" != "true" ]]; then
  log_info "--- Compiling modules (src) ---"
  tier_start=$(date +%s)
  GRADLE_ARGS=(build :tool:otel-javaagent-dist:assemble)
  if [[ "$SKIP_TESTS" == "true" ]]; then
    GRADLE_ARGS+=(-x test -x intTest)
  fi
  (cd "$SRC_DIR" && ./gradlew "${GRADLE_ARGS[@]}")
  GRADLE_TIME=$(( $(date +%s) - tier_start ))
  GRADLE_BUILT="true"
elif [[ "$NO_BUILD" == "true" ]]; then
  log_info "--- Skipping compile (--no-build) ---"
else
  log_info "--- Skipping compile (--skip-ss) ---"
fi

# --- Security Server tier ----------------------------------------------------
if [[ "$SKIP_SS" != "true" ]]; then
  log_info "--- Building Security Server images (build-security-server.sh --push) ---"
  tier_start=$(date +%s)
  IMAGE_REGISTRY="$REGISTRY" "${BUILD_SECURITY_SERVER}" --push
  SS_TIME=$(( $(date +%s) - tier_start ))
else
  log_info "--- Skipping Security Server tier (--skip-ss) ---"
fi

# --- Signer-with-HSM stop-gap image ------------------------------------------
# Layers SoftHSM2 onto the ss-signer image the tier above just pushed — needed
# whenever a Security Server release enables the chart's signer.hsm.enabled
# toggle (e.g. the k8s E2E topology's ss1). Requires ss-signer to already be
# in the registry, so it only runs when the SS tier ran too.
if [[ "$SKIP_SIGNER_HSM" != "true" && "$SKIP_SS" != "true" ]]; then
  log_info "--- Building signer-with-hsm image (build-signer-with-hsm.sh --push) ---"
  tier_start=$(date +%s)
  IMAGE_REGISTRY="$REGISTRY" "${BUILD_SIGNER_WITH_HSM}" --push
  SIGNER_HSM_TIME=$(( $(date +%s) - tier_start ))
elif [[ "$SKIP_SIGNER_HSM" != "true" ]]; then
  log_info "--- Skipping signer-with-hsm image (--skip-ss also skips its ss-signer base) ---"
else
  log_info "--- Skipping signer-with-hsm image (--skip-signer-hsm) ---"
fi

# --- Central Server tier: DEB packages, then the CS image -------------------
if [[ "$SKIP_CS" != "true" ]]; then
  if [[ "$NO_BUILD" != "true" ]]; then
    log_info "--- Building Ubuntu 26.04 DEB packages (build-native-packages.sh -r resolute) ---"
    tier_start=$(date +%s)
    PACKAGE_ARGS=(-r resolute)
    if [[ "$GRADLE_BUILT" == "true" ]]; then
      # Modules were already compiled above for the SS tier — don't recompile.
      PACKAGE_ARGS+=(--package-only)
    elif [[ "$SKIP_TESTS" == "true" ]]; then
      # build-native-packages.sh does its own local compile; propagate the test-skip flag.
      PACKAGE_ARGS+=(--skip-tests)
    fi
    "${PACKAGE_SCRIPT}" "${PACKAGE_ARGS[@]}"
    DEB_TIME=$(( $(date +%s) - tier_start ))
  else
    log_info "--- Skipping DEB build (--no-build), reusing existing packages ---"
  fi

  log_info "--- Building Central Server image (build-central-server.sh) ---"
  tier_start=$(date +%s)
  "${BUILD_CENTRAL_SERVER}" --registry "$REGISTRY"
  CS_TIME=$(( $(date +%s) - tier_start ))
else
  log_info "--- Skipping Central Server tier (--skip-cs) ---"
fi

END_TIME=$(date +%s)
echo
log_info "--- Timing summary ---"
[[ "$SKIP_INFRA" != "true" ]] && log_info "infra:            $(format_duration "$INFRA_TIME")"
if [[ "$SKIP_SS" != "true" ]]; then
  if [[ "$GRADLE_BUILT" == "true" ]]; then
    log_info "gradle compile:   $(format_duration "$GRADLE_TIME")"
  else
    log_info "gradle compile:   skipped"
  fi
  log_info "security-server:  $(format_duration "$SS_TIME")"
  if [[ "$SKIP_SIGNER_HSM" != "true" ]]; then
    log_info "signer-with-hsm:  $(format_duration "$SIGNER_HSM_TIME")"
  else
    log_info "signer-with-hsm:  skipped (--skip-signer-hsm)"
  fi
fi
if [[ "$SKIP_CS" != "true" ]]; then
  if [[ "$NO_BUILD" != "true" ]]; then
    log_info "deb packages:     $(format_duration "$DEB_TIME")"
  else
    log_info "deb packages:     skipped (--no-build)"
  fi
  log_info "central-server:   $(format_duration "$CS_TIME")"
fi
log_success "=== Done in $(format_duration $((END_TIME - START_TIME))) ==="
log_success "Images available in $REGISTRY"
log_success "Next: run a test tier from src, e.g."
log_success "  ./gradlew :security-server:api-test:clean :security-server:api-test:intTest"
