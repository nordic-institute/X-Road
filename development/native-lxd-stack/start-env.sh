#!/bin/bash
set -e
source "${BASH_SOURCE%/*}/../../.scripts/base-script.sh"

RECREATE=false
SKIP_COMPILE=false
SKIP_TESTS=false
SKIP_BUILD=false
SKIP_INITIALIZE=false
INVENTORY_PATH="config/ansible_hosts.txt"

function parse_arguments() {
  while [[ "$#" -gt 0 ]]; do
    case $1 in
    --recreate) RECREATE=true ;;
    --skip-compile) SKIP_COMPILE=true ;;
    --skip-tests) SKIP_TESTS=true ;;
    --skip-build) SKIP_BUILD=true ;;
    --skip-init) SKIP_INITIALIZE=true ;;
    --custom-inventory=*)
      INVENTORY_PATH="${1#*=}"
      if [ ! -f "$INVENTORY_PATH" ]; then
        log_error "Inventory file not found: $INVENTORY_PATH"
        exit 1
      fi
      ;;
    -h | --help) usage ;;
    *)
      echo "Unknown parameter: $1"
      exit 1
      ;;
    esac
    shift
  done

  # Validate flags
  if [ "$SKIP_BUILD" = true ] && [ "$SKIP_COMPILE" = true ]; then
    log_error "--skip-build already includes compile skipping. Don't use both flags."
    exit 1
  fi

  log_info "Execution plan:"
  log_kv "Recreate containers" "$RECREATE" 2 5
  log_kv "Skip compile" "$SKIP_COMPILE" 2 5
  log_kv "Skip tests" "$SKIP_TESTS" 2 5
  log_kv "Skip build" "$SKIP_BUILD" 2 5
  log_kv "Skip Initialize with Hurl" "$SKIP_INITIALIZE" 2 5
  log_kv "Using inventory" "$INVENTORY_PATH" 2 5
}

usage() {
  echo "Usage: $0 [options]"
  echo "Options:"
  echo " --skip-compile              Skip compilation phase"
  echo " --skip-tests               Skip test execution"
  echo " --skip-build               Skip both compilation and package building"
  echo " --recreate                Recreate containers"
  echo " --initialize              Initialize with Hurl"
  echo " --custom-inventory=PATH   Use custom inventory file instead of default"
  echo " -h, --help                This help text."
  exit 1
}

function handlePrepare() {
  if limactl list | grep -q '^xroad-lxd'; then
    # Check current status
    current_status=$(limactl list | grep '^xroad-lxd' | awk '{print $2}')
    
    if [ "$current_status" = "Running" ]; then
        log_info "Lima instance xroad-lxd is already running"
    elif [ "$current_status" = "Stopped" ]; then
        log_info "Starting lima instance xroad-lxd"
        limactl start xroad-lxd
        
        # Verify that the instance is running
        if limactl list | grep '^xroad-lxd' | awk '{print $2}' | grep -q 'Running'; then
            log_info "Lima instance xroad-lxd started successfully"
        else
            log_error "Failed to start lima instance xroad-lxd"
            exit 1
        fi
    else
        log_info "Lima instance xroad-lxd has status: $current_status - waiting for it to be ready"
        # Wait for the instance to reach a stable state
        sleep 5
        handlePrepare  # Recursive call to check again
    fi
  else
    log_error "Lima instance xroad-lxd not found. Please create it first."
    exit 1
  fi
}

function handleRecreate() {
  if [ "$RECREATE" = true ]; then
    ./scripts/delete-env.sh
  fi
}

function handleAnsible() {

  ANSIBLE_CONFIG="config/ansible.cfg" ansible-playbook -i "$INVENTORY_PATH" \
    ../../development/ansible/xroad_dev.yml \
    --forks 10 \
    --skip-tags compile,build-packages \
    -e onMacOs=$onMacOs \
    -vv
}

function handleBuild() {
  local build_args=""
  if [ "$SKIP_BUILD" = false ]; then

    if [ "$SKIP_COMPILE" = true ]; then
      build_args+="--package-only "
    fi
    if [ "$SKIP_TESTS" = true ]; then
      build_args+="--skip-tests "
    fi

    ./../../src/build_packages.sh -r noble -r rpm-el9 $build_args
  fi
}

function handleInitialize() {
  if [ "$SKIP_INITIALIZE" = false ]; then
    lxc exec xrd-hurl -- bash -c "cd /opt/hurl && ./run-hurl.sh"
  fi
}

function main() {
  parse_arguments "$@"

  local onMacOs="no"
  if [[ $(uname) == "Darwin" ]]; then
    onMacOs="yes"
    # lima is only for MacOS
    handlePrepare
  fi
  handleRecreate
  handleBuild
  handleAnsible
  handleInitialize
}

main "$@"
