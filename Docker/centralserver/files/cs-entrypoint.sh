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
        pg_ctlcluster 16 main start
        wait_db
        dpkg-reconfigure xroad-center
        pg_ctlcluster 16 main stop
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

if ! crudini --get /etc/xroad/conf.d/local.ini registration-service api-token &>/dev/null; then
  log "Creating API token for registration service..."
  TOKEN=$(tr -C -d "[:alnum:]" </dev/urandom | head -c32)
  ENCODED=$(echo -n "$TOKEN" | sha256sum -b | cut -d' ' -f1)
  pg_ctlcluster 16 main start
  wait_db
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
EOF
  pg_ctlcluster 16 main stop
  crudini --set /etc/xroad/conf.d/local.ini registration-service api-token "$TOKEN"
  crudini --set /etc/xroad/conf.d/local.ini management-service api-token "$TOKEN"
fi

log "Making sure that token pin policy is enforced by default"
if ! crudini --get /etc/xroad/conf.d/local.ini signer enforce-token-pin-policy &>/dev/null; then
  crudini --set /etc/xroad/conf.d/local.ini signer enforce-token-pin-policy "true"
fi

log "Enabling public postgres access.."
sed -i 's/#listen_addresses = \x27localhost\x27/listen_addresses = \x27*\x27/g' /etc/postgresql/*/main/postgresql.conf
sed -ri 's/host    replication     all             127.0.0.1\/32/host    all             all             0.0.0.0\/0/g' /etc/postgresql/*/main/pg_hba.conf

log "initializing transport keys"
mkdir -p -m0750 /var/run/xroad
chown xroad:xroad /var/run/xroad
su - xroad -c sh -c /usr/share/xroad/scripts/xroad-base.sh

exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
