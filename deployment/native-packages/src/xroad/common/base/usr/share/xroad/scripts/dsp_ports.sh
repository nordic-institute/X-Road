#!/bin/bash
#
# Value checks for the DID port and the DSP port of a Security Server, and the database rows
# that hold a port chosen at installation.
#
# Usage (when sourced):
#   . dsp_ports.sh
#   normalize_port "<value>"            # Prints the port without leading zeros; returns 1 when it is not a valid port
#   ports_differ "<did port>" "<dsp port>"   # Returns 0 when the two valid ports are different numbers
#   store_dsp_port_rows "<did port>" "<dsp port>"   # Stores the ports that differ from the default, keeps existing rows
#   $DSP_DEFAULT_DID_PORT, $DSP_DEFAULT_DSP_PORT   # Ports used when the database has no row
#

DSP_DEFAULT_DID_PORT=7183
DSP_DEFAULT_DSP_PORT=8183
DSP_DID_PORT_KEY=xroad.dsp.did-port
DSP_DSP_PORT_KEY=xroad.dsp.dsp-port

dsp_ports_log() { echo >&2 "$@"; }

# Prints the port number without leading zeros (08183 is 8183).
# Accepts digits only, in the range 1024-65535. Returns 1 and prints nothing otherwise.
# Arguments: $1 - port value
normalize_port() {
  local value="$1"
  [[ "$value" =~ ^[0-9]+$ ]] || return 1
  value="${value#"${value%%[!0]*}"}"
  [[ -n "$value" && ${#value} -le 5 ]] || return 1
  (( value >= 1024 && value <= 65535 )) || return 1
  printf '%s\n' "$value"
}

# Returns 0 when both values are valid ports and they are different numbers.
# Arguments: $1 - DID port, $2 - DSP port
ports_differ() {
  local did dsp
  did=$(normalize_port "$1") || return 1
  dsp=$(normalize_port "$2") || return 1
  [[ "$did" != "$dsp" ]]
}

# Prints the value the services use for a port key: the existing row, else the given port.
# Arguments: $1 - row key, $2 - port to use when the key has no row
# Returns 0 when the row exists (the existing value is printed and the kept row is logged),
# 1 when there is none (the given port is printed), 2 on a database error.
read_effective_dsp_port() {
  local key="$1" port="$2"
  local db_property="${DB_PROPERTY_SCRIPT:-/usr/share/xroad/scripts/db_property.sh}"
  local value rc=0

  value=$("$db_property" get "$key") || rc=$?
  case "$rc" in
    0) dsp_ports_log "Keeping existing ${key}=${value}"; printf '%s\n' "$value"; return 0 ;;
    1) printf '%s\n' "$port"; return 1 ;;
    *) dsp_ports_log "FATAL: failed to read the row ${key} from the database"; return 2 ;;
  esac
}

# Prints a port in comparable form: without leading zeros when it is a valid port, else as is.
# Arguments: $1 - port value
comparable_port() {
  normalize_port "$1" || printf '%s\n' "$1"
}

# Stores the DID port and the DSP port chosen at installation. A port equal to its default is not
# stored (a missing row means the default); an existing row is kept and wins over the given port.
# The DID port must differ from the DSP port as the services will see them, existing rows included.
# Arguments: $1 - DID port (already checked), $2 - DSP port (already checked)
# Returns non-zero, naming the key and the value, on a database error or when the effective ports are equal.
store_dsp_port_rows() {
  local did="$1" dsp="$2"
  local db_property="${DB_PROPERTY_SCRIPT:-/usr/share/xroad/scripts/db_property.sh}"
  local did_effective dsp_effective did_rc=0 dsp_rc=0

  did_effective=$(read_effective_dsp_port "$DSP_DID_PORT_KEY" "$did") || did_rc=$?
  (( did_rc <= 1 )) || return 1
  dsp_effective=$(read_effective_dsp_port "$DSP_DSP_PORT_KEY" "$dsp") || dsp_rc=$?
  (( dsp_rc <= 1 )) || return 1

  if [[ "$(comparable_port "$did_effective")" == "$(comparable_port "$dsp_effective")" ]]; then
    dsp_ports_log "FATAL: the DID port and the DSP port must be different, both are '${did_effective}'" \
      "(${DSP_DID_PORT_KEY}=${did_effective}, ${DSP_DSP_PORT_KEY}=${dsp_effective})"
    return 1
  fi

  if (( did_rc == 1 )) && [[ "$did" != "$DSP_DEFAULT_DID_PORT" ]]; then
    "$db_property" set --if-absent "$DSP_DID_PORT_KEY" "$did" \
      || { dsp_ports_log "FATAL: failed to store ${DSP_DID_PORT_KEY}=${did} in the database"; return 1; }
  fi
  if (( dsp_rc == 1 )) && [[ "$dsp" != "$DSP_DEFAULT_DSP_PORT" ]]; then
    "$db_property" set --if-absent "$DSP_DSP_PORT_KEY" "$dsp" \
      || { dsp_ports_log "FATAL: failed to store ${DSP_DSP_PORT_KEY}=${dsp} in the database"; return 1; }
  fi
}
