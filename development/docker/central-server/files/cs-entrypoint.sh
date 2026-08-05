#!/bin/bash
PACKAGED_VERSION="$(cat /root/VERSION)"
INSTALLED_VERSION=$(dpkg-query --showformat='${Version}' --show xroad-center)

log() { echo "$(date --utc -Iseconds) INFO [entrypoint] $*"; }
warn() { echo "$(date --utc -Iseconds) WARN [entrypoint] $*" >&2; }

wait_db() {
  local count=0
  while ((count++ < 60)) && ! pg_isready -q -t 2; do
    sleep 1
  done
}

seed_configuration_properties() {
  local seed_dir="/etc/xroad/db-config-seed"
  [ -d "$seed_dir" ] || return 0
  local file key val
  declare -A props

  for file in "$seed_dir"/*.properties; do
    [ -e "$file" ] || continue
    while IFS= read -r key; do
      [ -z "$key" ] && continue
      props["$key"]="$(crudini --get "$file" "" "$key")"
    done < <(crudini --get "$file" "")
  done

  local rows=""
  for key in "${!props[@]}"; do
    val="${props[$key]//\'/\'\'}"
    rows+="('${key//\'/\'\'}','$val',now(),now()),"
  done
  [ -z "$rows" ] && return 0
  rows="${rows%,}"

  log "Seeding ${#props[@]} configuration_properties"
  pg_isready -q -t 2 || pg_ctlcluster 18 main start
  wait_db
  su -c "psql -q centerui_production" postgres <<EOF
SET ROLE centerui;
INSERT INTO configuration_properties (property_key, property_value, created_at, updated_at)
VALUES $rows
ON CONFLICT DO NOTHING;
EOF
  pg_ctlcluster 18 main stop
}

log "Starting X-Road central server version $INSTALLED_VERSION"

if [ "$INSTALLED_VERSION" == "$PACKAGED_VERSION" ]; then
    if [ -f /etc/xroad/VERSION ]; then
        CONFIG_VERSION="$(cat /etc/xroad/VERSION)"
    else
        warn "Current configuration version not known"
        CONFIG_VERSION=
    fi
    if [ -n "$CONFIG_VERSION" ] && dpkg --compare-versions "$PACKAGED_VERSION" gt "$CONFIG_VERSION"; then
        # Update X-Road configuration on startup, if necessary
        log "Updating configuration from $CONFIG_VERSION to $PACKAGED_VERSION"
        cp -a /root/etc/xroad/* /etc/xroad/
        pg_ctlcluster 18 main start
        wait_db
        dpkg-reconfigure xroad-center
        pg_ctlcluster 18 main stop
        nginx -s stop
        sleep 1
        echo "$PACKAGED_VERSION" >/etc/xroad/version
    fi
else
    echo "WARN: Installed version ($INSTALLED_VERSION) does not match packaged version ($PACKAGED_VERSION)" >&2
fi

if [  -n "$XROAD_TOKEN_PIN" ]
then
    log "XROAD_TOKEN_PIN variable set, writing to /etc/xroad/autologin"
    echo "$XROAD_TOKEN_PIN" > /etc/xroad/autologin
    unset XROAD_TOKEN_PIN
fi

pg_isready -q -t 2 || pg_ctlcluster 18 main start
wait_db
TOKEN_ROWS=$(su -c "psql -qtA centerui_production -c \"SET ROLE centerui; SELECT count(*) FROM configuration_properties \
  WHERE property_key IN ('xroad.registration-service.api-token', 'xroad.management-service.api-token');\"" postgres)
if [ "$TOKEN_ROWS" != "2" ]; then
  log "Creating API token for registration/management service..."
  TOKEN=$(tr -C -d "[:alnum:]" </dev/urandom | head -c32)
  ENCODED=$(echo -n "$TOKEN" | sha256sum -b | cut -d' ' -f1)
  su -c "psql -q centerui_production" postgres <<EOF
SET ROLE centerui;
DO \$\$
DECLARE
  id bigint;
BEGIN
  SELECT nextval('hibernate_sequence') INTO id;
  INSERT INTO apikey values (id, '$ENCODED');
  INSERT INTO apikey_roles values (nextval('apikey_roles_id_seq'), id, 'XROAD_MANAGEMENT_SERVICE');
END
\$\$
;
INSERT INTO configuration_properties (property_key, property_value, created_at, updated_at) VALUES
('xroad.registration-service.api-token', '$TOKEN', now(), now()),
('xroad.management-service.api-token', '$TOKEN', now(), now())
ON CONFLICT DO NOTHING;
EOF
fi
pg_ctlcluster 18 main stop

log "Enabling public postgres access.."
sed -i 's/#listen_addresses = \x27localhost\x27/listen_addresses = \x27*\x27/g' /etc/postgresql/*/main/postgresql.conf
sed -ri 's/host    replication     all             127.0.0.1\/32/host    all             all             0.0.0.0\/0/g' /etc/postgresql/*/main/pg_hba.conf

# Apply PostgreSQL performance optimizations if enabled
if [ "${POSTGRES_PERFORMANCE_TUNING:-true}" = "true" ]; then
  log "Applying PostgreSQL performance optimizations for single-session performance.."
  POSTGRES_CONF="/etc/postgresql/18/main/postgresql.conf"
  PERF_CONF="/etc/postgresql/18/main/postgresql-performance.conf"
  
  if [ -f "$PERF_CONF" ]; then
    # Include performance config if not already included
    if ! grep -q "include.*postgresql-performance.conf" "$POSTGRES_CONF"; then
      echo "" >> "$POSTGRES_CONF"
      echo "# Include performance optimizations" >> "$POSTGRES_CONF"
      echo "include = 'postgresql-performance.conf'" >> "$POSTGRES_CONF"
    fi
    log "PostgreSQL performance configuration applied"
  else
    warn "PostgreSQL performance config file not found at $PERF_CONF"
  fi
else
  log "PostgreSQL performance tuning disabled (set POSTGRES_PERFORMANCE_TUNING=false to disable)"
fi

# Load OpenBao environment file
if [ -f /etc/openbao/openbao.env ]; then
  log "Loading OpenBao environment variables"
  set -a
  . /etc/openbao/openbao.env
  set +a
fi

# Trust the dev dataspace HTTPS certificate when present (e2e mounts it) so the
# co-located Issuer Service can resolve security-server DIDs over HTTPS during
# credential issuance. OpenBao's own CA is already in the default JVM truststore.
DS_HTTPS_CERT="/etc/xroad/ds-ssl/ds-https-cert.pem"
if [ -f "$DS_HTTPS_CERT" ]; then
  JVM_CACERTS=$(find /usr/lib/jvm -name cacerts 2>/dev/null | head -1)
  if [ -n "$JVM_CACERTS" ] && ! keytool -list -keystore "$JVM_CACERTS" -storepass changeit -alias ds-https >/dev/null 2>&1; then
    log "Importing dataspace HTTPS certificate into JVM truststore"
    keytool -importcert -noprompt -trustcacerts -alias ds-https -file "$DS_HTTPS_CERT" \
      -keystore "$JVM_CACERTS" -storepass changeit || warn "Failed to import ds-https certificate"
  fi
fi

seed_configuration_properties

exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
