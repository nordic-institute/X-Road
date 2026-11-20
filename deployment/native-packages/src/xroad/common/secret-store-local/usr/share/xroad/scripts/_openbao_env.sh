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

# require_env_name KEY VALUE: stops the calling script unless VALUE is usable as a database name, user or
# schema: 1 to 128 characters of letters, digits and _ . @ -, starting with a letter, digit or _. That keeps a
# value from being read as a connection string, a psql option or a server option. The message names KEY and
# never prints the value.
require_env_name() {
  local LC_ALL=C
  local key="$1"
  local value="$2"
  local pattern='^[A-Za-z0-9_][A-Za-z0-9_.@-]*$'

  if [[ ${#value} -gt 128 || ! $value =~ $pattern ]]; then
    echo >&2 "Invalid ${key} in the OpenBao environment file: expected 1 to 128 characters from letters, digits and _ . @ -, starting with a letter, digit or _"
    exit 1
  fi
}
