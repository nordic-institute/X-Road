#!/bin/bash
#############################################################################
#
# X-Road Security Server sidecar secret-store bootstrap.
#
# Container-native equivalent of the xroad-secret-store-local package's
# systemd-driven install flow (secret-store-init-db.sh + secret-store-init.sh),
# run from the container entrypoint on every boot instead of once at package
# install time. Idempotent: safe to run against an already-initialized store,
# and against a store that is already unsealed with a valid client token.
#
# Contract:
#   in  - XROAD_SECRET_STORE_HOST/PORT/SCHEME/TOKEN (external override)
#         XROAD_DB_HOST/PORT, /etc/xroad/db.properties (embedded PostgreSQL
#         location, same source X-Road's own serverconf database uses)
#   out - embedded mode: OpenBao unsealed, xrd-pki/xrd-secret/xrd-ds-secret
#         mounts present, /etc/xroad/secret-store-client-token non-empty,
#         supervisord's openbao program enabled
#         external mode: supervisord's openbao program disabled, nothing else
#         touched
#
#############################################################################
set -eo pipefail

. /usr/share/xroad/scripts/sidecar/_openbao.sh

SECRET_STORE_DIR=/etc/xroad/secret-store
CLIENT_TOKEN_FILE=/etc/xroad/secret-store-client-token
DB_PASSWORD_FILE="$SECRET_STORE_DIR/openbao-db-password"
ROOT_TOKEN_FILE="$SECRET_STORE_DIR/root-token"
UNSEAL_KEYS_FILE="$SECRET_STORE_DIR/unseal-keys"
BAO_HCL=/etc/openbao/openbao.hcl
BAO_BIN=/usr/bin/bao
SUPERVISOR_XROAD_CONF=/etc/supervisor/conf.d/xroad.conf

log() { echo "$(date --utc -Iseconds) INFO [secret-store] $*"; }
warn() { echo "$(date --utc -Iseconds) WARN [secret-store] $*" >&2; }

set_openbao_program_autostart() {
  crudini --set --existing=section "$SUPERVISOR_XROAD_CONF" program:openbao autostart "$1" &>/dev/null || :
}

# The embedded-store CA cert this image bakes in is meaningless for an
# external store, which may not even use TLS the same way — drop the
# trust env vars so a missing/irrelevant cert file cannot break startup.
clear_embedded_ca_trust_env() {
  local program
  for program in xroad-signer xroad-confclient xroad-proxy xroad-monitor xroad-opmonitor; do
    crudini --set --existing=section "$SUPERVISOR_XROAD_CONF" "program:$program" \
      environment 'HOME="/var/lib/xroad"' &>/dev/null || :
  done
  crudini --set --existing=section "$SUPERVISOR_XROAD_CONF" program:xroad-proxy-ui-api \
    environment 'HOME="/var/lib/xroad"' &>/dev/null || :
}

if [ -n "${XROAD_SECRET_STORE_HOST:-}" ]; then
  log "External secret store configured at $XROAD_SECRET_STORE_HOST — embedded OpenBao stays stopped"
  set_openbao_program_autostart false
  clear_embedded_ca_trust_env
  exit 0
fi

mkdir -p -m 0750 "$SECRET_STORE_DIR"
chown xroad:xroad "$SECRET_STORE_DIR"

# The openbao package's own postinst generates /opt/openbao/tls/tls.{crt,key}
# with no Subject Alternative Name, which curl -k tolerates but a JVM client
# doing real hostname verification rejects (SSLHandshakeException). /opt/openbao
# is not a persisted volume, so this has to be redone every boot, not just once.
log "Generating OpenBao TLS certificate with IP SAN"
tls_tmp_dir=$(mktemp -d)
openssl req \
  -out "$tls_tmp_dir/tls.crt" \
  -new \
  -keyout "$tls_tmp_dir/tls.key" \
  -newkey rsa:4096 \
  -nodes \
  -sha256 \
  -x509 \
  -subj "/O=OpenBao/CN=OpenBao" \
  -days 1095 \
  -addext "subjectAltName = IP:127.0.0.1" \
  -addext "keyUsage = digitalSignature,keyEncipherment" \
  -addext "extendedKeyUsage = serverAuth"
chmod 640 "$tls_tmp_dir/tls.key" "$tls_tmp_dir/tls.crt"
chown openbao:openbao "$tls_tmp_dir/tls.key" "$tls_tmp_dir/tls.crt"
mv -f "$tls_tmp_dir/tls.key" "$tls_tmp_dir/tls.crt" /opt/openbao/tls/
rmdir "$tls_tmp_dir"

# Resolve the PostgreSQL host that X-Road's own serverconf database uses, so
# OpenBao's storage backend follows the same embedded-vs-external choice
# without needing its own env vars re-supplied on every restart.
db_hostport="${XROAD_DB_HOST:-127.0.0.1}:${XROAD_DB_PORT:-5432}"
if [ -r /etc/xroad/db.properties ]; then
  db_url=$(crudini --get /etc/xroad/db.properties '' serverconf.hibernate.connection.url 2>/dev/null || echo -n "")
  if [[ "$db_url" =~ ^jdbc:postgresql://([^/]*) ]]; then
    db_hostport="${BASH_REMATCH[1]}"
    [[ "$db_hostport" == *:* ]] || db_hostport="${db_hostport}:5432"
  fi
fi
db_addr="${db_hostport%%:*}"
db_port="${db_hostport##*:}"
local_db=false
[[ "$db_addr" == "127.0.0.1" ]] && local_db=true

get_root_prop() {
  local root_properties="/etc/xroad.properties"
  [ -f "$root_properties" ] || root_properties=/etc/xroad/xroad.properties
  crudini --get "$root_properties" '' "$1" 2>/dev/null || echo -n "$2"
}

psql_master() {
  if [ "$local_db" = "true" ]; then
    su -l -c "psql -qtA -p $db_port" postgres
  else
    local master_user master_password
    master_user=$(get_root_prop postgres.connection.user postgres)
    master_password=$(get_root_prop postgres.connection.password)
    PGDATABASE=postgres PGUSER="$master_user" PGPASSWORD="$master_password" \
      psql -h "$db_addr" -p "$db_port" -qtA "$@"
  fi
}

we_started_postgres=false
if [ "$local_db" = "true" ] && ! pg_isready -q -h "$db_addr" -p "$db_port"; then
  log "Starting local PostgreSQL for OpenBao storage setup"
  pg_ctlcluster 18 main start
  we_started_postgres=true
  count=0
  while ((count++ < 30)) && ! pg_isready -q -h "$db_addr" -p "$db_port"; do sleep 1; done
fi

stop_postgres_if_we_started_it() {
  if [ "$we_started_postgres" = "true" ]; then
    log "Stopping local PostgreSQL started for OpenBao storage setup"
    pg_ctlcluster 18 main stop
  fi
}
trap stop_postgres_if_we_started_it EXIT

if [ ! -f "$DB_PASSWORD_FILE" ]; then
  head -c 24 /dev/urandom | base64 | tr "/+" "_-" > "$DB_PASSWORD_FILE"
  chmod 600 "$DB_PASSWORD_FILE"
fi
bao_db_password=$(cat "$DB_PASSWORD_FILE")

if PGCONNECT_TIMEOUT=5 PGDATABASE=openbao PGUSER=openbao PGPASSWORD="$bao_db_password" \
    psql -h "$db_addr" -p "$db_port" -qtA -c '\q' &>/dev/null; then
  log "OpenBao storage database already exists"
else
  log "Creating OpenBao storage database"
  psql_master <<SQL
\set ON_ERROR_STOP on
CREATE DATABASE "openbao" ENCODING 'UTF8';
REVOKE ALL ON DATABASE "openbao" FROM PUBLIC;
DO \$\$
BEGIN
  CREATE ROLE "openbao" LOGIN PASSWORD '$bao_db_password';
  EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'Role openbao already exists';
END
\$\$;
GRANT CREATE,TEMPORARY,CONNECT ON DATABASE "openbao" TO "openbao";
\c "openbao"
CREATE SCHEMA IF NOT EXISTS "openbao" AUTHORIZATION "openbao";
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public to "openbao";
SQL
fi

cat >"$BAO_HCL" <<EOF
ui = true

storage "postgresql" {
  connection_url = "postgres://openbao:${bao_db_password}@${db_addr}:${db_port}/openbao?search_path=openbao"
}

listener "tcp" {
  address       = "0.0.0.0:8200"
  tls_cert_file = "/opt/openbao/tls/tls.crt"
  tls_key_file  = "/opt/openbao/tls/tls.key"
}
EOF
chown openbao:openbao "$BAO_HCL"
chmod 640 "$BAO_HCL"

install -m 0644 /opt/openbao/tls/tls.crt /etc/xroad/ssl/openbao.crt
chown xroad:xroad /etc/xroad/ssl/openbao.crt

cat >/etc/openbao/openbao.env <<EOF
BAO_PG_USER=openbao
BAO_PG_PASSWORD=${bao_db_password}
BAO_PG_HOST=${db_addr}
BAO_PG_PORT=${db_port}
BAO_PG_DATABASE=openbao
BAO_PG_SCHEMA=openbao
BAO_PG_CONNECTION_URL=postgres://openbao:${bao_db_password}@${db_addr}:${db_port}/openbao?search_path=openbao
EOF
chown openbao:xroad /etc/openbao/openbao.env
chmod 640 /etc/openbao/openbao.env

export BAO_ADDR="https://127.0.0.1:8200"

log "Starting temporary OpenBao instance for first-boot/unseal setup"
mkdir -p /var/log/xroad
"$BAO_BIN" server -config="$BAO_HCL" >>/var/log/xroad/openbao-init.log 2>&1 &
temp_bao_pid=$!

stop_temp_openbao() {
  if kill -0 "$temp_bao_pid" 2>/dev/null; then
    kill -INT "$temp_bao_pid" 2>/dev/null || true
    wait "$temp_bao_pid" 2>/dev/null || true
  fi
  stop_postgres_if_we_started_it
}
trap stop_temp_openbao EXIT

if wait_until_ready "$BAO_ADDR"; then
  log "OpenBao is ready"
else
  warn "Timed out waiting for OpenBao service to become ready"
  exit 1
fi

if is_initialized "$BAO_ADDR"; then
  log "OpenBao is already initialized"
else
  log "Initializing OpenBao..."
  INIT_RESPONSE=$(initialize "$BAO_ADDR") || {
    warn "Failed to initialize OpenBao"
    exit 1
  }
  ROOT_TOKEN=$(echo "$INIT_RESPONSE" | jq -r '.root_token')
  UNSEAL_KEYS=$(echo "$INIT_RESPONSE" | jq -r '.keys_base64[]')
  echo "$UNSEAL_KEYS" >"$UNSEAL_KEYS_FILE"
  echo "$ROOT_TOKEN" >"$ROOT_TOKEN_FILE"
  chmod 600 "$ROOT_TOKEN_FILE" "$UNSEAL_KEYS_FILE"
fi

sealed_rc=0
is_sealed "$BAO_ADDR" || sealed_rc=$?
if [ "$sealed_rc" -eq 2 ]; then
  warn "Cannot determine OpenBao seal status; aborting"
  exit 1
elif [ "$sealed_rc" -ne 0 ]; then
  log "OpenBao is already unsealed"
else
  log "Unsealing OpenBao..."
  while IFS= read -r key || [ -n "$key" ]; do
    if ! unseal "$BAO_ADDR" "$key"; then
      warn "Failed to unseal OpenBao"
      exit 1
    fi
    sealed_rc=0
    is_sealed "$BAO_ADDR" || sealed_rc=$?
    if [ "$sealed_rc" -eq 2 ]; then
      warn "Cannot verify seal status after unseal; aborting"
      exit 1
    elif [ "$sealed_rc" -ne 0 ]; then
      log "Successfully unsealed OpenBao"
      break
    fi
  done <"$UNSEAL_KEYS_FILE"
fi

export BAO_TOKEN=${BAO_TOKEN:-$(cat "$ROOT_TOKEN_FILE")}

if curl -s -k -H "X-Vault-Token: $BAO_TOKEN" "$BAO_ADDR/v1/sys/mounts" | jq -e 'has("xrd-pki/")' >/dev/null; then
  log "PKI store already configured"
else
  log "Configuring PKI store..."
  configure_pki "$BAO_ADDR" "$BAO_TOKEN" || {
    warn "Failed to configure PKI"
    exit 1
  }
fi

log "Configuring KV stores..."
configure_kv "$BAO_ADDR" "$BAO_TOKEN" || {
  warn "Failed to configure KV store"
  exit 1
}

if dpkg -s xroad-ds-control-plane &>/dev/null || dpkg -s xroad-ds-identity-hub &>/dev/null; then
  log "Seeding DS-HTTPS TLS placeholder certificate"
  seed_ds_https_placeholder_cert "$BAO_ADDR" "$BAO_TOKEN" || {
    warn "Failed to seed DS-HTTPS TLS placeholder certificate"
    exit 1
  }
fi

regenerate_client_token=true
if [ -f "$CLIENT_TOKEN_FILE" ]; then
  EXISTING_TOKEN=$(cat "$CLIENT_TOKEN_FILE")
  http_status=$(curl -s -k -o /dev/null -w "%{http_code}" \
    --connect-timeout 5 --retry 3 --retry-delay 2 \
    -H "X-Vault-Token: $EXISTING_TOKEN" \
    "$BAO_ADDR/v1/auth/token/lookup-self")
  if [ "$http_status" = "200" ]; then
    log "X-Road client token is valid"
    regenerate_client_token=false
  else
    warn "Existing X-Road client token is invalid (HTTP $http_status), regenerating"
    rm -f "$CLIENT_TOKEN_FILE"
  fi
fi

if [ "$regenerate_client_token" = "true" ]; then
  log "Generating X-Road client token..."
  CLIENT_TOKEN=$(create_token "$BAO_ADDR" "$BAO_TOKEN" "xroad-policy" "0" "xroad-client" "${XROAD_SECRET_STORE_TOKEN_OVERRIDE:-}")
  if [ -z "$CLIENT_TOKEN" ]; then
    warn "Failed to create X-Road client token"
    exit 1
  fi
  echo "$CLIENT_TOKEN" >"$CLIENT_TOKEN_FILE"
  chmod 640 "$CLIENT_TOKEN_FILE"
  chown xroad:xroad "$CLIENT_TOKEN_FILE"
fi

trap - EXIT
stop_temp_openbao

set_openbao_program_autostart true

log "OpenBao secret-store bootstrap completed successfully"
