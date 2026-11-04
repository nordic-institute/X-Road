#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

VM_NAME="xroad-lxd"
RECREATE=false
MEMORY_GIB=""
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
      if [ -n "$MEMORY_GIB" ]; then
        echo "--memory has no effect on an existing VM. To change its memory, run:"
        echo "  limactl stop $VM_NAME && limactl edit $VM_NAME --memory $MEMORY_GIB && limactl start $VM_NAME"
        echo "or recreate it (deletes all containers): $0 --recreate --memory=$MEMORY_GIB"
        MEMORY_GIB=""
      fi
    fi
  fi
}

function create_and_start_vm() {
  echo "Creating and starting Lima VM $VM_NAME..."

  if [ ! -f "$LIMA_CONFIG" ]; then
    echo "Configuration file not found: $LIMA_CONFIG"
    exit 1
  fi

  local memory_args=()
  if [ -n "$MEMORY_GIB" ]; then
    memory_args=(--memory "$MEMORY_GIB")
  fi
  limactl start --name=${VM_NAME} --tty=false "${memory_args[@]}" "$LIMA_CONFIG"

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

function usage() {
  echo "Usage: $0 [options]"
  echo "Options:"
  echo " --recreate        Delete and recreate the VM $VM_NAME (deletes all containers)"
  echo " --memory=<GiB>    VM memory in GiB when the VM is created; default is the value in config/lima.yaml"
  echo " -h, --help        This help text."
  exit 0
}
function parse_arguments() {
  while [[ "$#" -gt 0 ]]; do
    case $1 in
    --recreate) RECREATE=true ;;
    --memory=*)
      MEMORY_GIB="${1#*=}"
      if [[ ! "$MEMORY_GIB" =~ ^[1-9][0-9]*$ ]]; then
        echo "Invalid --memory value: $MEMORY_GIB (expected a whole number of GiB, for example --memory=20)"
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
}

function main() {
  parse_arguments "$@"
  check_macos
  check_prerequisites
  setup_socket_vmnet
  handle_existing_vm
  create_and_start_vm
  setup_ssh_config

  echo "Proceeding with LXD setup..."
  source "$SCRIPT_DIR/setup-lxc.sh"
}

main "$@"
