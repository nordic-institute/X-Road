#!/bin/bash
#############################################################################
#
# X-Road Security Server container entrypoint script (common part for all
# image types)
#
# Handles necessary initialization in the following cases:
# * container is run for the first time (no configuration present)
# * container is run/started with exising configuration
#   * configuration is up-to-date or
#   * container image has been updated and configuration needs to be migrated
#     to the new version
#
#############################################################################

log() { echo "$(date --utc -Iseconds) INFO [entrypoint] $*"; }
warn() { echo "$(date --utc -Iseconds) WARN [entrypoint] $*" >&2; }

init_db_dir() {
  local pgdata=/var/lib/postgresql/18/main
  if [ ! -s "$pgdata/PG_VERSION" ]; then
    log "Initializing local database at \"$pgdata\""
    mkdir -p "$pgdata"
    chmod 0700 "$pgdata"
    chown postgres:postgres "$pgdata"
    sudo -u postgres /usr/lib/postgresql/18/bin/initdb -D "$pgdata"
  fi
}

configure_secret_store() {
  log "Configuring secret store"
  if ! bash /usr/share/xroad/scripts/sidecar/secret-store-init.sh 2>&1 | sed 's/^/    /'; then
    warn "Secret store configuration failed"
    return 1
  fi
}

# TLS trust for the embedded secret store's vault clients. The OpenBao TLS
# certificate is regenerated on every boot (see secret-store-init.sh), so it
# cannot ride the native packages' install-time system/JVM trust-store route;
# instead the services inherit the CA path through supervisord's environment.
# With an external store nothing is exported — the operator supplies trust
# configuration, and any value already present in the container environment
# wins over the embedded default.
configure_secret_store_trust_env() {
  if [ -n "${XROAD_SECRET_STORE_HOST:-}" ]; then
    return 0
  fi
  export QUARKUS_VAULT_TLS_CA_CERT="${QUARKUS_VAULT_TLS_CA_CERT:-/etc/xroad/ssl/openbao.crt}"
  export SPRING_CLOUD_VAULT_SSL_TRUST_STORE="${SPRING_CLOUD_VAULT_SSL_TRUST_STORE:-file:/etc/xroad/ssl/openbao.crt}"
  export SPRING_CLOUD_VAULT_SSL_TRUST_STORE_TYPE="${SPRING_CLOUD_VAULT_SSL_TRUST_STORE_TYPE:-PEM}"
}

seed_dsp_participant_context_id() {
  log "Seeding DSP participant-context-id"
  if ! bash /usr/share/xroad/scripts/sidecar/dsp-config-seed.sh 2>&1 | sed 's/^/    /'; then
    warn "DSP participant-context-id seeding failed"
    return 1
  fi
}

# xroad.signer.autologin.enabled (XRDADR-34) is resolved by the signer's
# XRoadConfig DSL from the serverconf configuration_properties table or its
# packaged default only - unlike the per-token PINs
# (XROAD_SIGNER_AUTOLOGIN_TOKENS__<id>__PIN, a plain smallrye @ConfigMapping
# that reads the process environment directly), it has no environment or
# JVM-system-property binding. XROAD_SIGNER_AUTOLOGIN_ENABLED is the
# sidecar's operator-facing bridge from a docker-run variable to that row;
# see signer-autologin-config-seed.sh for the full contract.
seed_signer_autologin_enabled() {
  log "Seeding signer autologin enable flag"
  if ! bash /usr/share/xroad/scripts/sidecar/signer-autologin-config-seed.sh 2>&1 | sed 's/^/    /'; then
    warn "Signer autologin enable-flag seeding failed"
    return 1
  fi
}

# The packaged xroad-proxy startup script (proxy.conf, via global.conf's
# set_quarkus_profiles) always launches the JVM with -Dquarkus.profile=native,ss,
# so proxy's own DeploymentMode stays NATIVE (org.niis.xroad.proxy.core.
# configuration.ProxyConfig#deploymentMode checks for "containerized" in the
# active profile list) - which is required: DeploymentMode.CONTAINERIZED also
# flips xroad.common-global-conf.source to REMOTE and the gRPC peer hosts
# (signer, configuration-client, ...) from 127.0.0.1 to service DNS names that
# do not exist in this single-container image. Adding "containerized" to the
# profile list is therefore not an option here.
#
# health-check-enabled is the one health-check key with a container-only
# default (ProxyConfigKeys.HEALTH_CHECK_ENABLED: false natively, true under
# "%containerized"); health-check-port and health-check-interface default the
# same way in both modes, so only this one property needs forcing. Forced
# directly as JVM system properties (ordinal 400, above the packaged
# application.yaml's ordinal-255 defaults) through the documented local.conf
# override point (see global.conf's apply_local_conf), so the proxy's
# quarkus.http.host-enabled: ${xroad.proxy.health-check-enabled} interpolation
# resolves true without touching quarkus.profile/DeploymentMode at all.
configure_proxy_health_check_listener() {
  local local_conf=/etc/xroad/services/local.conf
  local marker="# xroad-sidecar: force-enable xroad-proxy's health-check HTTP listener"
  if [ -f "$local_conf" ] && grep -qF "$marker" "$local_conf"; then
    return 0
  fi
  log "Force-enabling xroad-proxy's health-check HTTP listener"
  cat >>"$local_conf" <<EOF
$marker
if [ "\$1" = "XROAD_PROXY_PARAMS" ]; then
  PROXY_PARAMS="\$PROXY_PARAMS -Dxroad.proxy.health-check-enabled=true -Dquarkus.http.host-enabled=true"
fi
EOF
  chown root:root "$local_conf"
  chmod 644 "$local_conf"
}

# xroad-ds-control-plane's packaged application.yaml declares
# edc.iam.trusted-issuer.issuer.id: ${xroad.edc.iam.trusted-issuer.issuer.id}
# with no fallback (EdcConfigKeys.TRUSTED_ISSUER_ID is deliberately
# without a default, so an unset value fails startup rather than silently
# registering an empty trusted issuer). Every other deployment mode supplies
# this as a plain environment variable pointing at a real issuer service
# (the k8s chart's XROAD_EDC_IAM_TRUSTED_ISSUER_ISSUER_ID); the sidecar has
# no issuer service at all (out of scope, a Central Server component), so
# this seeds a placeholder DID of the same shape purely so the service
# starts - functional credential issuance needs a real issuer, configured by
# the operator overriding the same environment variable.
configure_ds_control_plane_trusted_issuer_default() {
  local local_conf=/etc/xroad/services/local.conf
  local marker="# xroad-sidecar: default xroad.edc.iam.trusted-issuer.issuer.id for xroad-ds-control-plane"
  if [ -f "$local_conf" ] && grep -qF "$marker" "$local_conf"; then
    return 0
  fi
  log "Seeding a default DS trusted-issuer DID for xroad-ds-control-plane"
  cat >>"$local_conf" <<EOF
$marker
if [ "\$1" = "XROAD_DS_CONTROL_PLANE_PARAMS" ]; then
  : "\${XROAD_EDC_IAM_TRUSTED_ISSUER_ISSUER_ID:=did:web:\${HOSTNAME:-localhost}%3A10100:issuer}"
  export XROAD_EDC_IAM_TRUSTED_ISSUER_ISSUER_ID
fi
EOF
  chown root:root "$local_conf"
  chmod 644 "$local_conf"
}

# xroad-opmonitor's packaged JVM flags fix -XX:MaxMetaspaceSize at 120m, sized
# for a dedicated container with the daemon's own uncontended memory
# allocation - every other deployment mode. In this image, op-monitor-daemon
# is one of several JVMs sharing one container's resource envelope, so the
# same request-driven metaspace growth has markedly less isolation here and
# can exhaust the packaged ceiling under sustained load, failing live
# requests with OutOfMemoryError: Metaspace. Raised here, in the documented
# local.conf override point, rather than in the shared xroad-opmonitor
# package config every deployment mode consumes.
configure_opmonitor_metaspace() {
  local local_conf=/etc/xroad/services/local.conf
  local marker="# xroad-sidecar: raise xroad-opmonitor's Metaspace ceiling"
  if [ -f "$local_conf" ] && grep -qF "$marker" "$local_conf"; then
    return 0
  fi
  log "Raising xroad-opmonitor's Metaspace ceiling for the shared single-container deployment"
  cat >>"$local_conf" <<EOF
$marker
if [ "\$1" = "XROAD_OPMON_PARAMS" ]; then
  OPMON_PARAMS="\$OPMON_PARAMS -XX:MaxMetaspaceSize=256m"
fi
EOF
  chown root:root "$local_conf"
  chmod 644 "$local_conf"
}

HOOK_DIR=/etc/xroad/entrypoint.d

# Hooks are the last provisioning step: they run after every seed_*/configure_*
# function above, once the dpkg-reconfigure step has succeeded - true by default
# only on the container's first boot (see RECONFIG_REQUIRED), so a plain restart
# does not re-run them. The local database started further down for the
# reconfigure step is still running when they execute, so a hook can reach it.
#
# find -L follows symlinks so that hook files delivered as symlinks (every file
# in a Kubernetes ConfigMap or Secret mount is one) are found; a symlink with no
# target resolves to nothing and is not a regular file, so it is not run.
run_first_boot_hooks() {
  [ -d "$HOOK_DIR" ] || return 0
  local hook rc
  local -a hooks
  mapfile -t hooks < <(find -L "$HOOK_DIR" -maxdepth 1 -type f -print | LC_ALL=C sort)
  for hook in "${hooks[@]}"; do
    if [ ! -x "$hook" ]; then
      log "Skipping non-executable first-boot hook \"$hook\""
      continue
    fi
    log "Running first-boot hook \"$hook\""
    "$hook"
    rc=$?
    if [ "$rc" -ne 0 ]; then
      warn "First-boot hook \"$hook\" exited with status $rc, aborting boot"
      exit 1
    fi
  done
}

create_backup_dir_if_not_exists() {
  local xroadDir=/var/lib/xroad
  local backupDir=$xroadDir/backup
  if [ ! -d "$backupDir" ]; then
    log "Create backup dir \"$backupDir\""
    mkdir -p "$backupDir"
    chown xroad:xroad "$xroadDir"
    chown xroad:xroad "$backupDir"
    chmod 0755 "$backupDir"
    chmod -R go-w "$backupDir"
  fi
}

XROAD_SCRIPT_LOCATION=/usr/share/xroad/scripts
DB_PROPERTIES=/etc/xroad/db.properties

if [ -f /etc/xroad.properties ]; then
  # makes it possible to "mount" a file to /etc/xroad.properties
  ROOT_PROPERTIES=/etc/xroad.properties
else
  # keep xroad.properties with other configuration (needed when running
  # database migration e.g. during upgrades)
  ROOT_PROPERTIES=/etc/xroad/xroad.properties
  ln -s "$ROOT_PROPERTIES" /etc/xroad.properties
fi

INSTALLED_VERSION=$(dpkg-query --showformat='${Version}' --show xroad-proxy)
PACKAGED_CONFIG=/usr/share/xroad/config
PACKAGED_VERSION="$(cat /${PACKAGED_CONFIG}/VERSION)"

RECONFIG=(xroad-signer xroad-proxy xroad-proxy-ui-api xroad-confclient xroad-opmonitor xroad-ds-control-plane xroad-ds-identity-hub)

LOCAL_DB=
RECONFIGURED=false

if [ -f /.xroad-reconfigured ]; then
  # restarted container, skip reconfigure by default
  RECONFIG_REQUIRED=${RECONFIG_REQUIRED:-false}
else
  # new container, run reconfigure by default
  # (Disabling reconfigure by setting RECONFIG_REQUIRED to false
  # when it is known to be unnecessary saves some container startup time)
  RECONFIG_REQUIRED=${RECONFIG_REQUIRED:-true}
fi

log "Starting X-Road Security Server version $INSTALLED_VERSION"

mkdir -p -m 1750 /var/tmp/xroad
chown xroad:xroad /etc/xroad /var/lib/xroad /var/tmp/xroad

if [[ -n "$XROAD_ADMIN_USER" ]] && ! getent passwd "$XROAD_ADMIN_USER" &>/dev/null; then
  # Configure admin user with user-supplied username and password
  log "Creating admin user with user-supplied credentials"
  if [[ -n "$XROAD_ADMIN_PWD_HASH" ]]; then
    useradd -m "${XROAD_ADMIN_USER}" -s /usr/sbin/nologin -p "$XROAD_ADMIN_PWD_HASH"
  else
    useradd -m "${XROAD_ADMIN_USER}" -s /usr/sbin/nologin
    echo "${XROAD_ADMIN_USER}:${XROAD_ADMIN_PASSWORD}" | chpasswd
  fi
  echo "xroad-proxy xroad-common/username string ${XROAD_ADMIN_USER}" | debconf-set-selections
fi
XROAD_ADMIN_USER=
XROAD_ADMIN_PASSWORD=
XROAD_ADMIN_PWD_HASH=

if [ "$INSTALLED_VERSION" == "$PACKAGED_VERSION" ]; then
  if [ -f /etc/xroad/VERSION ]; then
    CONFIG_VERSION="$(cat /etc/xroad/VERSION)"
  else
    warn "Current configuration version not known"
    CONFIG_VERSION=
  fi
  if dpkg --compare-versions "$PACKAGED_VERSION" gt "$CONFIG_VERSION"; then
    # ensure that the updated stock configuration is present in /etc/xroad
    # handles also the case where configuration is missing (e.g. config volume not automatically populated)
    log "Migrating configuration from ${CONFIG_VERSION:-none} to $PACKAGED_VERSION"
    cp -a "$PACKAGED_CONFIG/etc/xroad/"* /etc/xroad/
    # copy if not exists
    cp -a -n "$PACKAGED_CONFIG"/backup/devices.ini /etc/xroad/
    cp -a -n "$PACKAGED_CONFIG"/backup/local.properties /etc/xroad/services/
    # packages need to be reconfigured (runs possible db and config migrations)
    RECONFIG_REQUIRED=true
  fi
else
  warn "Installed version ($INSTALLED_VERSION) does not match packaged version ($PACKAGED_VERSION)"
fi

# Create database properties and configure remote db address if necessary
if [ ! -f ${DB_PROPERTIES} ]; then
  XROAD_DB_PORT="${XROAD_DB_PORT:-5432}"
  XROAD_DB_HOST="${XROAD_DB_HOST:-127.0.0.1}"
  log "Creating serverconf database and properties file"
  RECONFIG_REQUIRED=true
  if [[ "${XROAD_DB_HOST}" != "127.0.0.1" ]]; then
    LOCAL_DB=false
    log "Using remote database $XROAD_DB_HOST:$XROAD_DB_PORT"
    messagelog=true
    opmonitor=true
    echo "xroad-proxy xroad-common/database-host string ${XROAD_DB_HOST}:${XROAD_DB_PORT}" | debconf-set-selections
    if [ -n "${XROAD_DATABASE_NAME}" ]; then
      touch /etc/xroad/db.properties
      chown xroad:xroad /etc/xroad/db.properties
      chmod 640 /etc/xroad/db.properties
      set_db_props() {
        crudini --set --inplace "$ROOT_PROPERTIES" "" "$1.database.admin_user" "${XROAD_DATABASE_NAME}_$1_admin"
        echo "$1.hibernate.connection.username= ${XROAD_DATABASE_NAME}_$1" >>"${DB_PROPERTIES}"
        echo "$1.hibernate.connection.url = jdbc:postgresql://${XROAD_DB_HOST}:${XROAD_DB_PORT}/${XROAD_DATABASE_NAME}_$1" >>"${DB_PROPERTIES}"
      }
      set_db_props serverconf
      if [ -n "$opmonitor" ]; then
        set_db_props "op-monitor"
      fi
      if [ -n "$messagelog" ]; then
        set_db_props messagelog
      fi
    fi
  else
    LOCAL_DB=true
  fi
fi

if [[ "$RECONFIG_REQUIRED" == "true" ]]; then
  # reconfigure packages (also runs database migrations)

  if [ ! -f "$ROOT_PROPERTIES" ]; then
    touch "$ROOT_PROPERTIES"
    chown root:root "$ROOT_PROPERTIES"
    chmod 600 "$ROOT_PROPERTIES"
  fi

  db_host="${XROAD_DB_HOST:-127.0.0.1}:${XROAD_DB_PORT:-5432}"
  if [ -z "$LOCAL_DB" ]; then
    # exising config, determine database location from db.properties
    db_url=$(crudini --get '/etc/xroad/db.properties' "" 'serverconf.hibernate.connection.url' 2>/dev/null)
    pat='^jdbc:postgresql://([^/]*).*'
    if [[ "$db_url" =~ $pat ]]; then
      db_host="${BASH_REMATCH[1]:-$db_host}"
    fi
    if [[ -n "$db_host" && "$db_host" != 127.* ]]; then
      LOCAL_DB=false
    else
      LOCAL_DB=true
    fi
  fi
  if [[ "$LOCAL_DB" == "true" ]]; then
    init_db_dir
    pg_ctlcluster 18 main start
  else
    if [[ -n "$XROAD_DB_PWD" ]]; then
      if [[ -w "$ROOT_PROPERTIES" ]]; then
        crudini --set --inplace "$ROOT_PROPERTIES" "" "postgres.connection.password" "${XROAD_DB_PWD}"
      else
        warn "XROAD_DB_PWD is set but $ROOT_PROPERTIES is not writable"
      fi
    fi
  fi

  log "Waiting for the database to become available..."
  IFS=',' read -ra hosts <<<"$db_host"
  db_addr="${hosts[0]%%:*}"
  db_port="${hosts[0]##*:}"
  count=0
  while ((count++ < 60)) && ! pg_isready -q -t 2 -h "$db_addr" -p "$db_port"; do
    sleep 1
  done
  ((count >= 60)) && warn "Unable to determine database $db_addr:$db_port status"

  log "Reconfiguring packages"
  if dpkg-reconfigure -fnoninteractive "${RECONFIG[@]}" 2>&1 | sed 's/^/    /'; then
    RECONFIGURED=true
    seed_dsp_participant_context_id
    seed_signer_autologin_enabled
  fi
  # The local database, if any, is left running until the provisioning steps
  # below (and the first-boot hooks that follow them) are done with it.
fi
XROAD_DB_PWD=

if [ -n "${XROAD_LOG_LEVEL}" ]; then
  sed -i -e "s/XROAD_LOG_LEVEL=.*/XROAD_LOG_LEVEL=${XROAD_LOG_LEVEL}/" /etc/xroad/conf.d/variables-logback.properties
fi

if [ -n "${XROAD_ROOT_LOG_LEVEL}" ]; then
  sed -i -e "s/XROAD_ROOT_LOG_LEVEL=.*/XROAD_ROOT_LOG_LEVEL=${XROAD_ROOT_LOG_LEVEL}/" /etc/xroad/conf.d/variables-logback.properties
fi

configure_proxy_health_check_listener
configure_ds_control_plane_trusted_issuer_default
configure_opmonitor_metaspace
configure_secret_store
configure_secret_store_trust_env
create_backup_dir_if_not_exists

# The configuration is only recorded as provisioned once the operator's hooks
# have succeeded too, so a hook that fails leaves the container retryable: the
# next start finds no /.xroad-reconfigured, reconfigures again and re-runs it.
if [[ "$RECONFIGURED" == "true" ]]; then
  run_first_boot_hooks
  echo "$PACKAGED_VERSION" >/etc/xroad/VERSION
  touch /.xroad-reconfigured
fi

if [[ "$RECONFIG_REQUIRED" == "true" ]]; then
  if [[ "$LOCAL_DB" == "true" ]]; then
    pg_ctlcluster 18 main stop
    sleep 1
    crudini --set --existing=section /etc/supervisor/conf.d/xroad.conf program:postgres autostart true &>/dev/null || :
  else
    crudini --set --existing=section /etc/supervisor/conf.d/xroad.conf program:postgres autostart false &>/dev/null || :
  fi
fi
