#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

VM_NAME="xroad-lxd"
RECREATE=false
LIMA_CONFIG="$PROJECT_ROOT/config/lima.yaml"

function check_macos() {
  if [[ $(uname) != "Darwin" ]]; then
    echo "This script only works on MacOS"
    exit 1
  fi
}

function check_prerequisites() {
  if ! command -v brew &>/dev/null; then
    echo "Homebrew is not installed"
    exit 1
  fi

  if ! command -v limactl &>/dev/null; then
    echo "Installing lima..."
    brew install lima
  fi

  if ! command -v lxc &>/dev/null; then
    echo "Installing lxc..."
    brew install lxc
  fi
}

function handle_existing_vm() {
  if limactl list | grep -q "$VM_NAME"; then
    if [ "$RECREATE" = true ]; then
      echo "Removing existing VM $VM_NAME..."
      limactl stop $VM_NAME
      limactl delete $VM_NAME
    else
      echo "VM $VM_NAME already exists"
    fi
  fi
}

function create_and_start_vm() {
  echo "Creating and starting Lima VM $VM_NAME..."

  if [ ! -f "$LIMA_CONFIG" ]; then
    echo "Configuration file not found: $LIMA_CONFIG"
    exit 1
  fi

  limactl start --name=${VM_NAME} --tty=false "$LIMA_CONFIG"

  # Check if VM exists and is running
  if ! limactl list | grep -q "$VM_NAME"; then
    echo "Failed to start VM $VM_NAME"
    exit 1
  fi
}

function setup_ssh_config() {
  echo "Enabling ssh.config access..."
  chmod 644 ~/.lima/xroad-lxd/ssh.config
}

function parse_arguments() {
  while [[ "$#" -gt 0 ]]; do
    case $1 in
    --recreate) RECREATE=true ;;
    *)
      echo "Unknown parameter: $1"
      exit 1
      ;;
    esac
    shift
  done
}

function main() {
  parse_arguments "$@"
  check_macos
  check_prerequisites
  handle_existing_vm
  create_and_start_vm
  setup_ssh_config

  echo "Proceeding with LXD setup..."
  source "$SCRIPT_DIR/setup-lxc.sh"
}

main "$@"
