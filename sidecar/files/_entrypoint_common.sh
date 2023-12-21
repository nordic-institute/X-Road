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

RECONFIG=(xroad-signer xroad-proxy)
if [ -f /usr/share/xroad/jlib/addon/proxy/messagelog.conf ]; then
  RECONFIG+=(xroad-addon-messagelog)
fi
if [ -f /usr/share/xroad/jlib/addon/proxy/opmonitoring.conf ]; then
  RECONFIG+=(xroad-opmonitor)
fi

LOCAL_DB=

if [ -f /.xroad-reconfigured ]; then
  # restarted container, skip reconfigure by default
  RECONFIG_REQUIRED=${RECONFIG_REQUIRED:-false}
else
  # new container, run reconfigure by default
  # makes it possible to "upgrade" from "slim" to "full" container
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
  useradd -m "${XROAD_ADMIN_USER}" -s /usr/sbin/nologin
  echo "${XROAD_ADMIN_USER}:${XROAD_ADMIN_PASSWORD}" | chpasswd
  echo "xroad-proxy xroad-common/username string ${XROAD_ADMIN_USER}" | debconf-set-selections
fi
XROAD_ADMIN_USER=
XROAD_ADMIN_PASSWORD=

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
    cp -a -n "$PACKAGED_CONFIG"/backup/local.ini /etc/xroad/conf.d/
    cp -a -n "$PACKAGED_CONFIG"/backup/local.properties /etc/xroad/services/
    # packages need to be reconfigured (runs possible db and config migrations)
    RECONFIG_REQUIRED=true
  fi
else
    warn "Installed version ($INSTALLED_VERSION) does not match packaged version ($PACKAGED_VERSION)"
fi

# Generate internal and admin UI TLS keys and certificates if necessary
if [ ! -f /etc/xroad/ssl/internal.crt ]; then
  log "Generating new internal TLS key and certificate"
  "$XROAD_SCRIPT_LOCATION/generate_certificate.sh" -n internal -f -S -p 2>&1 >/dev/null | sed 's/^/    /'
fi

if [ ! -f /etc/xroad/ssl/proxy-ui-api.crt ]; then
  log "Generating new SSL key and certificate for the admin UI"
  "$XROAD_SCRIPT_LOCATION/generate_certificate.sh" -n proxy-ui-api -f -S -p 2>&1 >/dev/null | sed 's/^/   /'
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
    if [ -f /usr/share/xroad/jlib/addon/proxy/messagelog.conf ]; then
      messagelog=true
    fi
    if [ -f /usr/share/xroad/jlib/addon/proxy/opmonitoring.conf ]; then
      opmonitor=true
    fi
    echo "xroad-proxy xroad-common/database-host string ${XROAD_DB_HOST}:${XROAD_DB_PORT}" | debconf-set-selections
    if [ -n "${XROAD_DATABASE_NAME}" ]; then
      touch /etc/xroad/db.properties
      chown xroad:xroad /etc/xroad/db.properties
      chmod 640 /etc/xroad/db.properties
      set_db_props() {
        crudini --set --inplace "$ROOT_PROPERTIES" "" "$1.database.admin_user" "${XROAD_DATABASE_NAME}_$1_admin"
        echo "$1.hibernate.connection.username= ${XROAD_DATABASE_NAME}_$1" >> "${DB_PROPERTIES}"
        echo "$1.hibernate.connection.url = jdbc:postgresql://${XROAD_DB_HOST}:${XROAD_DB_PORT}/${XROAD_DATABASE_NAME}_$1" >> "${DB_PROPERTIES}"
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
    pg_ctlcluster 12 main start
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
  ((count>=60)) && warn "Unable to determine database $db_addr:$db_port status"

  log "Reconfiguring packages"
  if dpkg-reconfigure -fnoninteractive "${RECONFIG[@]}" 2>&1 | sed 's/^/    /'; then
    echo "$PACKAGED_VERSION" >/etc/xroad/VERSION
    touch /.xroad-reconfigured
  fi
  if [[ "$LOCAL_DB" == "true" ]]; then
    pg_ctlcluster 12 main stop
    sleep 1
    crudini --set --existing=section /etc/supervisor/conf.d/xroad.conf program:postgres autostart true &>/dev/null ||:
  else
    crudini --set --existing=section /etc/supervisor/conf.d/xroad.conf program:postgres autostart false &>/dev/null ||:
  fi
fi
XROAD_DB_PWD=

if [ -n "${XROAD_LOG_LEVEL}" ]; then
  sed -i -e "s/XROAD_LOG_LEVEL=.*/XROAD_LOG_LEVEL=${XROAD_LOG_LEVEL}/" /etc/xroad/conf.d/variables-logback.properties
fi

if [ -n "${XROAD_ROOT_LOG_LEVEL}" ]; then
  sed -i -e "s/XROAD_ROOT_LOG_LEVEL=.*/XROAD_ROOT_LOG_LEVEL=${XROAD_ROOT_LOG_LEVEL}/" /etc/xroad/conf.d/variables-logback.properties
fi

log "Generating internal gRPC TLS keys and certificate"
rm -rf /var/run/xroad
mkdir -p -m0750 /var/run/xroad
chown xroad:xroad /var/run/xroad
su - xroad -c sh -c /usr/share/xroad/scripts/xroad-base.sh

