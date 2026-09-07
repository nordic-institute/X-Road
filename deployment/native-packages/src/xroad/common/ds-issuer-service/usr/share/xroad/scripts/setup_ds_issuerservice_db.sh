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

gen_pw() { head -c 24 /dev/urandom | base64 | tr "/+" "_-"; }

DB_NAME="ds-issuer-service"
SCHEMA_NAME="ds-issuer-service"
ROLE_NAME="ds-issuer-service"
ROOT_PROPS="/etc/xroad.properties"
DB_PROPS="/etc/xroad/db.properties"

prepare_db_props

stored_pw=$(crudini --get "$ROOT_PROPS" '' "${DB_NAME}.database.admin_password" 2>/dev/null || true)

# Role login is probed over TCP with the role's own credentials, not via the
# (possibly socket-based) master connection used for provisioning.
role_probe() {
  PGCONNECT_TIMEOUT=5 PGPASSWORD="$stored_pw" psql -h "$db_host" -p "$db_port" -U "$ROLE_NAME" -d "$db_database" -qtA -c "\q" &>/dev/null
}

if [[ -n "$stored_pw" ]] && role_probe; then
  log "Database role $ROLE_NAME already exists and stored credentials are valid, skipping database setup."
  issuer_pw="$stored_pw"
else
  master_user=$(crudini --get "$ROOT_PROPS" '' postgres.connection.user 2>/dev/null || echo "postgres")
  master_pw=$(crudini --get "$ROOT_PROPS" '' postgres.connection.password 2>/dev/null || true)

  if [[ -n "$master_pw" ]]; then
    psql_master() {
      PGPASSWORD="$master_pw" psql -h "$db_host" -p "$db_port" -U "$master_user" -v ON_ERROR_STOP=1 -qtA "$@"
    }
  else
    psql_master() {
      su -l -c "psql -p $db_port -v ON_ERROR_STOP=1 -qtA $*" postgres
    }
  fi

  issuer_pw="${stored_pw:-$(gen_pw)}"

  psql_master -d postgres <<SQL || die "Failed to create role $ROLE_NAME. If the role already exists on a shared database, preseed ${DB_NAME}.database.admin_user and ${DB_NAME}.database.admin_password in $ROOT_PROPS (copy from the first node), or set XROAD_IGNORE_DATABASE_SETUP to skip database setup."
CREATE ROLE "$ROLE_NAME" LOGIN PASSWORD '$issuer_pw';
SQL

  if [[ ! -f "$ROOT_PROPS" ]]; then
    touch "$ROOT_PROPS"
    chown root:root "$ROOT_PROPS"
    chmod 600 "$ROOT_PROPS"
  fi
  crudini --set --inplace "$ROOT_PROPS" '' "${DB_NAME}.database.admin_user" "$ROLE_NAME"
  crudini --set --inplace "$ROOT_PROPS" '' "${DB_NAME}.database.admin_password" "$issuer_pw"

  psql_master -d "$db_database" <<SQL || die "Failed to provision schema $SCHEMA_NAME in $db_database"
CREATE SCHEMA IF NOT EXISTS "$SCHEMA_NAME" AUTHORIZATION "$ROLE_NAME";
GRANT USAGE ON SCHEMA "$SCHEMA_NAME" TO "$ROLE_NAME";
GRANT CONNECT ON DATABASE "$db_database" TO "$ROLE_NAME";
SQL
fi

if [[ ! -f "$DB_PROPS" ]]; then
  touch "$DB_PROPS"
  chown xroad:xroad "$DB_PROPS"
  chmod 640 "$DB_PROPS"
fi

jdbc_url="jdbc:postgresql://${db_host}:${db_port}/${db_database}?currentSchema=${SCHEMA_NAME},public"
crudini --set "$DB_PROPS" '' "xroad.db.${DB_NAME}.hibernate.connection.driver_class" org.postgresql.Driver
crudini --set "$DB_PROPS" '' "xroad.db.${DB_NAME}.hibernate.connection.url" "$jdbc_url"
crudini --set "$DB_PROPS" '' "xroad.db.${DB_NAME}.hibernate.connection.username" "$ROLE_NAME"
crudini --set "$DB_PROPS" '' "xroad.db.${DB_NAME}.hibernate.connection.password" "$issuer_pw"
crudini --set "$DB_PROPS" '' "xroad.db.${DB_NAME}.hibernate.hikari.dataSource.currentSchema" "${SCHEMA_NAME},public"
