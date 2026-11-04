#!/bin/bash
# Node name check shared by the Central Server installer, backup and restore scripts.
# The rule is the same as in the services (NodeNameResolver).
#
# Usage: source this file, then call
#   check_node_name <value> <description of where the value comes from>
#
# A blank value is valid and means "no node name": callers add no suffix and do
# not substitute a default name.
# Returns 0 for a valid or blank value. Otherwise prints the rule to stderr and returns 1.

NODE_NAME_RULE="must match ^[A-Za-z0-9][A-Za-z0-9_-]{0,62}\$ and must not be a value that YAML reads as a non-string, such as true, on, yes, null, 0123, 1_0 or 1e3"

check_node_name () {
  local value="$1"
  local source_description="$2"
  local LC_ALL=C

  if [[ "$value" =~ ^[[:space:]]*$ ]] ; then
    return 0
  fi

  local yaml_non_string_number='^(0[0-9_]+|0[xX][0-9A-Fa-f_]+|0[bB][01_]+|0[oO][0-7_]+|[0-9]+_[0-9_]*|[0-9][0-9_]*[eE]-?[0-9]+|[0-9]{4}-[0-9]{2}-[0-9]{2})$'
  local valid_name='^[A-Za-z0-9][A-Za-z0-9_-]{0,62}$'
  local valid=true

  if ! [[ "$value" =~ $valid_name ]] ; then
    valid=false
  else
    case "$value" in
      true|True|TRUE|false|False|FALSE|yes|Yes|YES|no|No|NO|on|On|ON|off|Off|OFF|null|Null|NULL)
        valid=false
        ;;
      *)
        if [[ "$value" =~ $yaml_non_string_number ]] ; then
          valid=false
        fi
        ;;
    esac
  fi

  if [[ $valid != true ]] ; then
    echo >&2 "Invalid node name '${value}' in ${source_description}: it ${NODE_NAME_RULE}"
    return 1
  fi
  return 0
}

# vim: ts=2 sw=2 sts=2 et filetype=sh
