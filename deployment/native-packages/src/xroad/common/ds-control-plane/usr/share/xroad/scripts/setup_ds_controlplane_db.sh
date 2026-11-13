#!/bin/bash

if [[ -v XROAD_IGNORE_DATABASE_SETUP ]]; then
  echo >&2 "XROAD_IGNORE_DATABASE_SETUP set, ignoring database setup"
  exit 0
fi

source /usr/share/xroad/scripts/_setup_db.sh

default_host=$1
if [[ -z "$default_host" ]]; then
    url=$(get_prop '/etc/xroad/db.properties' 'xroad.db.ds-control-plane.hibernate.connection.url' '')
    pat='^jdbc:postgresql://([^/]*).*'
    if [[ "$url" =~ $pat ]]; then
        default_host="${BASH_REMATCH[1]}"
    fi
fi

setup_database "ds-control-plane" "ds-control-plane" "$default_host"

db_properties=/etc/xroad/db.properties
url_key="xroad.db.ds-control-plane.hibernate.connection.url"
url=$(get_prop "$db_properties" "$url_key" '')
if [[ -n "$url" && "$url" != *currentSchema=* ]]; then
    schema=$(get_prop "$db_properties" "xroad.db.ds-control-plane.hibernate.hikari.dataSource.currentSchema" 'ds-control-plane')
    schema="${schema%%,*}"
    separator="$([[ "$url" == *"?"* ]] && echo "&" || echo "?")"
    crudini --set "$db_properties" '' "$url_key" "${url}${separator}currentSchema=${schema}"
fi
