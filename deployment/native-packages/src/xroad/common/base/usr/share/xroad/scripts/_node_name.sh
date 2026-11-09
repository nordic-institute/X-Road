#!/bin/bash
# Node name check shared by the Central Server installer, backup and restore scripts.
# The rule is the same as in the services (NodeName).
#
# Usage: source this file once at the top of the script, then call
#   check_node_name <value> <description of where the value comes from>
#   read_node_ini_node_name [node.ini path, default /etc/xroad/conf.d/node.ini]
#
# A blank value is valid and means "no node name": callers add no suffix and do
# not substitute a default name.
# check_node_name returns 0 for a valid or blank value. Otherwise it prints the rule to
# stderr and returns 1.
# read_node_ini_node_name prints the name key of the node section from the file
# (nothing when the file or the key is absent) and returns 0 when the value is valid or blank.
# It returns 1 with a message on stderr for an invalid value and for a file that cannot be
# read or parsed.

NODE_NAME_RULE="must match ^[A-Za-z0-9][A-Za-z0-9_-]{0,62}\$"

check_node_name () {
  local value="$1"
  local source_description="$2"
  local LC_ALL=C
  local valid_name='^[A-Za-z0-9][A-Za-z0-9_-]{0,62}$'

  if [[ "$value" =~ ^[[:space:]]*$ ]] ; then
    return 0
  fi

  if ! [[ "$value" =~ $valid_name ]] ; then
    echo >&2 "Invalid node name '${value}' in ${source_description}: it ${NODE_NAME_RULE}"
    return 1
  fi
  return 0
}

read_node_ini_node_name () {
  local file="${1:-/etc/xroad/conf.d/node.ini}"
  local value=""

  if [[ -e "$file" ]] ; then
    if ! crudini --get "$file" >/dev/null 2>&1 ; then
      echo >&2 "Cannot read the node name from ${file}: the file is unreadable or malformed"
      return 1
    fi
    value="$(crudini --get "$file" node name 2>/dev/null)" || value=""
  fi
  check_node_name "$value" "[node] name in ${file}" || return 1
  printf '%s\n' "$value"
}

# vim: ts=2 sw=2 sts=2 et filetype=sh
