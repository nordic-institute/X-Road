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

function setup_socket_vmnet() {
  echo "Checking socket_vmnet setup..."
  if ! brew list socket_vmnet &>/dev/null; then
    echo "Installing socket_vmnet..."
    brew install socket_vmnet
  fi

  # Ensure service is started
  echo "Ensuring socket_vmnet service is started (may ask for sudo)..."
  sudo brew services start socket_vmnet || true

  # Symlink setup
  SOCKET_VMNET_SRC="$(brew --prefix socket_vmnet)/bin/socket_vmnet"
  SOCKET_VMNET_DST="/opt/socket_vmnet/bin/socket_vmnet"

  if [ -f "$SOCKET_VMNET_SRC" ]; then
    # Check if link exists and points to correct src
    if [ ! -L "$SOCKET_VMNET_DST" ] || [ "$(readlink "$SOCKET_VMNET_DST")" != "$SOCKET_VMNET_SRC" ]; then
      echo "Linking socket_vmnet to $SOCKET_VMNET_DST (requires sudo)..."
      sudo mkdir -p "$(dirname "$SOCKET_VMNET_DST")"
      sudo ln -sf "$SOCKET_VMNET_SRC" "$SOCKET_VMNET_DST"
    fi
  else
    echo "ERROR: socket_vmnet binary not found at $SOCKET_VMNET_SRC"
    exit 1
  fi
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
  check_prerequisites
  setup_socket_vmnet
  handle_existing_vm
  create_and_start_vm
  setup_ssh_config

  echo "Proceeding with LXD setup..."
  source "$SCRIPT_DIR/setup-lxc.sh"
}

main "$@"
