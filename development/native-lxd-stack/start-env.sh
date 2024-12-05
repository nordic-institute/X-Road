#!/bin/bash
set -e
source ./../.scripts/base-script.sh

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

function handleRecreate() {
  if [ "$RECREATE" = true ]; then
    ./scripts/delete-env.sh
  fi
}

function handleAnsible() {
  ansible-playbook -i "$INVENTORY_PATH" \
    ../../ansible/xroad_dev.yml \
    --forks 10 \
    --skip-tags compile,build-packages \
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

    ./../../src/build_packages.sh -r noble $build_args
  fi
}

function handleInitialize() {
  if [ "$SKIP_INITIALIZE" = false ]; then
    lxc exec xrd-hurl -- bash -c "cd /opt/hurl && ./run-hurl.sh scenario/setup.hurl"

    #configure edc keys on CS
    lxc file push ./scripts/config-ds-cs-ss.sh xrd-cs/root/
    lxc exec xrd-cs -- bash /root/config-ds-cs-ss.sh cs DEV:COM:1234

    #configure edc keys on SS
    #ss0
    lxc file push ./scripts/config-ds-cs-ss.sh xrd-ss0/root/
    lxc exec xrd-ss0 -- bash /root/config-ds-cs-ss.sh ss DEV:COM:1234

    #ss1
    lxc file push ./scripts/config-ds-cs-ss.sh xrd-ss1/root/
    lxc exec xrd-ss1 -- bash /root/config-ds-cs-ss.sh ss DEV:COM:4321
    #todo: remove the init file after init from containers?

    #provision ds membership
    lxc exec xrd-hurl -- bash -c "cd /opt/hurl && ./run-hurl.sh scenario/provision-ds-membership.hurl"

    #trigger DS assets update job
    lxc exec xrd-hurl -- bash -c "cd /opt/hurl && ./run-hurl.sh scenario/trigger-ds-asset-update.hurl"

  fi
}

function main() {
  parse_arguments "$@"

  handleRecreate
  handleBuild
  handleAnsible
  handleInitialize
}

main "$@"
