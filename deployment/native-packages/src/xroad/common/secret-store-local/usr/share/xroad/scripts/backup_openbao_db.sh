#!/bin/bash

dump_file="$1"

db_connection_url=$(awk '/storage "postgresql"/,/\}/' /etc/openbao/openbao.hcl | grep 'connection_url' | cut -d'"' -f2)
pat='^postgres:\/\/([^:]+):([^@]+)@([^:]+):([0-9]+)\/([^?]+)(\?(.*))?$'
if [[ $db_connection_url =~ $pat ]]; then
  db_conn_user="${BASH_REMATCH[1]}"
  db_user=${db_conn_user%%@*}
  db_password="${BASH_REMATCH[2]}"
  db_addr="${BASH_REMATCH[3]}"
  db_port="${BASH_REMATCH[4]}"
  db_database="${BASH_REMATCH[5]}"
  db_options="${BASH_REMATCH[7]}" # full query string after '?', if present
  schema_pat='(^|&)search_path=([^&]*)'
  if [[ $db_options =~ $schema_pat ]]; then
    db_schema="${BASH_REMATCH[2]}"
  else
    db_schema="public"
  fi
  pg_options="-c client-min-messages=warning -c search_path=$db_schema"
else
  echo "Unable to determine OpenBao PostgreSQL connection URL in /etc/openbao/openbao.hcl"
  exit 1
fi

# Reading custom libpq ENV variables
if [ -f /etc/xroad/db_libpq.env ]; then
  source /etc/xroad/db_libpq.env
fi

if [[ ! -z $PGOPTIONS_EXTRA ]]; then
  PGOPTIONS_EXTRA=" ${PGOPTIONS_EXTRA}"
fi

PGOPTIONS="$pg_options${PGOPTIONS_EXTRA-}" PGPASSWORD="${db_password}" PGHOST="${PGHOST:-$db_addr}" PGPORT="${PGPORT:-$db_port}" \
    pg_dump -n "$db_schema" -x -O -F c -U "$db_user" -f "$dump_file" "$db_database"
