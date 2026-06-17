#!/bin/bash

dump_file="$1"

# Source environment file to get database credentials
if [[ -f /etc/openbao/openbao.env ]]; then
  source /etc/openbao/openbao.env
else
  echo "Unable to find OpenBao environment file at /etc/openbao/openbao.env"
  exit 1
fi

# Use individual variables from env file
db_conn_user="${BAO_PG_USER}"
db_user=${db_conn_user%%@*}
db_password="${BAO_PG_PASSWORD}"
db_addr="${BAO_PG_HOST}"
db_port="${BAO_PG_PORT}"
db_database="${BAO_PG_DATABASE}"
db_schema="${BAO_PG_SCHEMA:-public}"

pg_options="-c client-min-messages=warning -c search_path=$db_schema"

# Reading custom libpq ENV variables
if [ -f /etc/xroad/db_libpq.env ]; then
  source /etc/xroad/db_libpq.env
fi

if [[ ! -z $PGOPTIONS_EXTRA ]]; then
  PGOPTIONS_EXTRA=" ${PGOPTIONS_EXTRA}"
fi

PGOPTIONS="$pg_options${PGOPTIONS_EXTRA-}" PGPASSWORD="${db_password}" PGHOST="${PGHOST:-$db_addr}" PGPORT="${PGPORT:-$db_port}" \
    pg_dump -n "$db_schema" -x -O -F c -U "$db_user" -f "$dump_file" "$db_database"
