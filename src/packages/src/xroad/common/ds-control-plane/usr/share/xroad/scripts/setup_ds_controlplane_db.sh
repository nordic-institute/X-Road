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

<<<<<<<< HEAD:src/packages/src/xroad/common/ds-control-plane/usr/share/xroad/scripts/setup_ds_controlplane_db.sh
setup_database "ds-control-plane" "ds-control-plane" "$default_host"

========
setup_database "edc-data-plane" "edc-data-plane" "$default_host"
>>>>>>>> edc-poc:src/packages/src/xroad/common/edc-data-plane/usr/share/xroad/scripts/setup_edc_dataplane_db.sh
