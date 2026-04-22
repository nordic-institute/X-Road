#!/bin/bash
# Shared helpers for X-Road k8s Ansible scripts.
# Source from other scripts; not meant to be executed directly.

if [[ -z "${BASH_VERSION}" ]]; then
  echo "Error: This script requires Bash." >&2
  exit 1
fi

# scripts/_common.sh -> k8s/ -> development/ -> core/
K8S_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CORE_ROOT="$(cd "${K8S_ROOT}/../.." && pwd)"

# shellcheck source=/dev/null
source "${CORE_ROOT}/.scripts/base-script.sh"

STATE_DIR="${K8S_ROOT}/.state"
mkdir -p "${STATE_DIR}"

# Auto-activate local venv if present. Re-activate when parent shell has a
# stale VIRTUAL_ENV pointing elsewhere (e.g. leftover from an older path).
if [[ -f "${K8S_ROOT}/.venv/bin/activate" && "${VIRTUAL_ENV:-}" != "${K8S_ROOT}/.venv" ]]; then
  [[ -n "${VIRTUAL_ENV:-}" ]] && type deactivate >/dev/null 2>&1 && deactivate
  # shellcheck source=/dev/null
  source "${K8S_ROOT}/.venv/bin/activate"
fi

detect_os() {
  case "$(uname)" in
    Darwin) echo "macos" ;;
    Linux) echo "linux" ;;
    *) echo "unknown" ;;
  esac
}

require_bin() {
  local bin="$1"
  local install_hint="${2:-}"
  if ! command -v "${bin}" >/dev/null 2>&1; then
    log_error "Required binary not found: ${bin}"
    [[ -n "${install_hint}" ]] && log_info "Install: ${install_hint}"
    return 1
  fi
}

validate_env_arg() {
  local env_name="$1"
  case "${env_name}" in
    dev|test|eks) return 0 ;;
    *)
      log_error "Unknown environment: ${env_name} (expected dev|test|eks)"
      return 1
      ;;
  esac
}

inventory_path_for() {
  echo "${K8S_ROOT}/inventory/$1"
}

run_ansible_playbook() {
  local playbook="$1"
  shift
  (
    cd "${K8S_ROOT}"
    ANSIBLE_CONFIG="${K8S_ROOT}/ansible.cfg" \
      ansible-playbook "${playbook}" "$@"
  )
}
