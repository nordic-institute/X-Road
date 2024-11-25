#!/bin/bash

# Configuration
VM_NAME="xroad-lxd"
LXD_PORT=28443
TRUST_PASSWORD="secret"

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

  # Add remote with certificate acceptance
  lxc remote add xroad-lxd https://127.0.0.1:${LXD_PORT} \
    --password=${TRUST_PASSWORD} \
    --accept-certificate

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
