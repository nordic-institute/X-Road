#!/bin/bash
set -e

# Setup X-Road admin user with appropriate groups
#
# This script creates X-Road groups and adds the specified admin user to them.
# It's used by various X-Road component installation scripts to ensure consistent
# user and group configuration.

# Define all X-Road groups
groups="xroad-security-officer xroad-registration-officer xroad-service-administrator xroad-system-administrator xroad-securityserver-observer"

# Define groups that are allowed on ss-cluster secondary nodes
secondary_groups="xroad-security-officer xroad-securityserver-observer"

log() {
  echo >&2 "$@"
}

get_node_type() {
  echo "${1:-$(crudini --get '/etc/xroad/conf.d/node.ini' node type 2>/dev/null || echo standalone)}"
}

# Setup X-Road admin user with groups
# Arguments:
#   $1 - Admin username
#   $2 - Node type (optional, will be auto-detected from /etc/xroad/conf.d/node.ini if not provided)
setup_xroad_admin_user() {
  local username="$1"

  if [ -z "$username" ]; then
    log "WARNING: X-Road admin user not configured."
    return 1
  fi

  local node_type
  node_type="$(get_node_type "$2")"

  local groupnames=""
  if [[ "$node_type" == "secondary" || "$node_type" == "slave" ]]; then
    log "Cluster secondary node detected, configuring secondary node compatible groups"
    groupnames=$secondary_groups
  else
    log "Configuring groups"
    groupnames=$groups
  fi

  # Get current user's groups
  usergroups=" $(id -Gn "$username") "

  # Create groups and add user to them
  for groupname in $groupnames; do
    if ! getent group "$groupname" > /dev/null; then
      groupadd --system "$groupname" || true
    fi
    if [[ $usergroups != *" $groupname "* ]]; then
      usermod -a -G "$groupname" "$username" || true
    fi
  done

  log "X-Road admin user '$username' configured successfully"
}

# If script is executed directly (not sourced), run the setup function
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  if [ $# -lt 1 ]; then
    echo "Usage: $0 <username> [node_type]"
    echo "  username    - The admin user to configure"
    echo "  node_type   - Optional: 'standalone' (default) or 'secondary'"
    exit 1
  fi

  setup_xroad_admin_user "$@"
fi