#!/usr/bin/env bash
# Layer-leak scan for images built with a package mirror.
#
# Saves each image and checks every layer and the image configuration:
#   - the mirror token string is absent;
#   - no apt or dnf mirror credential file exists.
# Then checks the final filesystem for the apt TLS-verification override that the mirror
# script writes and the cleanup script removes.
#
# Usage: XROAD_MIRROR_TOKEN=<token> ./test-mirror-layer-leak.sh <image> [<image>...]
#
# Exit codes: 0 clean, 1 a finding, 2 usage error. The token is never printed.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../../" && pwd)"
source "${ROOT_DIR}/scripts/lib/base-script.sh"

CREDENTIAL_FILE_PATTERN='^(\./)?(etc/apt/auth\.conf\.d/[^/]+|etc/yum\.repos\.d/00-mirror-[^/]*\.repo|root/\.netrc)$'
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
  if [[ -n "${CONTAINER}" ]]; then docker rm -f "${CONTAINER}" >/dev/null 2>&1; fi
  rm -rf "${WORK_DIR}"
}
trap cleanup EXIT

# The token goes to a private file so it never appears in a process argument list.
umask 077
printf '%s\n' "${XROAD_MIRROR_TOKEN}" >"${TOKEN_FILE}"

pass() { log_success "  ok: $1"; }
fail() { log_error "  FAILED: $1"; FAILURES=$((FAILURES + 1)); }

check() {
  local description="$1"
  shift
  if "$@"; then pass "${description}"; else fail "${description}"; fi
}

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

no_credential_file_in_layer() {
  local layer="$1" listing hits
  if ! listing="$(tar -tf "${layer}" 2>/dev/null)"; then
    log_error "      cannot list the layer"
    return 1
  fi
  hits="$(printf '%s\n' "${listing}" | grep -E "${CREDENTIAL_FILE_PATTERN}" | grep -v '/\.wh\.' || true)"
  if [[ -n "${hits}" ]]; then
    printf '%s\n' "${hits}" | sed 's/^/      credential file: /' >&2
    return 1
  fi
  return 0
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
  docker rm -f "${CONTAINER}" >/dev/null 2>&1
  CONTAINER=""
  [[ ${present} -ne 0 ]]
}

scan_image() {
  local image="$1" dir config layer index=0 total
  dir="${WORK_DIR}/image-${RANDOM}"
  mkdir -p "${dir}"

  log_info "Scanning ${image}"
  if ! docker save "${image}" | tar -x -C "${dir}"; then
    fail "${image}: cannot save the image"
    return
  fi

  if ! config="${dir}/$(jq -er '.[0].Config' "${dir}/manifest.json" 2>/dev/null)" ||
    ! total="$(jq -er '.[0].Layers | length' "${dir}/manifest.json" 2>/dev/null)"; then
    fail "${image}: cannot read the image manifest"
    return
  fi
  if ((total == 0)); then
    fail "${image}: the manifest lists no layers"
    return
  fi

  check "${image}: image configuration has no token" stream_has_no_token cat -- "${config}"

  while IFS= read -r layer; do
    index=$((index + 1))
    check "${image}: layer ${index}/${total} ${layer##*/} has no token" stream_has_no_token tar -xOf "${dir}/${layer}"
    check "${image}: layer ${index}/${total} ${layer##*/} has no credential file" no_credential_file_in_layer "${dir}/${layer}"
  done < <(jq -r '.[0].Layers[]' "${dir}/manifest.json")

  check "${image}: final filesystem has no ${TLS_OVERRIDE_PATH}" no_tls_override_in_final_filesystem "${image}"
  rm -rf "${dir}"
}

for image in "$@"; do
  scan_image "${image}"
done

if [[ "${FAILURES}" -ne 0 ]]; then
  log_error "${FAILURES} check(s) failed"
  exit 1
fi
log_success "All checks passed"
