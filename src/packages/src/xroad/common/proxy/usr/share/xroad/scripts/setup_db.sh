#!/bin/bash

log() { echo >&2 "$@"; }
die() { log "$@"; exit 1; }

get_prop() {
    crudini --get "$1" '' "$2" 2>/dev/null || echo -n "$3"
}

setup_database() {

    node_type=$(crudini --get '/etc/xroad/conf.d/node.ini' node type 2>/dev/null || echo standalone)
    if [[ "$node_type" == "slave" ]]; then
      log "Skipping database setup on cluster slave node"
      return 0
    fi

    local db_properties=/etc/xroad/db.properties
    local root_properties=/etc/xroad.properties

    local db_host="${1:-127.0.0.1:5432}"

    local tmp_password="$(head -c 24 /dev/urandom | base64 | tr "/+" "_-")"
    local db_user=$(get_prop ${db_properties} 'serverconf.hibernate.connection.username' 'serverconf')
    local db_schema=$(get_prop ${db_properties} 'serverconf.hibernate.connection.currentSchema' "${db_user},public")
    db_schema=${db_schema%%,*}
    local db_password=$(get_prop ${db_properties} 'serverconf.hibernate.connection.password' "$tmp_password")
    local db_url=$(get_prop ${db_properties} 'serverconf.hibernate.connection.url' "jdbc:postgresql://$db_host/serverconf")
    local db_master_user=postgres
    local db_database=serverconf
    local db_admin_user=$(get_prop ${root_properties} 'serverconf.admin.username' "$db_user")
    local db_admin_password=$(get_prop ${root_properties} 'serverconf.admin.password' "$db_password")

    export PGOPTIONS="-c client-min-messages=warning -c search_path=$db_schema,public"

    pat='^jdbc:postgresql://([^/]*)($|/([^\?]*)(.*)$)'
    if [[ "$db_url" =~ $pat ]]; then
      db_host=${BASH_REMATCH[1]:-$db_host}
      #match 2 unused
      db_database=${BASH_REMATCH[3]:-serverconf}
      db_options="${BASH_REMATCH[4]:-$db_options}"
    else
      log "Unable to parse '$db_url', using 'jdbc:postgresql://$db_host/$db_database$db_options'"
    fi

    local hosts
    IFS=',' read -ra hosts <<<"$db_host"
    local db_addr=${hosts[0]%%:*}
    local db_port=${hosts[0]##*:}

    local_psql() { su -l -c "psql -qtA -p ${db_port:-5432} $*" postgres; }
    remote_psql() { psql -h "${db_addr:-127.0.0.1}" -p "${db_port:-5432}" -qtA "$@"; }

    psql_dbuser() {
        PGDATABASE="$db_database" PGUSER="$db_user" PGPASSWORD="$db_password" remote_psql "$@"
    }

    psql_adminuser() {
        PGDATABASE="$db_database" PGUSER="$db_admin_user" PGPASSWORD="$db_admin_password" remote_psql "$@"
    }

    if [[ -f ${root_properties} && $(get_prop ${root_properties} postgres.connection.password) != "" ]]; then
        local db_master_passwd=$(get_prop ${root_properties} postgres.connection.password)
        db_master_user=$(get_prop ${root_properties} postgres.connection.user 'postgres')
        function psql_master() {
            PGPASSWORD="${db_master_passwd}" PGUSER="${db_master_user}" remote_psql "$@"
        }
    else
        function psql_master() { local_psql "$@"; }
    fi

    if PGCONNECT_TIMEOUT=5 psql_dbuser -c "\q" &>/dev/null; then
        log "Database and user exists, skipping database creation."
    else
        { cat <<EOF
CREATE DATABASE "${db_database}" ENCODING 'UTF8';
REVOKE ALL ON DATABASE "${db_database}" FROM PUBLIC;
DO \$\$
BEGIN
  CREATE ROLE "${db_admin_user}" LOGIN PASSWORD '${db_admin_password}';
  GRANT "${db_admin_user}" to "${db_master_user}";
  EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'User $db_admin_user already exists';
END\$\$;
GRANT CREATE,TEMPORARY,CONNECT ON DATABASE "${db_database}" TO "${db_admin_user}";
\c "${db_database}"
CREATE EXTENSION hstore;
CREATE SCHEMA "${db_schema}" AUTHORIZATION "${db_admin_user}";
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public to "${db_admin_user}";
EOF
        if [ "$db_user" != "$db_admin_user" ]; then
            cat <<EOF
DO \$\$
BEGIN
  CREATE ROLE "${db_user}" LOGIN PASSWORD '${db_password}';
  GRANT "${db_user}" to "${db_master_user}";
  EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'User $db_admin_user already exists';
END\$\$;
GRANT TEMPORARY,CONNECT ON DATABASE "${db_database}" TO "${db_user}";
GRANT USAGE ON SCHEMA public to "${db_user}";
EOF
       fi
       } | psql_master || die "Creating database '${db_database}' on '${db_host}' failed."
    fi

    touch ${db_properties}
    chown xroad:xroad ${db_properties}
    chmod 640 ${db_properties}

    crudini --set ${db_properties} '' serverconf.hibernate.jdbc.use_streams_for_binary true
    crudini --set ${db_properties} '' serverconf.hibernate.dialect ee.ria.xroad.common.db.CustomPostgreSQLDialect
    crudini --set ${db_properties} '' serverconf.hibernate.connection.driver_class org.postgresql.Driver
    crudini --set ${db_properties} '' serverconf.hibernate.connection.url "jdbc:postgresql://$db_host/$db_database$db_options"
    crudini --set ${db_properties} '' serverconf.hibernate.connection.currentSchema "${db_schema},public"
    crudini --set ${db_properties} '' serverconf.hibernate.connection.username "${db_user}"
    crudini --set ${db_properties} '' serverconf.hibernate.connection.password "${db_password}"

    if [[ $(psql_adminuser -c "select 1 from pg_tables where schemaname = 'public' and tablename='databasechangelog'" 2>/dev/null) == 1 ]]; then
      cd /usr/share/xroad/db/
      /usr/share/xroad/db/liquibase.sh \
        --classpath=/usr/share/xroad/jlib/proxy.jar \
        --url="jdbc:postgresql://$db_host/$db_database" \
        --changeLogFile=/usr/share/xroad/db/serverconf-legacy-changelog.xml \
        --password="${db_admin_password}" \
        --username="${db_admin_user}" \
        update || die "Connection to database has failed, please check database availability and configuration in ${db_properties} file"

      psql_master --single-transaction -d "$db_database" <<EOF || die "Renaming public schema to '$db_schema' failed."
\set STOP_ON_ERROR on
ALTER DATABASE "${db_database}" OWNER TO "${db_master_user}";
REVOKE ALL ON DATABASE "${db_database}" FROM PUBLIC;
GRANT CREATE,TEMPORARY,CONNECT ON DATABASE "${db_database}" TO "${db_admin_user}";
GRANT TEMPORARY,CONNECT ON DATABASE "${db_database}" TO "${db_user}";
ALTER SCHEMA public RENAME TO "${db_schema}";
ALTER SCHEMA "${db_schema}" OWNER TO "${db_admin_user}";
REVOKE ALL ON SCHEMA "${db_schema}" FROM PUBLIC;
CREATE SCHEMA public;
GRANT USAGE ON SCHEMA public TO "${db_admin_user}";
GRANT USAGE ON SCHEMA public TO "${db_user}";
ALTER EXTENSION hstore SET SCHEMA public;
EOF
    fi

    cd /usr/share/xroad/db/
    context="--contexts=user"
    if [[ "$db_user" != "$db_admin_user" ]]; then
      context="--contexts=admin"
    fi

    JAVA_OPTS="-Ddb_user=$db_user -Ddb_schema=$db_schema" /usr/share/xroad/db/liquibase.sh \
      --classpath=/usr/share/xroad/jlib/proxy.jar \
      --url="jdbc:postgresql://$db_host/$db_database?currentSchema=${db_schema},public" \
      --changeLogFile=/usr/share/xroad/db/serverconf-changelog.xml \
      --password="${db_admin_password}" \
      --username="${db_admin_user}" \
      --defaultSchemaName="${db_schema}" \
      $context \
      update \
      || die "Connection to database has failed, please check database availability and configuration in ${db_properties} file"
}

setup_database "$1"
