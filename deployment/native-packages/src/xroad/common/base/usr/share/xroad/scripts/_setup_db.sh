#!/bin/bash

log() { echo >&2 "$@"; }
die() {
  log "$@"
  exit 1
}

MIN_PG_VERSION=15

# Waits for the database to answer and refuses a PostgreSQL older than MIN_PG_VERSION.
# Arguments: $1 - name of the psql function to query the database with, called as "$1 -tA -c <sql>"
check_pg_version() {
  local -r psql_fn="$1"
  local pg_version_num=""
  local i

  log "Checking PostgreSQL readiness and version..."

  for i in {1..30}; do
    pg_version_num=$(PGCONNECT_TIMEOUT=5 "$psql_fn" -tA -c "SHOW server_version_num" 2>/dev/null | tr -d '[:space:]')

    if [[ "$pg_version_num" =~ ^[0-9]+$ ]]; then
      break
    fi

    log "PostgreSQL not ready yet (attempt $i)..."
    sleep 1
  done

  if [[ -z "$pg_version_num" ]]; then
    die "Unable to determine PostgreSQL version. Minimum required version is $MIN_PG_VERSION."
  fi

  local pg_major_version=$((pg_version_num / 10000))

  if [ "$pg_major_version" -lt "$MIN_PG_VERSION" ]; then
    die "PostgreSQL version $pg_major_version is not supported. Minimum required version is $MIN_PG_VERSION."
  fi

  log "PostgreSQL version $pg_major_version OK"
}

# Refuses a database that does not accept writes, such as a standby or a read-only endpoint.
# An existing configuration with a host list only gets a warning, because only the first host is checked.
# Arguments: $1 - name of the psql function to query the database with, $2 - database address, named in the message,
# $3 - optional, existing for an address that was configured before
check_db_writable() {
  local -r psql_fn="$1" address="$2" state="${3:-}"
  local read_only

  read_only=$(PGCONNECT_TIMEOUT=5 "$psql_fn" -tA -c "SHOW transaction_read_only" 2>/dev/null | tr -d '[:space:]')

  if [[ "$read_only" != "off" ]]; then
    if [[ "$state" == existing && "$address" == *,* ]]; then
      log "Warning: the first host '${address%%,*}' of the database address '$address' does not accept writes. Only the first host was checked. Make sure that the address follows the primary, or use one write address instead."
      return 0
    fi
    die "The database at '$address' does not accept writes. Use the write address of the database: the primary, a virtual IP, a connection pooler, a managed endpoint or a Kubernetes read-write service."
  fi

  log "Database at '$address' accepts writes"
}

# Refuses a host list in a new database address. OpenBao and the setup steps use only the first host, so they miss a failover.
# An existing configuration with a host list only gets a warning.
# Arguments: $1 - the host part of the address, host[:port][,host[:port]], $2 - fresh or existing
check_db_host_answer() {
  local -r hosts="$1" state="$2"

  [[ "$hosts" == *,* ]] || return 0

  if [[ "$state" == fresh ]]; then
    die "The database address '$hosts' is a host list. Give one write address: a virtual IP, a connection pooler, a managed endpoint or a Kubernetes read-write service."
  fi

  log "Warning: the database address '$hosts' is a host list. OpenBao uses only the first host, so it does not follow a failover. Use one write address instead."
}

get_prop() {
  crudini --get "$1" '' "$2" 2>/dev/null || echo -n "$3"
}

gen_pw() {
  head -c 24 /dev/urandom | base64 | tr "/+" "_-"
}

DB_SQL_DIR=/usr/share/xroad/db

# Quotes a value as a psql single-quoted \set argument.
psql_quote() {
  local value="${1//\\/\\\\}"
  printf "'%s'" "${value//\'/\'\'}"
}

# Runs the shared bootstrap SQL through psql_master, which the caller defines.
# Arguments are name=value pairs, the psql variables documented in bootstrap-roles.sql.
run_db_bootstrap() {
  local pair file
  for file in bootstrap-roles.sql bootstrap-schema.sql; do
    [[ -r "$DB_SQL_DIR/$file" ]] || {
      log "Bootstrap SQL file $DB_SQL_DIR/$file is missing or unreadable."
      return 1
    }
  done
  {
    for pair in "$@"; do
      printf '\\set %s %s\n' "${pair%%=*}" "$(psql_quote "${pair#*=}")"
    done
    cat "$DB_SQL_DIR/bootstrap-roles.sql" "$DB_SQL_DIR/bootstrap-schema.sql"
  } | psql_master
}

# Logs a message naming the role and returns 1 when the runtime role cannot log in. The caller defines psql_dbuser.
# Arguments: $1 - runtime role, $2 - properties file holding its stored password
check_runtime_login() {
  PGCONNECT_TIMEOUT=5 psql_dbuser -c "\q" &>/dev/null && return 0
  log "Logging in as database role '$1' failed after database setup. If the role already existed, its password differs from the one in $2: set the stored password to match the role and rerun."
  return 1
}

# Succeeds when the database exists. The caller defines psql_master.
# Arguments: $1 - database name
database_exists() {
  local -r name="${1//\'/\'\'}"
  [[ "$(printf "SELECT 1 FROM pg_database WHERE datname = '%s';\n" "$name" | psql_master 2>/dev/null)" == 1 ]]
}

# Logs a message and returns 1 when the runtime login failed although the database exists and a password is stored for it.
# The caller then stops before changing anything. The caller defines psql_master.
# Arguments: $1 - runtime role, $2 - database name, $3 - properties file, $4 - stored runtime password
refuse_existing_database() {
  [[ -n "$4" ]] || return 0
  database_exists "$2" || return 0
  log "Logging in as database role '$1' failed. Database '$2' already exists and $3 stores a password for it, so nothing was changed. Set the stored password to match the role and rerun."
  return 1
}

setup_database() {
  local -r db_name="$1"
  local -r db_default_user="$2"
  local -r db_properties=/etc/xroad/db.properties
  if [ -f /etc/xroad/xroad.properties ]; then
    local -r root_properties=/etc/xroad/xroad.properties
  else
    local -r root_properties=/etc/xroad.properties
  fi
  get_db_prop() { get_prop "$db_properties" "xroad.db.$db_name.hibernate.connection.$1" "$2"; }

  local db_host="${3:-127.0.0.1:5432}"
  local db_master_conn_user="$(get_prop ${root_properties} postgres.connection.user 'postgres')"
  local db_master_user="${db_master_conn_user%%@*}"
  local suffix="${db_master_conn_user##$db_master_user}"

  local db_conn_user=$(get_db_prop 'username' "${db_default_user}${suffix}")
  local db_user="${db_conn_user%%@*}"
  local db_schema=$(get_prop ${db_properties} "xroad.db.$db_name.hibernate.hikari.dataSource.currentSchema" 'public')
  db_schema="${db_schema%%,*}"
  local db_password=$(get_db_prop 'password' "$(gen_pw)")
  local db_url=$(get_db_prop 'url' "jdbc:postgresql://$db_host/$db_name")
  local db_database="$db_name"
  local db_options

  local db_admin_conn_user=$(get_prop ${root_properties} "$db_name.database.admin_user")
  local db_admin_password=$(get_prop ${root_properties} "$db_name.database.admin_password")
  if [[ -z "$db_admin_conn_user" && -n "$db_admin_password" ]]; then
    db_admin_conn_user="${db_user}_admin$suffix"
  else
    db_admin_conn_user="${db_admin_conn_user:-$db_conn_user}"
    db_admin_password="${db_admin_password:-$db_password}"
  fi
  local db_admin_user="${db_admin_conn_user%%@*}"

  # Reading custom libpq ENV variables
  if [ -f /etc/xroad/db_libpq.env ]; then
    source /etc/xroad/db_libpq.env
  fi

  if [[ ! -z $PGOPTIONS_EXTRA ]]; then
    PGOPTIONS_EXTRA=" ${PGOPTIONS_EXTRA}"
  fi

  export PGOPTIONS="-c client-min-messages=warning -c search_path=$db_schema,public${PGOPTIONS_EXTRA-}"

  pat='^jdbc:postgresql://([^/]*)($|/([^\?]*)(.*)$)'
  if [[ "$db_url" =~ $pat ]]; then
    db_host="${BASH_REMATCH[1]:-$db_host}"
    #match 2 unused
    db_database="${BASH_REMATCH[3]:-$db_database}"
    db_options="${BASH_REMATCH[4]:-$db_options}"
  else
    log "Unable to parse '$db_url', using 'jdbc:postgresql://$db_host/$db_database$db_options'"
  fi

  local hosts
  IFS=',' read -ra hosts <<<"$db_host"
  local db_addr=${hosts[0]%%:*}
  local db_port=${hosts[0]##*:}

  local_psql() { su -l -c "psql -qtA -p ${PGPORT:-$db_port} ${*@Q}" postgres; }
  remote_psql() { psql -h "${PGHOST:-$db_addr}" -p "${PGPORT:-$db_port}" -qtA "$@"; }

  psql_dbuser() {
    PGDATABASE="$db_database" PGUSER="$db_conn_user" PGPASSWORD="$db_password" remote_psql "$@"
  }

  psql_adminuser() {
    PGDATABASE="$db_database" PGUSER="$db_admin_conn_user" PGPASSWORD="$db_admin_password" remote_psql "$@"
  }

  if [[ -f ${root_properties} && $(get_prop ${root_properties} postgres.connection.password) != "" ]]; then
    local db_master_passwd=$(get_prop ${root_properties} postgres.connection.password)
    function psql_master() {
      PGDATABASE="postgres" PGPASSWORD="${db_master_passwd}" PGUSER="${db_master_conn_user}" remote_psql "$@"
    }
  else
    function psql_master() { local_psql "$@"; }
  fi

  check_pg_version psql_master

  if PGCONNECT_TIMEOUT=5 psql_dbuser -c "\q" &>/dev/null; then
    log "Database and user exists, skipping database creation."
  else
    refuse_existing_database "$db_user" "$db_database" "$db_properties" "$(get_db_prop 'password' '')" || exit 1

    if [[ $(get_prop ${root_properties} "$db_name.database.admin_user" "") == "" ]]; then
      db_admin_user="${db_user}_admin"
      db_admin_conn_user="${db_admin_user}${suffix}"
    fi

    if [[ $(get_prop ${root_properties} "$db_name.database.admin_password" "") == "" ]]; then
      db_admin_password="$(gen_pw)"
    fi

    if [[ ! -f "$root_properties" ]]; then
      touch ${root_properties}
      chown root:root ${root_properties}
      chmod 600 ${root_properties}
    fi

    if [[ $db_schema == "public" ]]; then
      db_schema="$db_user"
    fi

    local single_role=off
    if [ "$db_user" == "$db_admin_user" ]; then
      single_role=on
    fi

    run_db_bootstrap \
      db_name="$db_database" \
      admin_role="$db_admin_user" \
      admin_password="$db_admin_password" \
      runtime_role="$db_user" \
      runtime_password="$db_password" \
      master_role="$db_master_user" \
      schema_name="$db_schema" \
      hstore=off \
      single_role="$single_role" ||
      die "Creating database '${db_database}' on '${db_host}' failed, please check database availability and configuration in ${db_properties} and ${root_properties}"

    if [ -w "$root_properties" ]; then
      crudini --set --inplace "${root_properties}" '' "$db_name.database.admin_user" "${db_admin_conn_user}"
      crudini --set --inplace "${root_properties}" '' "$db_name.database.admin_password" "${db_admin_password}"
    fi
  fi

  if [[ ! -f "$db_properties" ]]; then
    touch ${db_properties}
    chown xroad:xroad ${db_properties}
    chmod 640 ${db_properties}
  fi

  if [ -w "$db_properties" ]; then
    crudini --set ${db_properties} '' "xroad.db.$db_name.hibernate.jdbc.use_streams_for_binary" true
    crudini --set ${db_properties} '' "xroad.db.$db_name.hibernate.connection.driver_class" org.postgresql.Driver
    crudini --set ${db_properties} '' "xroad.db.$db_name.hibernate.connection.url" "jdbc:postgresql://$db_host/$db_database$db_options"
    if [[ $db_schema != "public" ]]; then
      crudini --set ${db_properties} '' "xroad.db.$db_name.hibernate.hikari.dataSource.currentSchema" "${db_schema},public"
    fi
    crudini --set ${db_properties} '' "xroad.db.$db_name.hibernate.connection.username" "${db_conn_user}"
    crudini --set ${db_properties} '' "xroad.db.$db_name.hibernate.connection.password" "${db_password}"
  fi

  check_runtime_login "$db_user" "$db_properties" || exit 1

  context="--contexts=user"
  if [[ "$db_user" != "$db_admin_user" ]]; then
    context="--contexts=admin"
  fi

  url_concat_string="$([[ "$db_url" == *"?"* ]] && echo "&" || echo "?")"

  LIQUIBASE_COMMAND_PASSWORD="${db_admin_password}" /usr/share/xroad/db/liquibase.sh \
    --changelog=$db_name \
    --url="${db_url}${url_concat_string}currentSchema=${db_schema},public" \
    --username="${db_admin_conn_user}" \
    --defaultSchemaName="${db_schema}" \
    --prop-db-user="${db_user}" \
    $context \
    update ||
    die "Running database migrations failed, please check database availability and configuration in ${db_properties} and ${root_properties}"
}
