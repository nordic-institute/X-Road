#!/bin/bash
set -e
# shellcheck source=./_common.sh
source "${BASH_SOURCE%/*}/_common.sh"

OS="$(detect_os)"
log_info "Running preflight checks (os=${OS})"

fail=0

check_bin() {
  local bin="$1"
  local macos_hint="$2"
  local linux_hint="$3"
  if ! command -v "${bin}" >/dev/null 2>&1; then
    log_error "Missing: ${bin}"
    if [[ "${OS}" == "macos" ]]; then
      log_info "  Install: ${macos_hint}"
    else
      log_info "  Install: ${linux_hint}"
    fi
    fail=1
  else
    log_kv "  ${bin}" "$(command -v "${bin}")" 4 2
  fi
}

check_bin docker "brew install --cask docker" "apt install docker.io / dnf install docker-ce"
check_bin kubectl "brew install kubectl" "curl -LO https://dl.k8s.io/release/.../kubectl"
check_bin kind "brew install kind" "go install sigs.k8s.io/kind@latest"
check_bin helm "brew install helm" "curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash"
check_bin ansible-playbook "brew install ansible" "pip install ansible-core"
check_bin ansible-galaxy "brew install ansible" "pip install ansible-core"

if command -v docker >/dev/null 2>&1; then
  if ! docker info >/dev/null 2>&1; then
    log_error "Docker daemon not reachable (is Docker Desktop / docker service running?)"
    fail=1
  else
    log_kv "  docker daemon" "reachable" 4 2
    # Warn on low memory — kubeadm control-plane often hangs under 4GB.
    mem_bytes="$(docker info --format '{{.MemTotal}}' 2>/dev/null || echo 0)"
    mem_gib=$(( mem_bytes / 1024 / 1024 / 1024 ))
    if [[ "${mem_gib}" -gt 0 && "${mem_gib}" -lt 6 ]]; then
      log_warn "Docker memory limit is ${mem_gib} GiB. kind kubeadm-init often stalls under 6 GiB;"
      log_warn "raise it in Docker Desktop → Settings → Resources if bring-up fails."
    else
      log_kv "  docker memory" "${mem_gib} GiB" 4 2
    fi
  fi
fi

if command -v python3 >/dev/null 2>&1; then
  if ! python3 -c "import kubernetes" >/dev/null 2>&1; then
    log_error "Python 'kubernetes' client not importable"
    log_info "  Install: python3 -m pip install -r ${K8S_ROOT}/requirements.txt"
    fail=1
  else
    log_kv "  python kubernetes" "importable" 4 2
  fi
fi

if command -v ansible-galaxy >/dev/null 2>&1; then
  for coll in kubernetes.core community.crypto ansible.posix; do
    if ! ansible-galaxy collection list "${coll}" 2>/dev/null | grep -q "${coll}"; then
      log_warn "Ansible collection not installed: ${coll}"
      log_info "  Install: ansible-galaxy collection install -r ${K8S_ROOT}/requirements.yml --force"
      fail=1
    else
      log_kv "  collection ${coll}" "installed" 4 2
    fi
  done
fi

if [[ "${fail}" -ne 0 ]]; then
  log_error "Preflight checks failed."
  exit 1
fi

log_success "Preflight passed."
