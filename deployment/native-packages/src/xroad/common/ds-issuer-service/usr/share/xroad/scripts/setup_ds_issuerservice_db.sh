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

issuer_pw=$(crudini --get "$ROOT_PROPS" '' "${DB_NAME}.database.admin_password" 2>/dev/null || true)
if [[ -z "$issuer_pw" ]]; then
  issuer_pw="$(gen_pw)"
  if [[ ! -f "$ROOT_PROPS" ]]; then
    touch "$ROOT_PROPS"
    chown root:root "$ROOT_PROPS"
    chmod 600 "$ROOT_PROPS"
  fi
  crudini --set --inplace "$ROOT_PROPS" '' "${DB_NAME}.database.admin_user" "$ROLE_NAME"
  crudini --set --inplace "$ROOT_PROPS" '' "${DB_NAME}.database.admin_password" "$issuer_pw"
fi

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

psql_master -d postgres <<SQL || die "Failed to provision role $ROLE_NAME"
DO \$do\$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$ROLE_NAME') THEN
    CREATE ROLE "$ROLE_NAME" LOGIN PASSWORD '$issuer_pw';
  ELSE
    ALTER ROLE "$ROLE_NAME" WITH LOGIN PASSWORD '$issuer_pw';
  END IF;
END
\$do\$;
SQL

psql_master -d "$db_database" <<SQL || die "Failed to provision schema $SCHEMA_NAME in $db_database"
CREATE SCHEMA IF NOT EXISTS "$SCHEMA_NAME" AUTHORIZATION "$ROLE_NAME";
GRANT USAGE ON SCHEMA "$SCHEMA_NAME" TO "$ROLE_NAME";
GRANT CONNECT ON DATABASE "$db_database" TO "$ROLE_NAME";
SQL

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
