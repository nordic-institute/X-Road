#!/bin/bash
#
# Value checks for the DID port and the DSP port. normalize_port and ports_differ are copies of the
# functions of the same names in dsp_ports.sh of the xroad-base package and must stay identical.
#

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
