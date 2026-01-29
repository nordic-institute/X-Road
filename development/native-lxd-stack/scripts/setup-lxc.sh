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

function configure_lxc_client() {
  echo "Configuring LXC client..."
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
  lxc remote add xroad-lxd https://127.0.0.1:${LXD_PORT} \
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
