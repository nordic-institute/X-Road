#!/bin/bash
#
# Migration step 4: runs the regular first-boot provisioning (Liquibase,
# OpenBao setup) from _entrypoint_common.sh. The local database stays running
# until step 6.

MIGRATION_LIQUIBASE_LOG="/var/log/xroad/migration-liquibase-openbao.log"

# curl and the migration CLI in step 5 must trust OpenBao's self-signed
# certificate, as the native xroad-secret-store-local postinst sets up.
migration_trust_openbao_cert() {
  local cert=/etc/xroad/ssl/openbao.crt

  if [[ ! -s "$cert" ]]; then
    STEP_REASON="OpenBao TLS certificate not found at $cert after the secret store bootstrap"
    return 1
  fi

  mkdir -p /var/log/xroad
  if ! install -m 644 "$cert" /usr/local/share/ca-certificates/xroad-migration-openbao.crt \
    || ! update-ca-certificates >>"$MIGRATION_LIQUIBASE_LOG" 2>&1; then
    STEP_REASON="failed to add the OpenBao TLS certificate to the system trust store, see $MIGRATION_LIQUIBASE_LOG"
    return 1
  fi

  if [[ -x /var/lib/dpkg/info/ca-certificates-java.postinst ]] \
    && ! /var/lib/dpkg/info/ca-certificates-java.postinst configure >>"$MIGRATION_LIQUIBASE_LOG" 2>&1; then
    STEP_REASON="failed to sync the OpenBao TLS certificate into the JVM trust store, see $MIGRATION_LIQUIBASE_LOG"
    return 1
  fi

  return 0
}

step_liquibase_openbao() {
  if ! XROAD_ENTRYPOINT_FUNCTIONS_ONLY=true source /root/_entrypoint_common.sh; then
    STEP_REASON="failed to load the shared entrypoint provisioning functions"
    return 1
  fi

  RECONFIG_REQUIRED=true

  overlay_packaged_config
  run_reconfigure

  if [[ "$RECONFIGURED" != "true" ]]; then
    STEP_REASON="package reconfiguration (Liquibase) did not complete"
    return 1
  fi

  if ! configure_secret_store; then
    STEP_REASON="OpenBao bootstrap failed"
    return 1
  fi

  # configure_secret_store only generates and installs this certificate for
  # the embedded OpenBao; an external store brings its own trust.
  if [[ -z "${XROAD_SECRET_STORE_HOST:-}" ]]; then
    migration_trust_openbao_cert || return 1
  fi

  return 0
}
