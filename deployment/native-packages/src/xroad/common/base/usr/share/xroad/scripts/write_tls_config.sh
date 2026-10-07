#!/bin/bash
#
# Stores the TLS certificate provisioning identity of X-Road modules as configuration
# database rows. Splits a combined IP/DNS Subject Alternative Name list into the two
# families and extracts the bare common name from a subject answer.
#
# Usage (when called directly):
#   write_tls_config.sh setup_default <module_name>  # Auto-detect hostname and IPs, store identity rows if absent
#   write_tls_config.sh set <module_name> <subject> <alt_names> [--yes|--if-absent]  # Given identity, no guards
#
# Usage (when sourced):
#   . write_tls_config.sh
#   setup_default_tls_config "proxy"           # Auto-detect, store identity rows if absent
#   write_tls_identity_rows "proxy" "$subject" "$altn" [reconfigure]  # Package path, guarded
#   store_tls_identity_rows "proxy" "$subject" "$altn" [--yes|--if-absent]  # No guards
#

log () { echo >&2 "$@"; }

usage() {
  cat >&2 <<EOF
Usage:
  $0 setup_default <module_name>
  $0 set <module_name> <subject> <alt_names> [--yes|-y|--if-absent]

Commands:
  setup_default - Auto-detect hostname and IPs, store identity rows in the database if absent
  set           - Store the given identity rows; an existing row prompts before overwrite

Arguments:
  module_name   - X-Road module name (e.g., proxy, op-monitor, proxy-ui-api)
  subject       - Common name as a bare host name, /CN=host or a full distinguished name
  alt_names     - Alternative names in format: IP:1.1.1.1,DNS:name,IP:2.2.2.2,...

Options for set:
  -y, --yes      Overwrite existing rows without prompting
      --if-absent  Keep existing rows, insert only the missing ones

Examples:
  $0 setup_default proxy
  $0 set proxy ss1.example.com "IP:10.0.0.1,DNS:ss1.example.com,DNS:ss1" --yes

EOF
  exit 1
}

# Extract the values of one family (IP or DNS) from a combined list, comma-joined.
# Arguments: $1 - family prefix, $2 - combined list
extract_alt_names() {
  printf '%s\n' "$2" | tr ',' '\n' \
    | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' \
    | sed -n -e "s/^$1:[[:space:]]*//p" \
    | sed -e '/^$/d' \
    | paste -sd, -
}

# Extract the IP alternative names (IPv4 and IPv6) from a combined IP/DNS list.
# Input format:
#   IP:1.1.1.1,DNS:example.com,IP:2.2.2.2,DNS:example.org
#   IP:10.0.2.15,IP:fd17:625c:f037:2:a00:27ff:fe7f:dfb6,DNS:example.org,DNS:example.org
# Whitespace around entries is ignored.
extract_ip_list() {
  extract_alt_names "IP" "$1"
}

# Extract the DNS alternative names from a combined IP/DNS list.
# Input format:
#   IP:1.1.1.1,DNS:example.com,IP:2.2.2.2,DNS:example.org
#   IP:10.0.2.15,IP:fd17:625c:f037:2:a00:27ff:fe7f:dfb6,DNS:example.org,DNS:example.org
# Whitespace around entries is ignored.
extract_dns_list() {
  extract_alt_names "DNS" "$1"
}

# Extract the bare common name from a subject answer.
# Accepts a bare host name, a /CN=host form or a full distinguished name in slash
# (/C=EE/O=Org/CN=host) or comma (CN=host, O=Org, C=EE) form; the CN component may appear anywhere.
extract_cn() {
  local subject="${1#"${1%%[![:space:]]*}"}"
  subject="${subject%"${subject##*[![:space:]]}"}"
  if [[ "$subject" == *[/,=]* ]]; then
    subject=$(echo "$subject" | tr '/,' '\n\n' | sed -n 's/^[[:space:]]*[Cc][Nn][[:space:]]*=[[:space:]]*//p' | head -n1)
    subject="${subject%"${subject##*[![:space:]]}"}"
  fi
  echo "$subject"
}

# Store the TLS identity rows of a module in the configuration database without the package
# guards; the caller decides whether this host should write.
# Arguments:
#   $1 - module name (e.g., admin-service, management-service)
#   $2 - subject answer (bare host, /CN=host or full distinguished name)
#   $3 - Alternative names in format: IP:1.1.1.1,DNS:name,IP:2.2.2.2,...
#   $4 - optional db_property.sh write flag: --yes overwrites, --if-absent keeps existing
#        rows; none prompts before overwriting an existing row
# An answer without a common name falls back to the host name (hostname -f).
# Returns non-zero, naming the key, on any database error.
store_tls_identity_rows() {
  local module_name="$1"
  local subject="$2"
  local altn="$3"
  local -a write_flag=()
  [[ -n "${4:-}" ]] && write_flag=("$4")
  local db_property="${DB_PROPERTY_SCRIPT:-/usr/share/xroad/scripts/db_property.sh}"

  local prefix="xroad.${module_name}.tls.certificate-provisioning"
  local cn dns_list ip_list
  cn=$(extract_cn "$subject")
  if [[ -z "$cn" ]]; then
    cn=$(hostname -f 2>/dev/null || hostname 2>/dev/null || true)
    if [[ -z "$cn" ]]; then
      log "FATAL: no common name in the ${module_name} subject answer and no host name available"
      return 1
    fi
    log "No common name in the ${module_name} subject answer, using ${cn}"
  fi
  dns_list=$(extract_dns_list "$altn")
  ip_list=$(extract_ip_list "$altn")

  local key value
  for key in common-name alt-names ip-subject-alt-names; do
    case "$key" in
      common-name) value="$cn" ;;
      alt-names) value="$dns_list" ;;
      ip-subject-alt-names) value="$ip_list" ;;
    esac
    "$db_property" set "${prefix}.${key}" "$value" "${write_flag[@]}" \
      || { log "FATAL: failed to store ${prefix}.${key} in the database"; return 1; }
  done
}

# Package entry point for the TLS identity rows of a module.
# Arguments: as store_tls_identity_rows, with $4 optional "reconfigure" to overwrite existing rows.
# Skips when XROAD_IGNORE_DATABASE_SETUP is set or systemd is not the running init.
# Existing rows are kept unless reconfiguring.
write_tls_identity_rows() {
  local module_name="$1"
  local mode="${4:-}"

  if [[ -v XROAD_IGNORE_DATABASE_SETUP ]]; then
    log "XROAD_IGNORE_DATABASE_SETUP is set, not storing ${module_name} TLS identity rows"
    return 0
  fi

  # Identity rows are written only when systemd is the running init (the sd_booted test).
  # Without it the package is being configured inside an image build or a container's
  # first-start reconfigure, where the host name belongs to the build or the container rather
  # than to the deployment, and a stored row would outrank the runtime XROAD_HOST default.
  # Deployed X-Road always runs under systemd.
  if [[ ! -d /run/systemd/system ]]; then
    log "systemd is not the running init (image build or container reconfigure), not storing ${module_name} TLS identity rows"
    return 0
  fi

  local write_flag="--if-absent"
  if [[ "$mode" == "reconfigure" || "${DEBCONF_RECONFIGURE:-}" == "1" ]]; then
    write_flag="--yes"
  fi

  store_tls_identity_rows "$module_name" "$2" "$3" "$write_flag"
}

# Store the TLS identity rows of a module using the auto-detected host name and addresses.
# Existing rows are kept. Skips when XROAD_IGNORE_DATABASE_SETUP is set or systemd is not the
# running init.
# Arguments:
#   $1 - module name (e.g., proxy, proxy-ui-api)
setup_default_tls_config() {
  local module_name="$1"

  local host alt_names
  host=$(hostname -f)
  if (( ${#host} > 64 )); then
    host=$(hostname -s)
  fi
  alt_names="$(ip addr | awk '/scope global/ {split($2,a,"/"); printf "IP:%s,", a[1]}')DNS:$(hostname -f),DNS:$(hostname -s)"

  write_tls_identity_rows "$module_name" "$host" "$alt_names"
}

# Main execution block - only runs when script is executed directly (not sourced)
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  case "${1:-}" in
    setup_default)
      [[ -n "${2:-}" ]] || { log "Error: module name required"; usage; }
      setup_default_tls_config "$2"
      ;;
    set)
      shift
      write_flag=""
      positional=()
      while (($#)); do
        case "$1" in
          -y|--yes)    write_flag="--yes" ;;
          --if-absent) write_flag="--if-absent" ;;
          -h|--help)   usage ;;
          -*)          log "Error: unknown option $1"; usage ;;
          *)           positional+=("$1") ;;
        esac
        shift
      done
      (( ${#positional[@]} == 3 )) || { log "Error: set needs <module_name> <subject> <alt_names>"; usage; }
      store_tls_identity_rows "${positional[0]}" "${positional[1]}" "${positional[2]}" "$write_flag"
      ;;
    *)
      log "Error: unknown command '${1:-}'"
      usage
      ;;
  esac
fi
