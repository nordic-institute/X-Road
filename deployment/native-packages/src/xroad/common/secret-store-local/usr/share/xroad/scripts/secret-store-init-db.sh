#!/bin/bash

die() {
  echo >&2 "$@"
  exit 1
}

get_prop() {
  crudini --get "$1" '' "$2" 2>/dev/null || echo -n "$3"
}

gen_pw() {
  head -c 24 /dev/urandom | base64 | tr "/+" "_-"
}

setup_database() {
  local -r db_properties=/etc/xroad/db.properties
  if [ -f /etc/xroad/xroad.properties ]; then
    local -r root_properties=/etc/xroad/xroad.properties
  else
    local -r root_properties=/etc/xroad.properties
  fi

  local new_db_host="$1"
  local db_host="${new_db_host:-127.0.0.1:5432}"


  local db_master_conn_user=$(get_prop ${root_properties} postgres.connection.user 'postgres')
  local db_master_user=${db_master_conn_user%%@*}
  local suffix="${db_master_conn_user##$db_master_user}"

  local db_conn_user="openbao$suffix"
  local db_user=${db_conn_user%%@*}
  local db_password="$(gen_pw)"
  local db_database="openbao"
  local db_schema="openbao"
  local db_options="?search_path=$db_schema"

  # db config predefined in OpenBao's config file supersedes default ones
  db_connection_url=$(awk '/storage "postgresql"/,/\}/' /etc/openbao/openbao.hcl | grep 'connection_url' | cut -d'"' -f2)
  pat='^postgres:\/\/([^:]+):([^@]+)@([^:]+):([0-9]+)\/([^?]+)(\?(.*))?$'
  if [[ $db_connection_url =~ $pat ]]; then
    db_conn_user="${BASH_REMATCH[1]}"
    db_user=${db_conn_user%%@*}
    db_password="${BASH_REMATCH[2]}"
    db_database="${BASH_REMATCH[5]}"
    db_options="${BASH_REMATCH[7]}" # full query string after '?', if present
    schema_pat='(^|&)search_path=([^&]*)'
    if [[ $db_options =~ $schema_pat ]]; then
      db_schema="${BASH_REMATCH[2]}"
    else
      db_schema="public"
    fi
  fi

  # Reading custom libpq ENV variables
  if [ -f /etc/xroad/db_libpq.env ]; then
    source /etc/xroad/db_libpq.env
  fi

  if [[ ! -z $PGOPTIONS_EXTRA ]]; then
    PGOPTIONS_EXTRA=" ${PGOPTIONS_EXTRA}"
  fi

  export PGOPTIONS="-c client-min-messages=warning -c search_path=${db_schema}${PGOPTIONS_EXTRA-}"

  local hosts
  IFS=',' read -ra hosts <<<"$db_host"
  local db_addr=${hosts[0]%%:*}
  local db_port=${hosts[0]##*:}

  local_psql() { su -l -c "psql -qtA -p ${PGPORT:-$db_port} $*" postgres; }
  remote_psql() { psql -h "${PGHOST:-$db_addr}" -p "${PGPORT:-$db_port}" -qtA "$@"; }

  psql_dbuser() {
    PGDATABASE=$db_database PGUSER=$db_conn_user PGPASSWORD=$db_password remote_psql "$@"
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
    echo "Database and user exists, skipping database creation."
  else
    {
      cat <<EOF
\set ON_ERROR_STOP on
CREATE DATABASE "${db_database}" ENCODING 'UTF8';
REVOKE ALL ON DATABASE "${db_database}" FROM PUBLIC;
DO \$\$
BEGIN
  CREATE ROLE "${db_user}" LOGIN PASSWORD '${db_password}';
  GRANT "${db_user}" to postgres;
  EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'User $db_user already exists';
END
\$\$;
GRANT CREATE,TEMPORARY,CONNECT ON DATABASE "${db_database}" TO "${db_user}";
\c "${db_database}"
CREATE SCHEMA "${db_schema}" AUTHORIZATION "${db_user}";
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public to "${db_user}";
EOF
    } | psql_master || die "Creating database '${db_database}' on '${db_host}' failed, please check database availability and configuration in ${db_properties} and ${root_properties}"

    # Configure OpenBao to use PostgreSQL storage instead of file storage
    # Replace the default "file" storage backend with "postgresql"
    if [ -f /etc/openbao/openbao.hcl ]; then
      sed -i.bak 's/^storage "file" {/storage "postgresql" {/' /etc/openbao/openbao.hcl
      # Remove the file path line if it exists (only needed for file storage)
      sed -i '/^\s*path\s*=.*openbao.*data/d' /etc/openbao/openbao.hcl
    fi

    # Create environment file with database credentials
    # Store both individual components (for backup/restore scripts) and full URL (for OpenBao)
    # OpenBao reads BAO_PG_CONNECTION_URL directly from environment
    # See: https://openbao.org/docs/configuration/storage/postgresql/#postgresql-parameters
    cat > /etc/openbao/openbao.env <<EOF
# Individual components for backup/restore scripts
BAO_PG_USER=${db_conn_user}
BAO_PG_PASSWORD=${db_password}
BAO_PG_HOST=${db_addr}
BAO_PG_PORT=${db_port}
BAO_PG_DATABASE=${db_database}
BAO_PG_SCHEMA=${db_schema}

# Full connection URL for OpenBao
BAO_PG_CONNECTION_URL=postgres://${db_conn_user}:${db_password}@${db_addr}:${db_port}/${db_database}${db_options}
EOF
    chmod 0600 /etc/openbao/openbao.env
    chown openbao:openbao /etc/openbao/openbao.env

  fi
}

setup_database "$1"
