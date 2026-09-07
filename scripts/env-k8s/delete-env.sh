#!/bin/bash
set -e
# shellcheck source=./_common.sh
source "${BASH_SOURCE%/*}/_common.sh"

ENV_NAME="dev"
KEEP_CLUSTER=false
FORCE=false
CUSTOM_INVENTORY=""

usage() {
  cat <<EOF
Usage: $0 [options]

Tear down the X-Road k8s environment: reap port-forwards → ansible teardown.yml
→ (optionally) kind delete cluster.

Options:
  --env=dev|test|eks|e2e     Target environment (default: dev)
  --keep-cluster             Uninstall helm releases but keep KinD cluster
  --force                    Do not prompt before destroying
  --custom-inventory=PATH    Use a custom inventory path
  -h, --help                 This help text.
EOF
  exit "${1:-1}"
}

parse_arguments() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --env=*) ENV_NAME="${1#*=}" ;;
      --keep-cluster) KEEP_CLUSTER=true ;;
      --force) FORCE=true ;;
      --custom-inventory=*) CUSTOM_INVENTORY="${1#*=}" ;;
      -h|--help) usage 0 ;;
      *) log_error "Unknown parameter: $1"; usage ;;
    esac
    shift
  done
  validate_env_arg "${ENV_NAME}" || exit 1
}

confirm() {
  [[ "${FORCE}" == true ]] && return 0
  read -r -p "Destroy environment '${ENV_NAME}'? [y/N] " reply
  [[ "${reply}" =~ ^[Yy]$ ]]
}

reapPortForwards() {
  local pidfile="${STATE_DIR}/port-forwards-${ENV_NAME}.pid"
  if [[ -f "${pidfile}" ]]; then
    log_info "Reaping port-forward PIDs from ${pidfile}"
    while IFS= read -r pid; do
      [[ "${pid}" =~ ^[0-9]+$ ]] || continue
      local cmd
      cmd=$(ps -o args= -p "${pid}" 2>/dev/null || true)
      # Only signal the PID if it still maps to a kubectl port-forward —
      # avoids nuking unrelated processes if the OS recycled the PID.
      if [[ -n "${cmd}" && "${cmd}" == *kubectl* && "${cmd}" == *port-forward* ]]; then
        kill "${pid}" 2>/dev/null || true
      fi
    done < "${pidfile}"
    rm -f "${pidfile}"
  fi
}

runTeardown() {
  local inventory="${CUSTOM_INVENTORY:-$(inventory_path_for "${ENV_NAME}")}"
  run_ansible_playbook playbooks/teardown.yml \
    -i "${inventory}" \
    -e "env_name=${ENV_NAME}" \
    -e "keep_cluster=${KEEP_CLUSTER}"
}

main() {
  parse_arguments "$@"
  if ! confirm; then
    log_info "Aborted."
    exit 0
  fi
  reapPortForwards
  runTeardown
  log_success "Environment '${ENV_NAME}' torn down (keep_cluster=${KEEP_CLUSTER})"
}

main "$@"
