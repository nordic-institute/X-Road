#!/bin/bash

# The OpenBao environment file is writable by the OpenBao user, so a script that runs as root reads
# it as data with this function and never executes it as shell code.

# read_env_value FILE KEY: prints the value of the last KEY=value line of FILE, split on the first
# "=". A missing key gives an empty value. A trailing carriage return is dropped.
read_env_value() {
  local file="$1"
  local key="$2"
  local line
  local value=""

  while IFS= read -r line || [ -n "$line" ]; do
    line=${line%$'\r'}
    case "$line" in
      "$key"=*) value=${line#*=} ;;
    esac
  done <"$file"

  printf '%s' "$value"
}
