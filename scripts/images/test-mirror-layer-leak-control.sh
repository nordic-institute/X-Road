#!/usr/bin/env bash
# Negative control for test-mirror-layer-leak.sh.
#
# Builds a small probe image whose layer holds a dummy token followed by a large file, then scans
# it with the same dummy token. The control passes only when the scan exits 1 and names a token
# finding, so a scan that fails open does not go unnoticed. The real mirror token is never used.
#
# Usage: ./test-mirror-layer-leak-control.sh
#
# Reads XROAD_MIRROR_DOCKER_URL (optional) to pull the probe base image through the Docker mirror.
# Exit codes: 0 the scan caught the probe, 1 it did not, 2 the probe could not be built.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../../" && pwd)"
source "${ROOT_DIR}/scripts/lib/base-script.sh"

PROBE_IMAGE="mirror-layer-leak-probe:$$"
PROBE_TOKEN="dummy-probe-token-not-a-mirror-credential"
WORK_DIR="$(mktemp -d)"

cleanup() {
  docker rmi -f "${PROBE_IMAGE}" >/dev/null 2>&1
  rm -rf "${WORK_DIR}"
}
trap cleanup EXIT

cat >"${WORK_DIR}/Dockerfile" <<'EOF'
ARG DOCKER_REGISTRY=""
FROM ${DOCKER_REGISTRY}alpine:3.22
RUN --mount=type=secret,id=probe_token \
    cp /run/secrets/probe_token /a-token \
    && head -c 41943040 /dev/zero > /z-filler
EOF

build_args=()
if [[ -n "${XROAD_MIRROR_DOCKER_URL:-}" ]]; then
  build_args+=(--build-arg "DOCKER_REGISTRY=${XROAD_MIRROR_DOCKER_URL}")
fi

log_info "Building the leak probe image"
if ! PROBE_TOKEN="${PROBE_TOKEN}" docker buildx build --load \
  ${build_args[@]+"${build_args[@]}"} \
  --secret "id=probe_token,env=PROBE_TOKEN" \
  --tag "${PROBE_IMAGE}" "${WORK_DIR}" >/dev/null; then
  log_error "Cannot build the leak probe image"
  exit 2
fi

log_info "Scanning the probe image, the scan must fail"
scan_output="$(XROAD_MIRROR_TOKEN="${PROBE_TOKEN}" "${SCRIPT_DIR}/test-mirror-layer-leak.sh" "${PROBE_IMAGE}" 2>&1)"
scan_exit=$?

if [[ ${scan_exit} -ne 1 ]]; then
  log_error "Control failed: the scan exited ${scan_exit} on the probe image, expected 1"
  exit 1
fi
if ! grep -q 'FAILED: .* has no token' <<<"${scan_output}"; then
  log_error "Control failed: the scan exited 1 without a token finding"
  exit 1
fi
log_success "Control passed: the scan caught the token in the probe image"
