#!/bin/bash

# Configuration
VM_NAME="xroad-lxd"
LXD_PORT=28443
REMOTE_NAME="xroad-lxd"

function verify_lxd_version() {
  echo "Checking LXD version in VM..."
  limactl shell ${VM_NAME} sudo lxd --version
}

function check_lxd_port() {
  echo "Checking LXD port in VM..."
  limactl shell ${VM_NAME} sudo ss -tlpn | grep ${LXD_PORT}
}

function remote_url() {
  lxc remote list --format csv \
    | awk -F, -v name="${REMOTE_NAME}" '$1 == name || $1 == name " (current)" {print $2}'
}

function remote_is_usable() {
  lxc info "${REMOTE_NAME}:" >/dev/null 2>&1
}

function add_remote() {
  local api_url=$1

  echo "Generating trust token..."
  TOKEN=$(limactl shell ${VM_NAME} sudo lxc config trust add --name xroad-host --quiet)

  echo "Adding remote with token..."
  lxc remote add "${REMOTE_NAME}" "${api_url}" --token="${TOKEN}"
}

function configure_lxc_client() {
  echo "Configuring LXC client..."

  # The API is reached through lima's loopback port forward (lima.yaml), not the socket_vmnet IP.
  local api_url="https://127.0.0.1:${LXD_PORT}"
  local current_url
  current_url=$(remote_url)

  if [[ -n "$current_url" && "${current_url%/}" != "$api_url" ]]; then
    echo "Pointing remote ${REMOTE_NAME} at ${api_url} (was ${current_url})..."
    lxc remote set-url ${REMOTE_NAME} ${api_url}
  fi

  if [[ -n "$current_url" ]] && remote_is_usable; then
    echo "Remote ${REMOTE_NAME} at ${api_url} is reachable"
  else
    if [[ -n "$current_url" ]]; then
      lxc remote switch local
      lxc remote remove ${REMOTE_NAME}
    fi
    add_remote ${api_url}
  fi

  echo "Switching to remote..."
  lxc remote switch ${REMOTE_NAME}
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
