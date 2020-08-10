#!/bin/bash
#
# Database setup
#
is_rhel8() {
    ([[ -f /etc/os-release ]] && source /etc/os-release && [[ "$ID_LIKE" == *rhel* && "$VERSION_ID" == 8* ]])
}

get_prop() {
  local tmp="$(crudini --get "$1" '' "$2" 2>/dev/null)"
  echo "${tmp:-$3}"
}

init_local_postgres() {
    local root_properties=/etc/xroad.properties
    SERVICE_NAME=postgresql

    if [[ -f ${root_properties} && $(get_prop ${root_properties} postgres.connection.password) != "" ]]; then
      # using remote db
      return 0
    fi

    # check if postgres is already running
    systemctl -q is-active $SERVICE_NAME && return 0

    # Copied from postgresql-setup. Determine default data directory
    PGDATA=$(systemctl -q show -p Environment "${SERVICE_NAME}.service" | sed 's/^Environment=//' | tr ' ' '\n' | sed -n 's/^PGDATA=//p' | tail -n 1)
    if [ -z "$PGDATA" ]; then
        echo "failed to find PGDATA setting in ${SERVICE_NAME}.service"
        return 1
    fi

    if [ ! -e "$PGDATA/PG_VERSION" ]; then
        if is_rhel8; then
            cmd="--initdb"
        else
            cmd="initdb"
        fi
        PGSETUP_INITDB_OPTIONS="--auth-host=md5 -E UTF8" postgresql-setup $cmd || return 1
    fi

    # ensure that PostgreSQL is running
    systemctl start $SERVICE_NAME || return 1
}

init_local_postgres
/usr/share/xroad/scripts/setup_opmonitor_db.sh

exit 0
