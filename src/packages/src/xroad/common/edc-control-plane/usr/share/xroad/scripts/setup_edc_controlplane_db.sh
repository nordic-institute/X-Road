#!/bin/bash


if [[ -v XROAD_IGNORE_DATABASE_SETUP ]]; then
  echo >&2 "XROAD_IGNORE_DATABASE_SETUP set, ignoring database setup"
  exit 0
fi

source /usr/share/xroad/scripts/_setup_db.sh

default_host=$1
if [[ -z "$default_host" ]]; then
    url=$(get_prop '/etc/xroad/db.properties' 'serverconf.hibernate.connection.url' '')
    pat='^jdbc:postgresql://([^/]*).*'
    if [[ "$url" =~ $pat ]]; then
        default_host="${BASH_REMATCH[1]}"
    fi
fi

setup_database "edc-control-plane" "edc-control-plane" "$default_host"

#url=$(crudini --get /etc/xroad/db.properties '' edc-control-plane.hibernate.connection.url)
#user=$(crudini --get /etc/xroad/db.properties '' edc-control-plane.hibernate.connection.username)
#password=$(crudini --get /etc/xroad/db.properties '' edc-control-plane.hibernate.connection.password)

#export XROAD_DS_PASSWORD=$password
