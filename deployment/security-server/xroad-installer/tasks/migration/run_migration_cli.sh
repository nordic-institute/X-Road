#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../../lib/common.sh"

# Source secret-store-local.conf to export XROAD_SECRET_STORE_{HOST,PORT,SCHEME,TOKEN}.
# Guarded with [ -f ... ] so the wrapper continues on hosts without xroad-secret-store-local.
# Java side (MigrationVaultClient.createAndPreflight) per-subcommand validates these vars.
[ -f /etc/xroad/services/secret-store-local.conf ] && source /etc/xroad/services/secret-store-local.conf

XROAD_MIGRATION_UNATTENDED="${XROAD_MIGRATION_UNATTENDED:-}"

# This constant must match the destination set in download_migration_cli.sh.
MIGRATION_CLI_JAR="/var/tmp/migration-cli.jar"

# Sentinel directory for per-step completion tracking.
SENTINEL_DIR="/var/lib/xroad-upgrade"

ensure_sentinel_dir() {
  if [[ ! -d "$SENTINEL_DIR" ]]; then
    mkdir -p "$SENTINEL_DIR"
    chmod 750 "$SENTINEL_DIR"
    log_info "Created sentinel directory: $SENTINEL_DIR"
  fi
}

is_step_done() {
  [[ -f "$SENTINEL_DIR/step-${1}.done" ]]
}

mark_step_done() {
  touch "$SENTINEL_DIR/step-${1}.done"
  log_info "Sentinel written: $SENTINEL_DIR/step-${1}.done"
}

# Prompt the operator for the soft token PIN before the keyconf migration step.
# The migration-CLI keyconf step reads the PIN from XROAD_MIGRATION_SOFTTOKEN_PIN
prompt_for_softtoken_pin() {
  local signer_dir="$1"
  local softtoken_p12="$signer_dir/softtoken/.softtoken.p12"

  if [[ ! -f "$softtoken_p12" ]]; then
    log_info "Soft token keystore not found at $softtoken_p12 — keyconf migration will not need a PIN"
    return 0
  fi

  if [[ -n "${XROAD_MIGRATION_SOFTTOKEN_PIN:-}" ]]; then
    log_info "XROAD_MIGRATION_SOFTTOKEN_PIN already set — reusing provided PIN for keyconf migration"
    return 0
  fi

  if [[ "${XROAD_MIGRATION_UNATTENDED:-}" == "true" ]]; then
    log_die "Unattended mode: export XROAD_MIGRATION_SOFTTOKEN_PIN before running the migration (required by the keyconf step)."
  fi

  local pin
  pin=$(whiptail --title "Migration Step: keyconf" --passwordbox \
    "Enter the soft token PIN.\n\nThe PIN is needed once to hash and store the existing soft token credential during the keyconf migration." \
    12 60 3>&1 1>&2 2>&3) || log_warn_exit "Soft token PIN entry cancelled by operator"

  if [[ -z "$pin" ]]; then
    log_die "Empty soft token PIN provided. Aborting keyconf migration."
  fi

  export XROAD_MIGRATION_SOFTTOKEN_PIN="$pin"
}

# Show a whiptail confirmation dialog before each migration step.
confirm_step() {
  local step="$1"
  local description="$2"

  if [[ "${XROAD_MIGRATION_UNATTENDED:-}" == "true" ]]; then
    log_info "Unattended mode: skipping confirmation for step '$step'"
    return 0
  fi

  if whiptail --title "Migration Step: $step" \
    --yesno "$description\n\nProceed?" 16 78; then
    log_info "Operator confirmed step: $step"
  else
    log_warn_exit "Migration step '$step' cancelled by operator"
  fi
}

# Run a single migration-CLI step with sentinel-based idempotency and
# per-step operator confirmation.
#
# Usage: run_migration_step <step> [--id <sentinel-id>] [--description <text>] [--no-confirm] [arg1] [arg2] ...
#   --id <sentinel-id>      Override the sentinel filename so the same CLI command
#                           can be invoked multiple times with distinct sentinels
#                           (e.g. file-to-db --id file-to-db-acme).
#   --description <text>    Operator-facing body shown in the whiptail confirmation
#                           dialog. Use this to explain what the step migrates and
#                           from/to where — important when the same CLI subcommand
#                           is invoked multiple times for different inputs.
#   --no-confirm            Skip the generic confirm_step prompt — used when the
#                           caller has already collected explicit operator intent
#                           via its own dialog (e.g. opt-in steps).
run_migration_step() {
  local step="$1"
  shift
  local sentinel="$step"
  local skip_confirm=false
  local description=""
  while true; do
    if [[ "${1:-}" == "--id" ]]; then
      shift
      sentinel="$1"
      shift
    elif [[ "${1:-}" == "--description" ]]; then
      shift
      description="$1"
      shift
    elif [[ "${1:-}" == "--no-confirm" ]]; then
      shift
      skip_confirm=true
    else
      break
    fi
  done
  local -a args=("$@")

  if is_step_done "$sentinel"; then
    log_info "Step '$sentinel' already completed (sentinel found) — skipping"
    return 0
  fi

  if [[ "$skip_confirm" != "true" ]]; then
    confirm_step "$sentinel" "${description:-Run migration CLI step: $step}"
  fi

  log_message "Running: java -jar $MIGRATION_CLI_JAR $step ${args[*]}"
  local tmpout
  tmpout=$(mktemp)
  if ! java -jar "$MIGRATION_CLI_JAR" "$step" "${args[@]}" 2>&1 | tee "$tmpout"; then
    rm -f "$tmpout"
    log_die "Migration CLI step '$sentinel' failed (non-zero exit). Sentinel not written."
  fi
  # Migration-CLI sometimes returns 0 even after an internal exception. Scan
  # output for stack traces / error markers and fail if we see any.
  if grep -qE '^(Error |Caused by:|Exception in |[[:alpha:].]+Exception:)' "$tmpout"; then
    local firstline
    firstline=$(grep -m1 -E '^(Error |Caused by:|Exception in |[[:alpha:].]+Exception:)' "$tmpout")
    rm -f "$tmpout"
    log_die "Migration CLI step '$sentinel' reported an error: $firstline"
  fi
  rm -f "$tmpout"

  mark_step_done "$sentinel"
}

main() {
  log_message "================================="
  log_message "Step: Run Migration CLI"
  log_message "================================="
  log_message ""

  require_root

  if [[ ! -f "$MIGRATION_CLI_JAR" ]]; then
    log_die "Migration CLI JAR not found at $MIGRATION_CLI_JAR. Run download_migration_cli.sh first."
  fi

  ensure_sentinel_dir

  run_migration_step "validate" \
    --description "Validate prerequisites and connectivity to the configuration database before running any migration steps. This is a read-only check; no data is written."

  # configuration-anchor migrates the configuration anchor XML contents into
  # the configuration database. The anchor path is configured in local.ini
  # (proxy.configuration-anchor-file); fall back to the canonical default.
  # crudini is a guaranteed prerequisite (installed by setup_prerequisites.sh).
  local conf_anchor_file
  conf_anchor_file=$(crudini --get /etc/xroad/conf.d/local.ini proxy configuration-anchor-file 2>/dev/null \
    || echo "/etc/xroad/configuration-anchor.xml")
  if [[ -f "$conf_anchor_file" ]]; then
    run_migration_step "configuration-anchor" \
      --description "Import the X-Road configuration anchor XML\n  from: $conf_anchor_file\n  into: configuration database" \
      "$conf_anchor_file" "/etc/xroad/db.properties"
  else
    log_info "Configuration anchor file not found at $conf_anchor_file — skipping configuration-anchor migration"
  fi

  # signer-devices migrates HSM/signer module declarations from devices.ini into
  # the configuration database under the "signer" scope. The devices.ini path is
  # configured in local.ini (signer.device-configuration-file); fall back to the
  # canonical default.
  local devices_ini_file
  devices_ini_file=$(crudini --get /etc/xroad/conf.d/local.ini signer device-configuration-file 2>/dev/null \
    || echo "/etc/xroad/devices.ini")
  if [[ -f "$devices_ini_file" ]]; then
    run_migration_step "signer-devices" \
      --description "Migrate HSM/signer module declarations\n  from: $devices_ini_file\n  into: configuration database (signer scope)" \
      "$devices_ini_file" "/etc/xroad/db.properties"
  else
    log_info "Signer devices file not found at $devices_ini_file — skipping signer-devices migration"
  fi

  # ini-to-db: migrate INI configuration files into the configuration database.
  # Order mirrors src/tool/migration-cli/migrate-xroad-7-config.sh CONFIG_FILES:
  # alphabetical override-*.ini first, then local.ini. nullglob is toggled
  # locally so the override-*.ini glob expands to zero entries (instead of the
  # literal pattern) when no overrides are present. The ${arr[@]+"${arr[@]}"}
  # form is the set -u-safe expansion for a possibly-empty array.
  local ini_basename
  shopt -s nullglob
  local -a override_ini_files=(/etc/xroad/conf.d/override-*.ini)
  shopt -u nullglob
  for ini_file in ${override_ini_files[@]+"${override_ini_files[@]}"} /etc/xroad/conf.d/local.ini; do
    if [[ ! -f "$ini_file" ]]; then
      log_info "INI file not found at $ini_file — skipping ini-to-db migration"
      continue
    fi
    ini_basename=$(basename "$ini_file" .ini)
    run_migration_step "ini-to-db" --id "ini-to-db-${ini_basename}" \
      --description "Migrate INI configuration\n  from: $ini_file\n  into: configuration database\n\nThis step runs once per INI file (override-*.ini and local.ini)." \
      "$ini_file" "/etc/xroad/db.properties"
  done

  # properties-to-db (ssl): migrate the SSL properties file under the
  # proxy-ui-api scope. Path is configured in local.ini
  # (proxy-ui-api.ssl-properties); fall back to the canonical default.
  # Distinct sentinel id so future properties-to-db steps don't collide.
  local ssl_properties_file
  ssl_properties_file=$(crudini --get /etc/xroad/conf.d/local.ini proxy-ui-api ssl-properties 2>/dev/null \
    || echo "/etc/xroad/ssl.properties")
  if [[ -f "$ssl_properties_file" ]]; then
    run_migration_step "properties-to-db" --id "properties-to-db-ssl" \
      --description "Migrate SSL properties\n  from: $ssl_properties_file\n  into: configuration database (proxy-ui-api scope)" \
      "$ssl_properties_file" "/etc/xroad/db.properties" "proxy-ui-api"
  else
    log_info "SSL properties file not found at $ssl_properties_file — skipping properties-to-db (ssl) migration"
  fi

  # keyconf is special: the soft token PIN must be collected from the operator
  # before invoking migration-cli. We confirm the step FIRST so an operator who
  # cancels at the confirmation dialog is never asked for the PIN. Then, since
  # confirmation has already been collected explicitly, run_migration_step is
  # invoked with --no-confirm to avoid a duplicate dialog.
  if ! is_step_done "keyconf"; then
    confirm_step "keyconf" "Migrate the signer keyconf (keys, certificates, soft token credentials)\n  from: /etc/xroad/signer\n  into: configuration database\n\nYou will be prompted for the soft token PIN next."
    prompt_for_softtoken_pin "/etc/xroad/signer"
    run_migration_step "keyconf" --no-confirm "/etc/xroad/signer" "/etc/xroad/db.properties"
    unset XROAD_MIGRATION_SOFTTOKEN_PIN
  fi

  # signer-token-pins migrates token PINs from xroad-autologin scripts to OpenBao.
  # Only meaningful when xroad-autologin is installed — detect by script presence
  # at the paths the migration-cli probes (works on both Ubuntu and RHEL).
  local autologin_custom="/usr/share/xroad/autologin/custom-fetch-pin.sh"
  local autologin_default="/usr/share/xroad/autologin/default-fetch-pin.sh"
  if [[ -f "$autologin_custom" || -f "$autologin_default" ]]; then
    run_migration_step "signer-token-pins" \
      --description "Migrate signer token PINs\n  from: xroad-autologin fetch-pin scripts\n  into: OpenBao secret store"
  else
    log_info "xroad-autologin not installed — skipping signer-token-pins migration"
  fi

  # file-to-db (acme): stores the entire contents of acme.yml under property
  # key xroad.acme (scope: proxy-ui-api). Distinct sentinel id so it doesn't
  # collide with the mail file-to-db sentinel below.
  local acme_yml="/etc/xroad/conf.d/acme.yml"
  if [[ -f "$acme_yml" ]]; then
    run_migration_step "file-to-db" --id "file-to-db-acme" \
      --description "Migrate ACME configuration (full file contents)\n  from: $acme_yml\n  into: configuration database\n  key:  xroad.acme (proxy-ui-api scope)" \
      "$acme_yml" "/etc/xroad/db.properties" "xroad.acme" "proxy-ui-api"
  else
    log_info "ACME configuration file not found at $acme_yml — skipping file-to-db (acme) migration"
  fi

  # file-to-db (mail): stores the entire contents of mail.yml under property
  # key xroad.mail-notification (scope: proxy-ui-api).
  local mail_yml="/etc/xroad/conf.d/mail.yml"
  if [[ -f "$mail_yml" ]]; then
    run_migration_step "file-to-db" --id "file-to-db-mail" \
      --description "Migrate mail notification configuration (full file contents)\n  from: $mail_yml\n  into: configuration database\n  key:  xroad.mail-notification (proxy-ui-api scope)" \
      "$mail_yml" "/etc/xroad/db.properties" "xroad.mail-notification" "proxy-ui-api"
  else
    log_info "Mail notification configuration file not found at $mail_yml — skipping file-to-db (mail) migration"
  fi

  # batch-signing: optional opt-in to preserve the X-Road 7 behavior of having
  # batch signing enabled. X-Road 8 disables it by default. When the operator
  # opts in we set xroad.proxy.batch-signing-enabled=true via set-property;
  # otherwise the default (disabled) stands. The sentinel is written either
  # way so reruns don't re-prompt.
  local batch_sentinel="batch-signing-prompt"
  if is_step_done "$batch_sentinel"; then
    log_info "Step '$batch_sentinel' already completed (sentinel found) — skipping"
  elif [[ "${XROAD_MIGRATION_UNATTENDED:-}" == "true" ]]; then
    log_info "Unattended mode: leaving batch signing at X-Road 8 default (disabled)"
    mark_step_done "$batch_sentinel"
  elif whiptail --title "Migration Step: batch-signing" --defaultno \
    --yesno "Keep batch signing enabled?\n\nBatch signing is disabled by default in X-Road 8. Select Yes to preserve the X-Road 7 behavior of batch signing being enabled." 12 60; then
    log_info "Operator opted to enable batch signing"
    run_migration_step "set-property" --id "$batch_sentinel" --no-confirm \
      "/etc/xroad/db.properties" "xroad.proxy.batch-signing-enabled" "true"
  else
    log_info "Operator opted to leave batch signing at X-Road 8 default (disabled)"
    mark_step_done "$batch_sentinel"
  fi

  log_message ""
  log_info "Migration CLI steps completed."
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then main; fi
