#!/bin/bash
# Shared helpers for X-Road k8s Ansible scripts.
# Source from other scripts; not meant to be executed directly.

if [[ -z "${BASH_VERSION}" ]]; then
  echo "Error: This script requires Bash." >&2
  exit 1
fi

# scripts/env-k8s/_common.sh -> scripts/ -> repo root (CORE_ROOT)
CORE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
K8S_ROOT="${CORE_ROOT}/development/k8s"

# shellcheck source=/dev/null
source "${CORE_ROOT}/scripts/lib/base-script.sh"

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

_file_sha256() {
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  elif command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    echo "nohash"
  fi
}

# Provision the k8s Python venv and its pip + Ansible collection dependencies.
# Idempotent: the pip and galaxy installs are each skipped when their
# requirements file is unchanged since the last successful install (tracked by
# a sha marker under STATE_DIR). Activates the venv into the calling shell so
# subsequent ansible-playbook calls resolve to it.
ensure_k8s_deps() {
  local req_txt="${K8S_ROOT}/requirements.txt"
  local req_yml="${K8S_ROOT}/requirements.yml"

  if ! command -v python3 >/dev/null 2>&1; then
    log_error "python3 not found on PATH; cannot create the k8s venv"
    log_info "Install python3 (macOS: brew install python), then re-run"
    return 1
  fi

  if [[ ! -f "${K8S_ROOT}/.venv/bin/activate" ]]; then
    log_info "Creating Python venv at ${K8S_ROOT}/.venv"
    python3 -m venv "${K8S_ROOT}/.venv"
  fi
  if [[ "${VIRTUAL_ENV:-}" != "${K8S_ROOT}/.venv" ]]; then
    [[ -n "${VIRTUAL_ENV:-}" ]] && type deactivate >/dev/null 2>&1 && deactivate
    # shellcheck source=/dev/null
    source "${K8S_ROOT}/.venv/bin/activate"
  fi

  if [[ -f "${req_txt}" ]]; then
    local pip_cur pip_prev
    pip_cur="$(_file_sha256 "${req_txt}")"
    pip_prev="$(cat "${STATE_DIR}/pip.sha" 2>/dev/null || echo "")"
    if [[ "${pip_cur}" != "${pip_prev}" ]]; then
      log_info "Installing Python requirements (requirements.txt)"
      python -m pip install --upgrade pip >/dev/null
      python -m pip install -r "${req_txt}"
      echo "${pip_cur}" > "${STATE_DIR}/pip.sha"
    else
      log_kv "  python requirements" "up to date" 4 2
    fi
  fi

  if [[ -f "${req_yml}" ]]; then
    local gal_cur gal_prev
    gal_cur="$(_file_sha256 "${req_yml}")"
    gal_prev="$(cat "${STATE_DIR}/galaxy.sha" 2>/dev/null || echo "")"
    if [[ "${gal_cur}" != "${gal_prev}" ]]; then
      log_info "Installing Ansible collections (requirements.yml)"
      ansible-galaxy collection install -r "${req_yml}"
      echo "${gal_cur}" > "${STATE_DIR}/galaxy.sha"
    else
      log_kv "  ansible collections" "up to date" 4 2
    fi
  fi
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
