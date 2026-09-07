#!/bin/bash

# Configuration
VM_NAME="xroad-lxd"
LXD_PORT=28443

function verify_lxd_version() {
  echo "Checking LXD version in VM..."
  limactl shell ${VM_NAME} sudo lxd --version
}

function check_lxd_port() {
  echo "Checking LXD port in VM..."
  limactl shell ${VM_NAME} sudo ss -tlpn | grep ${LXD_PORT}
}

function discover_lima_ip() {
  # Lima exposes the VM on socket_vmnet at a stable DHCP-leased IP (lima0).
  limactl shell ${VM_NAME} ip -4 -br addr show lima0 \
    | awk '{print $3}' | cut -d/ -f1
}

function configure_lxc_client() {
  echo "Configuring LXC client..."

  local lima_ip
  lima_ip=$(discover_lima_ip)
  if [[ -z "$lima_ip" ]]; then
    echo "ERROR: could not discover Lima socket_vmnet IP (lima0). Is the VM running?"
    exit 1
  fi
  echo "Using Lima socket_vmnet IP: $lima_ip"

  # Remove existing remote if it exists (but not default)
  if lxc remote list | grep -q xroad-lxd; then
    lxc remote switch local
    lxc remote remove xroad-lxd
  fi

  # Generate trust token
  echo "Generating trust token..."
  TOKEN=$(limactl shell ${VM_NAME} sudo lxc config trust add --name xroad-host --quiet)

  # Add remote with token
  echo "Adding remote with token..."
  lxc remote add xroad-lxd https://${lima_ip}:${LXD_PORT} \
    --token=${TOKEN}

  echo "Switching to remote..."
  lxc remote switch xroad-lxd
}

function verify_setup() {
  echo "Verifying setup..."
  lxc info
}

function main() {
  verify_lxd_version
  check_lxd_port
  configure_lxc_client
  verify_setup
}

main
