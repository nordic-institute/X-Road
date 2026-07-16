#!/bin/bash
set -e
# shellcheck source=./_common.sh
source "${BASH_SOURCE%/*}/_common.sh"

ENV_NAME="dev"
RECREATE=false
SKIP_IMAGES=false
SKIP_FORWARD=false
SKIP_INIT=false
SKIP_PREFLIGHT=false
CUSTOM_INVENTORY=""
ANSIBLE_VERBOSITY="-vv"
EXTRA_ANSIBLE_ARGS=()

usage() {
  cat <<EOF
Usage: $0 [options]

Bring up the X-Road k8s environment: preflight → (optionally) build images →
ansible site.yml → port-forward → (optionally) hurl init-ss2.

Options:
  --env=dev|test|eks         Target environment (default: dev)
  --recreate                 Tear down environment before bringing it up
  --skip-images              Skip the Security Server image build
  --skip-forward             Skip kubectl port-forwards
  --skip-init                Skip init-ss2.sh hurl bootstrap
  --skip-preflight           Skip tooling preflight check
  --custom-inventory=PATH    Use a custom inventory path
  -v, -vv, -vvv, -vvvv       Ansible verbosity (default: -vv). Pass --quiet to disable.
  --quiet                    Run ansible-playbook without -v
  -e VAR=VAL                 Pass --extra-vars through to ansible-playbook
  -h, --help                 This help text.
EOF
  exit "${1:-1}"
}

parse_arguments() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --env=*) ENV_NAME="${1#*=}" ;;
      --recreate) RECREATE=true ;;
      --skip-images) SKIP_IMAGES=true ;;
      --skip-forward) SKIP_FORWARD=true ;;
      --skip-init) SKIP_INIT=true ;;
      --skip-preflight) SKIP_PREFLIGHT=true ;;
      --custom-inventory=*) CUSTOM_INVENTORY="${1#*=}" ;;
      -v|-vv|-vvv|-vvvv) ANSIBLE_VERBOSITY="$1" ;;
      --quiet) ANSIBLE_VERBOSITY="" ;;
      -e)
        if [[ -z "${2:-}" || "$2" == -* ]]; then
          log_error "-e requires a VAR=VAL argument"
          usage
        fi
        EXTRA_ANSIBLE_ARGS+=("-e" "$2")
        shift
        ;;
      -h|--help) usage 0 ;;
      *) log_error "Unknown parameter: $1"; usage ;;
    esac
    shift
  done

  validate_env_arg "${ENV_NAME}" || exit 1

  log_info "Execution plan:"
  log_kv "  Environment" "${ENV_NAME}" 2 5
  log_kv "  Recreate" "${RECREATE}" 2 5
  log_kv "  Skip images" "${SKIP_IMAGES}" 2 5
  log_kv "  Skip port-forward" "${SKIP_FORWARD}" 2 5
  log_kv "  Skip hurl init" "${SKIP_INIT}" 2 5
  log_kv "  Custom inventory" "${CUSTOM_INVENTORY:-<default>}" 2 5
}

handlePreflight() {
  [[ "${SKIP_PREFLIGHT}" == true ]] && { log_info "Skipping preflight"; return; }
  SKIP_INIT="${SKIP_INIT}" "${K8S_ROOT}/scripts/preflight.sh"
}

handleRecreate() {
  [[ "${RECREATE}" != true ]] && return
  log_info "Recreating environment — running delete first"
  local delete_args=(--env="${ENV_NAME}" --force)
  [[ -n "${CUSTOM_INVENTORY}" ]] && delete_args+=(--custom-inventory="${CUSTOM_INVENTORY}")
  "${K8S_ROOT}/scripts/delete-env.sh" "${delete_args[@]}"
}

handleBuildImages() {
  if [[ "${SKIP_IMAGES}" == true ]]; then
    log_info "Skipping image build"
    return
  fi
  if [[ "${ENV_NAME}" != "dev" ]]; then
    log_info "Skipping image build for env=${ENV_NAME} (uses artifactory images)"
    return
  fi
  log_info "Building X-Road container images into local registry (localhost:5555)"
  IMAGE_REGISTRY="localhost:5555" "${CORE_ROOT}/scripts/images/build-security-server.sh" --push
}

handleAnsible() {
  local inventory="${CUSTOM_INVENTORY:-$(inventory_path_for "${ENV_NAME}")}"

  log_info "Running ansible site.yml (inventory=${inventory})"
  log_info "Slow steps ahead: 'kind create cluster' pulls the node image (~1-2 min on first run)"
  log_info "and helm releases wait for pods to become Ready (several minutes)."
  log_kv "  Ansible verbosity" "${ANSIBLE_VERBOSITY:-<quiet>}" 2 5
  local verbosity_args=()
  [[ -n "${ANSIBLE_VERBOSITY}" ]] && verbosity_args+=("${ANSIBLE_VERBOSITY}")
  run_ansible_playbook playbooks/site.yml \
    -i "${inventory}" \
    -e "env_name=${ENV_NAME}" \
    --forks 10 \
    "${verbosity_args[@]}" \
    "${EXTRA_ANSIBLE_ARGS[@]}"
}

handlePortForward() {
  [[ "${SKIP_FORWARD}" == true ]] && { log_info "Skipping port-forward"; return; }
  "${K8S_ROOT}/scripts/port-forward.sh" --env="${ENV_NAME}"
}

handleInitialize() {
  [[ "${SKIP_INIT}" == true ]] && { log_info "Skipping hurl init"; return; }
  local init_script="${K8S_ROOT}/scripts/init-ss2.sh"
  if [[ ! -x "${init_script}" ]]; then
    log_warn "${init_script} not found or not executable; skipping"
    return
  fi
  log_info "Running init-ss2.sh (hurl bootstrap)"
  "${init_script}"
}

main() {
  parse_arguments "$@"

  local start
  start=$(date +%s)

  handlePreflight
  handleRecreate
  handleBuildImages
  handleAnsible
  handlePortForward
  handleInitialize

  local end
  end=$(date +%s)
  log_success "Environment '${ENV_NAME}' up in $(format_duration $((end - start)))"
}

main "$@"
