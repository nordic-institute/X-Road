#!/bin/bash
if [[ -v XROAD_IGNORE_DATABASE_SETUP ]]; then
  echo >&2 "XROAD_IGNORE_DATABASE_SETUP set, ignoring database setup"
  exit 0
fi

source /usr/share/xroad/scripts/_setup_db.sh

setup_database() {

  node_type=$(crudini --get '/etc/xroad/conf.d/node.ini' node type 2>/dev/null || echo standalone)
  if [[ "$node_type" == "secondary" || "$node_type" == "slave" ]]; then
    log "Skipping database setup on cluster secondary node"
    return 0
  fi

  local -r db_properties=/etc/xroad/db.properties
  if [ -f /etc/xroad/xroad.properties ]; then
    local -r root_properties=/etc/xroad/xroad.properties
  else
    local -r root_properties=/etc/xroad.properties
  fi
  local -r db_name=serverconf

  local new_db_host="$1"
  local db_host="${new_db_host:-127.0.0.1:5432}"

  local db_master_conn_user=$(get_prop ${root_properties} postgres.connection.user 'postgres')
  local db_master_user=${db_master_conn_user%%@*}
  local suffix="${db_master_conn_user##$db_master_user}"

  local db_conn_user=$(get_prop ${db_properties} "xroad.db.${db_name}.hibernate.connection.username" "${db_name}$suffix")
  local db_user=${db_conn_user%%@*}
  local db_schema=$(get_prop ${db_properties} "xroad.db.${db_name}.hibernate.hikari.dataSource.currentSchema" "${db_user},public")
  db_schema=${db_schema%%,*}
  local db_password=$(get_prop ${db_properties} "xroad.db.${db_name}.hibernate.connection.password" "$(gen_pw)")
  local db_url=$(get_prop ${db_properties} "xroad.db.${db_name}.hibernate.connection.url" "jdbc:postgresql://$db_host/${db_name}")
  local db_database=${db_name}
  local db_options

  local db_admin_conn_user=$(get_prop ${root_properties} "${db_name}.database.admin_user")
  local db_admin_password=$(get_prop ${root_properties} "${db_name}.database.admin_password")
  if [[ -z "$db_admin_conn_user" && -n "$db_admin_password" ]]; then
    db_admin_conn_user="${db_user}_admin${suffix}"
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
    db_host=${new_db_host:-${BASH_REMATCH[1]}}
    #match 2 unused
    db_database=${BASH_REMATCH[3]:-$db_database}
    db_options="${BASH_REMATCH[4]:-$db_options}"
  else
    log "Unable to parse '$db_url', using 'jdbc:postgresql://$db_host/$db_database$db_options'"
  fi

  local hosts
  IFS=',' read -ra hosts <<<"$db_host"
  local db_addr=${hosts[0]%%:*}
  local db_port=${hosts[0]##*:}

  local_psql() { su -l -c "psql -qtA -p ${PGPORT:-$db_port} $*" postgres; }
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

  if PGCONNECT_TIMEOUT=5 psql_dbuser -c "\q" &>/dev/null; then
    log "Database and user exists, skipping database creation."
  else
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
      hstore=on \
      single_role="$single_role" ||
      die "Creating database '${db_database}' on '${db_host}' failed, please check database availability and configuration in ${db_properties} and ${root_properties}"

    if [[ -w "$root_properties" ]]; then
      crudini --set --inplace "${root_properties}" '' "$db_name.database.admin_user" "${db_admin_conn_user}"
      crudini --set --inplace "${root_properties}" '' "$db_name.database.admin_password" "${db_admin_password}"
    else
      log "$root_properties is not writable"
    fi
  fi

  if [ ! -f "$db_properties" ]; then
    touch ${db_properties}
    chown xroad:xroad ${db_properties}
    chmod 640 ${db_properties}
  fi

  if [ -w "$db_properties" ]; then
    crudini --set ${db_properties} '' xroad.db.${db_name}.hibernate.jdbc.use_streams_for_binary true
    crudini --set ${db_properties} '' xroad.db.${db_name}.hibernate.dialect org.hibernate.dialect.PostgreSQLDialect
    crudini --set ${db_properties} '' xroad.db.${db_name}.hibernate.connection.driver_class org.postgresql.Driver
    crudini --set ${db_properties} '' xroad.db.${db_name}.hibernate.connection.url "jdbc:postgresql://$db_host/$db_database$db_options"
    crudini --set ${db_properties} '' xroad.db.${db_name}.hibernate.hikari.dataSource.currentSchema "${db_schema},public"
    crudini --set ${db_properties} '' xroad.db.${db_name}.hibernate.connection.username "${db_conn_user}"
    crudini --set ${db_properties} '' xroad.db.${db_name}.hibernate.connection.password "${db_password}"
  else
    log "$db_properties is not writable, not updating database properties"
  fi

  check_runtime_login "$db_user" "$db_properties" || exit 1

  context="--contexts=user"
  if [[ "$db_user" != "$db_admin_user" ]]; then
    context="--contexts=admin"
  fi

  url_concat_string="$([[ "$db_url" == *"?"* ]] && echo "&" || echo "?")"

  LIQUIBASE_COMMAND_PASSWORD="${db_admin_password}" /usr/share/xroad/db/liquibase.sh \
    --changelog=${db_name} \
    --url="${db_url}${url_concat_string}currentSchema=${db_schema},public" \
    --username="${db_admin_conn_user}" \
    --defaultSchemaName="${db_schema}" \
    --prop-db-user="${db_user}" \
    $context \
    update ||
    die "Running database migrations failed, please check database availability and configuration in ${db_properties} and ${root_properties}"
}

setup_database "$1"
