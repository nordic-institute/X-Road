#!/bin/bash
#
# Database setup
#

get_prop() {
  local tmp="$(crudini --get "$1" '' "$2" 2>/dev/null)"
  echo "${tmp:-$3}"
}

init_local_postgres() {
    if [ -f /etc/xroad/xroad.properties ]; then
      local -r root_properties=/etc/xroad/xroad.properties
    else
      local -r root_properties=/etc/xroad.properties
    fi
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
        PGSETUP_INITDB_OPTIONS="--auth-host=md5 -E UTF8" postgresql-setup --initdb || return 1
    fi

    # ensure that PostgreSQL is running
    systemctl start $SERVICE_NAME || return 1

    # Verify PostgreSQL version meets minimum requirement
    local MIN_PG_VERSION=15
    local pg_version_num
    pg_version_num=$(su -l -c "psql -tAc 'SHOW server_version_num'" postgres 2>/dev/null | tr -d '[:space:]')
    if [ -n "$pg_version_num" ]; then
        local pg_major_version=$((pg_version_num / 10000))
        if [ "$pg_major_version" -lt "$MIN_PG_VERSION" ]; then
            echo "ERROR: PostgreSQL version $pg_major_version is not supported. Minimum required version is $MIN_PG_VERSION."
            echo "Please enable PostgreSQL $MIN_PG_VERSION module stream: dnf module enable postgresql:$MIN_PG_VERSION"
            return 1
        fi
    fi
}

init_local_postgres
