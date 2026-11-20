#!/bin/bash

. /usr/share/xroad/scripts/_openbao_env.sh

dump_file="$1"
env_file=/etc/openbao/xroad-openbao.env

if [[ ! -f $env_file ]]; then
  echo "Unable to find OpenBao environment file at $env_file"
  exit 1
fi

# Read the database credentials from the env file as data
db_conn_user=$(read_env_value "$env_file" BAO_PG_USER)
db_user=${db_conn_user%%@*}
db_password=$(read_env_value "$env_file" BAO_PG_PASSWORD)
db_addr=$(read_env_value "$env_file" BAO_PG_HOST)
db_port=$(read_env_value "$env_file" BAO_PG_PORT)
db_database=$(read_env_value "$env_file" BAO_PG_DATABASE)
db_schema=$(read_env_value "$env_file" BAO_PG_SCHEMA)
db_schema="${db_schema:-public}"

require_env_name BAO_PG_USER "$db_conn_user"
require_env_name BAO_PG_DATABASE "$db_database"
require_env_name BAO_PG_SCHEMA "$db_schema"

pg_options="-c client-min-messages=warning -c search_path=$db_schema"

# Reading custom libpq ENV variables
if [ -f /etc/xroad/db_libpq.env ]; then
  source /etc/xroad/db_libpq.env
fi

if [[ ! -z $PGOPTIONS_EXTRA ]]; then
  PGOPTIONS_EXTRA=" ${PGOPTIONS_EXTRA}"
fi

PGOPTIONS="$pg_options${PGOPTIONS_EXTRA-}" PGPASSWORD="${db_password}" PGHOST="${PGHOST:-$db_addr}" PGPORT="${PGPORT:-$db_port}" \
    pg_dump -n "$db_schema" -x -O -F c -U "$db_user" -f "$dump_file" -d "$db_database"
