#!/usr/bin/env bash
# Layer-leak scan for images built with a package mirror.
#
# Runs the negative control (test-mirror-layer-leak-control.sh) first. Then saves each image and
# checks that every layer of every manifest entry and the image configuration hold no copy of the
# mirror token. Then checks the final filesystem for the apt TLS-verification override that the mirror
# script writes and the cleanup script removes.
#
# Usage: XROAD_MIRROR_TOKEN=<token> ./test-mirror-layer-leak.sh <image> [<image>...]
#
# LAYER_LEAK_CONTROL_ACTIVE=1 skips the control; the control sets it for its own call of this script.
# Exit codes: 0 clean, 1 a finding or a failed control, 2 usage error or a control probe that could
# not be built. The token is never printed.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../../" && pwd)"
source "${ROOT_DIR}/scripts/lib/base-script.sh"
source "${ROOT_DIR}/scripts/lib/test-helpers.sh"

TLS_OVERRIDE_PATH="etc/apt/apt.conf.d/99mirror-no-verify"

if [[ $# -lt 1 ]]; then
  log_error "Usage: XROAD_MIRROR_TOKEN=<token> $0 <image> [<image>...]"
  exit 2
fi
if [[ -z "${XROAD_MIRROR_TOKEN:-}" ]]; then
  log_error "XROAD_MIRROR_TOKEN is not set"
  exit 2
fi
for tool in docker jq tar grep; do
  if ! command -v "${tool}" >/dev/null 2>&1; then
    log_error "${tool} not found on PATH"
    exit 2
  fi
done

WORK_DIR="$(mktemp -d)"
TOKEN_FILE="${WORK_DIR}/token"
CONTAINER=""
FAILURES=0

cleanup() {
  if [[ -n "${CONTAINER}" ]]; then docker rm -f -v "${CONTAINER}" >/dev/null 2>&1; fi
  rm -rf "${WORK_DIR}"
}
trap cleanup EXIT

# The token goes to a private file so it never appears in a process argument list.
umask 077
printf '%s\n' "${XROAD_MIRROR_TOKEN}" >"${TOKEN_FILE}"

COUNT_FILE="${WORK_DIR}/count"

stream_has_no_token() {
  local -a status
  local count
  "$@" 2>/dev/null | grep -acF -f "${TOKEN_FILE}" >"${COUNT_FILE}"
  status=("${PIPESTATUS[@]}")
  if ((status[0] != 0 || status[1] > 1)); then
    log_error "      cannot read the content to scan"
    return 1
  fi
  count="$(<"${COUNT_FILE}")"
  [[ "${count}" =~ ^[0-9]+$ ]] && ((count == 0))
}

no_tls_override_in_final_filesystem() {
  local image="$1"
  CONTAINER="mirror-layer-leak-$$"
  docker create --name "${CONTAINER}" --entrypoint true "${image}" >/dev/null 2>&1 || {
    log_error "Cannot create a container from ${image}"
    return 1
  }
  local present=1
  if docker cp "${CONTAINER}:/${TLS_OVERRIDE_PATH}" - >/dev/null 2>&1; then present=0; fi
  docker rm -f -v "${CONTAINER}" >/dev/null 2>&1
  CONTAINER=""
  [[ ${present} -ne 0 ]]
}

scan_image() {
  local image="$1" dir config layer entry entries index total
  dir="$(mktemp -d "${WORK_DIR}/image-XXXXXX")"

  log_info "Scanning ${image}"
  if ! docker save "${image}" | tar -x -C "${dir}"; then
    fail "${image}: cannot save the image"
    return
  fi

  if ! entries="$(jq -er 'length' "${dir}/manifest.json" 2>/dev/null)" || ((entries == 0)); then
    fail "${image}: cannot read the image manifest"
    return
  fi

  for ((entry = 0; entry < entries; entry++)); do
    if ! config="${dir}/$(jq -er ".[${entry}].Config" "${dir}/manifest.json" 2>/dev/null)" ||
      ! total="$(jq -er ".[${entry}].Layers | length" "${dir}/manifest.json" 2>/dev/null)"; then
      fail "${image}: cannot read manifest entry ${entry}"
      continue
    fi
    if ((total == 0)); then
      fail "${image}: manifest entry ${entry} lists no layers"
      continue
    fi

    check "${image}: entry ${entry} image configuration has no token" stream_has_no_token cat -- "${config}"

    index=0
    while IFS= read -r layer; do
      index=$((index + 1))
      check "${image}: entry ${entry} layer ${index}/${total} ${layer##*/} has no token" stream_has_no_token tar -xOf "${dir}/${layer}"
    done < <(jq -r ".[${entry}].Layers[]" "${dir}/manifest.json")
  done

  check "${image}: final filesystem has no ${TLS_OVERRIDE_PATH}" no_tls_override_in_final_filesystem "${image}"
  rm -rf "${dir}"
}

if [[ -z "${LAYER_LEAK_CONTROL_ACTIVE:-}" ]]; then
  "${SCRIPT_DIR}/test-mirror-layer-leak-control.sh"
  control_exit=$?
  if [[ ${control_exit} -ne 0 ]]; then
    log_error "The negative control did not pass (exit ${control_exit}), no image was scanned"
    exit "${control_exit}"
  fi
fi

for image in "$@"; do
  scan_image "${image}"
done

if [[ "${FAILURES}" -ne 0 ]]; then
  log_error "${FAILURES} check(s) failed"
  exit 1
fi
log_success "All checks passed"
