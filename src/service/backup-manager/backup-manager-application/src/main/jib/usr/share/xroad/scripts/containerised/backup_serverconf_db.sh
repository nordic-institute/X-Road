#!/bin/bash

dump_file="$1"

db_host="${XROAD_SERVERCONF_DB_HOST:-db-serverconf}"
db_port="${XROAD_SERVERCONF_DB_PORT:-5432}"
db_database="${XROAD_SERVERCONF_DB_DATABASE:-serverconf}"
db_schema="${XROAD_SERVERCONF_DB_SCHEMA:-public}"
db_user="${XROAD_SERVERCONF_DB_USER:-serverconf}"
db_password="${XROAD_SERVERCONF_DB_PASSWORD}"

pg_options="-c client-min-messages=warning -c search_path=$db_schema,public"

if [[ ! -z $PGOPTIONS_EXTRA ]]; then
  PGOPTIONS_EXTRA=" ${PGOPTIONS_EXTRA}"
fi

PGOPTIONS="$pg_options${PGOPTIONS_EXTRA-}" PGPASSWORD="${db_password}" PGHOST="${PGHOST:-$db_host}" PGPORT="${PGPORT:-$db_port}" \
    pg_dump -n "$db_schema" -x -O -F c -U "$db_user" -f "$dump_file" "$db_database"
