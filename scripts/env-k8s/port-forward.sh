#!/bin/bash
set -e
# shellcheck source=./_common.sh
source "${BASH_SOURCE%/*}/_common.sh"

ENV_NAME="dev"

usage() {
  cat <<EOF
Usage: $0 [--env=dev|test|eks|e2e]

Start async kubectl port-forwards for the given environment. Writes child PIDs
to .state/port-forwards-<env>.pid so delete-env.sh can reap them.
EOF
  exit "${1:-1}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env=*) ENV_NAME="${1#*=}" ;;
    -h|--help) usage 0 ;;
    *) log_error "Unknown parameter: $1"; usage ;;
  esac
  shift
done
validate_env_arg "${ENV_NAME}" || exit 1

run_ansible_playbook playbooks/port_forward.yml \
  -i "$(inventory_path_for "${ENV_NAME}")" \
  -e "env_name=${ENV_NAME}" \
  -e "state_dir=${STATE_DIR}"
