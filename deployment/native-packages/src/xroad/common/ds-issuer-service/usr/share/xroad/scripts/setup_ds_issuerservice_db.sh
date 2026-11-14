#!/bin/bash

if [[ -v XROAD_IGNORE_DATABASE_SETUP ]]; then
  echo >&2 "XROAD_IGNORE_DATABASE_SETUP set, ignoring database setup"
  exit 0
fi

source /usr/share/xroad/scripts/_read_cs_db_properties.sh

log() { echo >&2 "$@"; }
die() {
  log "$@"
  exit 1
}

get_prop() {
  crudini --get "$1" '' "$2" 2>/dev/null || echo -n "$3"
}

gen_pw() { head -c 24 /dev/urandom | base64 | tr "/+" "_-"; }

DB_NAME="ds-issuer-service"
SCHEMA_NAME="ds-issuer-service"
ROLE_NAME="ds-issuer-service"
ROOT_PROPS="/etc/xroad.properties"
DB_PROPS="/etc/xroad/db.properties"

# The Issuer Service schema lives in the Central Server database and is owned by the Central
# Server admin role. The Issuer Service itself runs as ROLE_NAME, which only has DML rights.
prepare_db_props

db_admin_user=$(get_prop "$ROOT_PROPS" 'centerui.database.admin_user' "$db_user")
db_admin_password=$(get_prop "$ROOT_PROPS" 'centerui.database.admin_password' "$db_password")
[[ -n "$db_admin_user" && -n "$db_admin_password" ]] ||
  die "No database credentials found for creating the Issuer Service schema: set centerui.database.admin_user and centerui.database.admin_password in ${ROOT_PROPS}"

url_concat_string="$([[ "$db_url" == *"?"* ]] && echo "&" || echo "?")"
jdbc_url="${db_url}${url_concat_string}currentSchema=${SCHEMA_NAME},public"

runtime_pw=$(get_prop "$DB_PROPS" "xroad.db.${DB_NAME}.hibernate.connection.password" "")

# Log in over TCP with the role's own credentials: a local socket connection can pass on peer or trust
# authentication without proving the stored password.
role_probe() {
  PGCONNECT_TIMEOUT=5 PGPASSWORD="$runtime_pw" psql -h "$db_host" -p "$db_port" -U "$ROLE_NAME" -d "$db_database" -qtA -c "\q" &>/dev/null
}

psql_admin() {
  PGPASSWORD="$db_admin_password" psql -h "$db_host" -p "$db_port" -U "$db_admin_user" -d "$db_database" -v ON_ERROR_STOP=1 -qtA "$@"
}

master_conn_user=$(get_prop "$ROOT_PROPS" postgres.connection.user 'postgres')
master_pw=$(get_prop "$ROOT_PROPS" postgres.connection.password "")
if [[ -n "$master_pw" ]]; then
  psql_master() {
    PGPASSWORD="$master_pw" psql -h "$db_host" -p "$db_port" -U "$master_conn_user" -v ON_ERROR_STOP=1 -qtA "$@"
  }
else
  psql_master() {
    su -l -c "psql -p ${PGPORT:-$db_port} -v ON_ERROR_STOP=1 -qtA ${*@Q}" postgres
  }
fi

role_ready=false
if [[ -n "$runtime_pw" ]] && role_probe; then
  log "Database role $ROLE_NAME already exists and stored credentials are valid, skipping role setup."
  role_ready=true
else
  role_exists=$(psql_master -d postgres -c "SELECT 1 FROM pg_roles WHERE rolname = '${ROLE_NAME}'") ||
    die "Failed to look up database role $ROLE_NAME"
  if [[ -n "$role_exists" ]]; then
    die "Database role $ROLE_NAME exists but its credentials in ${DB_PROPS} are missing or invalid. On a subsequent node of a shared database, copy ${DB_PROPS} from the first node, or set XROAD_IGNORE_DATABASE_SETUP to skip database setup."
  fi
  runtime_pw="${runtime_pw:-$(gen_pw)}"
fi

psql_admin <<SQL || die "Failed to create schema $SCHEMA_NAME in $db_database as $db_admin_user, check centerui.database.admin_user and centerui.database.admin_password in ${ROOT_PROPS}"
CREATE SCHEMA IF NOT EXISTS "$SCHEMA_NAME";
SQL

if [[ ! -f "$DB_PROPS" ]]; then
  touch "$DB_PROPS"
  chown xroad:xroad "$DB_PROPS"
  chmod 640 "$DB_PROPS"
fi

crudini --set "$DB_PROPS" '' "xroad.db.${DB_NAME}.hibernate.connection.driver_class" org.postgresql.Driver
crudini --set "$DB_PROPS" '' "xroad.db.${DB_NAME}.hibernate.connection.url" "$jdbc_url"
crudini --set "$DB_PROPS" '' "xroad.db.${DB_NAME}.hibernate.connection.username" "$ROLE_NAME"
crudini --set "$DB_PROPS" '' "xroad.db.${DB_NAME}.hibernate.connection.password" "$runtime_pw"
crudini --set "$DB_PROPS" '' "xroad.db.${DB_NAME}.hibernate.hikari.dataSource.currentSchema" "${SCHEMA_NAME},public"

if [[ "$role_ready" == false ]]; then
  psql_master -d postgres <<SQL || die "Failed to create role $ROLE_NAME"
CREATE ROLE "$ROLE_NAME" LOGIN PASSWORD '$runtime_pw';
GRANT TEMPORARY,CONNECT ON DATABASE "$db_database" TO "$ROLE_NAME";
\c "$db_database"
GRANT USAGE ON SCHEMA public TO "$ROLE_NAME";
SQL
fi

LIQUIBASE_COMMAND_PASSWORD="$db_admin_password" /usr/share/xroad/db/liquibase.sh \
  --changelog="$DB_NAME" \
  --url="$jdbc_url" \
  --username="$db_admin_user" \
  --defaultSchemaName="$SCHEMA_NAME" \
  --prop-db-user="$ROLE_NAME" \
  --contexts=admin \
  update ||
  die "Running database migrations failed, please check database availability and configuration in ${DB_PROPS}"
