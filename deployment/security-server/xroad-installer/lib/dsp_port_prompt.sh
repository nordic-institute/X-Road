#!/bin/bash
#
# The DID port and DSP port questions shared by xroad-installer.sh and xroad-upgrade.sh.
# Sourced after lib/common.sh. Sources the staged lib/dsp_ports.sh for the value checks and the defaults.
#
# Usage (when sourced):
#   validate_dsp_port_config   # Stops the script when XROAD_DID_PORT or XROAD_DSP_PORT holds an invalid value
#   select_dsp_ports           # Asks for the ports that are still empty, then logs the chosen ports
#   select_dsp_ports --no-prompt   # Same, but uses the default for a port that is still empty instead of asking
#

DSP_PORT_PROMPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

[[ -f "$DSP_PORT_PROMPT_DIR/dsp_ports.sh" ]] \
  || log_die "Missing $DSP_PORT_PROMPT_DIR/dsp_ports.sh. Use the published installer, or stage the helper scripts with copy_helper_scripts from scripts/release/_installer-common.sh."
# shellcheck source=/dev/null
source "$DSP_PORT_PROMPT_DIR/dsp_ports.sh"

# Stops the script when a DID or DSP port that came from the config file or the environment is invalid.
# A valid port is stored without leading zeros.
validate_dsp_port_config() {
  local normalized
  if [[ -n "$XROAD_DID_PORT" ]]; then
    normalized=$(normalize_port "$XROAD_DID_PORT") \
      || log_die "Invalid DID port '$XROAD_DID_PORT' in XROAD_DID_PORT: expected a number from 1024 to 65535."
    XROAD_DID_PORT="$normalized"
  fi
  if [[ -n "$XROAD_DSP_PORT" ]]; then
    normalized=$(normalize_port "$XROAD_DSP_PORT") \
      || log_die "Invalid DSP port '$XROAD_DSP_PORT' in XROAD_DSP_PORT: expected a number from 1024 to 65535."
    XROAD_DSP_PORT="$normalized"
  fi
  if [[ -n "$XROAD_DID_PORT" && -n "$XROAD_DSP_PORT" ]] && ! ports_differ "$XROAD_DID_PORT" "$XROAD_DSP_PORT"; then
    log_die "The DID port and the DSP port must be different, both are '$XROAD_DID_PORT'."
  fi
}

# Asks for one port until the answer is valid and prints it. Cancel or no terminal keeps the default.
# Arguments: $1 - port name, $2 - default port, $3 - the other port (empty when not known yet)
ask_port() {
  local name="$1" default="$2" other="$3"
  local answer port

  while true; do
    if ! answer=$(whiptail --inputbox "Enter the $name (1024-65535):" 8 78 "$default" --title "$name" 3>&1 1>&2 2>&3); then
      log_info "No answer for the $name, using the default $default"
      printf '%s\n' "$default"
      return
    fi
    if port=$(normalize_port "$answer") && { [[ -z "$other" ]] || ports_differ "$port" "$other"; }; then
      printf '%s\n' "$port"
      return
    fi
    whiptail --msgbox "'$answer' is not valid. Enter a number from 1024 to 65535 that differs from the other port." 8 78 --title "Invalid $name" >&2
  done
}

# Collects the DID port and the DSP port. With --no-prompt a port that is still empty gets its default.
select_dsp_ports() {
  local prompt=true
  [[ "${1:-}" == "--no-prompt" ]] && prompt=false

  if [[ -z "$XROAD_DID_PORT" ]]; then
    if [[ "$prompt" == true ]]; then
      XROAD_DID_PORT=$(ask_port "DID port" "$DSP_DEFAULT_DID_PORT" "$XROAD_DSP_PORT")
    else
      XROAD_DID_PORT="$DSP_DEFAULT_DID_PORT"
    fi
  fi
  if [[ -z "$XROAD_DSP_PORT" ]]; then
    if [[ "$prompt" == true ]]; then
      XROAD_DSP_PORT=$(ask_port "DSP port" "$DSP_DEFAULT_DSP_PORT" "$XROAD_DID_PORT")
    else
      XROAD_DSP_PORT="$DSP_DEFAULT_DSP_PORT"
    fi
  fi
  ports_differ "$XROAD_DID_PORT" "$XROAD_DSP_PORT" \
    || log_die "The DID port and the DSP port must be different, both are '$XROAD_DID_PORT'."

  log_info "DID port: $XROAD_DID_PORT"
  log_info "DSP port: $XROAD_DSP_PORT"
}
