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

get_properties_files() {
  if [ -f /etc/xroad/xroad.properties ]; then
    echo "/etc/xroad/xroad.properties"
  else
    echo "/etc/xroad.properties"
  fi
}

load_or_generate_credentials() {
  local suffix="$1"

  if [ -f /etc/openbao/openbao.env ] && [ -s /etc/openbao/openbao.env ]; then
    echo "Found existing /etc/openbao/openbao.env, attempting to load credentials"
    source /etc/openbao/openbao.env

    if [ -n "${BAO_PG_USER}" ] && [ -n "${BAO_PG_PASSWORD}" ] && [ -n "${BAO_PG_DATABASE}" ]; then
      echo "Successfully loaded credentials from /etc/openbao/openbao.env"
      db_conn_user="${BAO_PG_USER}"
      db_user=${db_conn_user%%@*}
      db_password="${BAO_PG_PASSWORD}"
      db_addr="${BAO_PG_HOST}"
      db_port="${BAO_PG_PORT}"
      db_database="${BAO_PG_DATABASE}"
      db_schema="${BAO_PG_SCHEMA}"
    else
      echo "WARNING: /etc/openbao/openbao.env exists but is incomplete, regenerating"
      rm -f /etc/openbao/openbao.env
      db_conn_user="openbao$suffix"
      db_user=${db_conn_user%%@*}
      db_password="$(gen_pw)"
      db_database="openbao"
      db_schema="openbao"
      db_addr=""
      db_port=""
    fi
  else
    if [ -f /etc/openbao/openbao.env ]; then
      echo "WARNING: /etc/openbao/openbao.env exists but is empty, regenerating"
      rm -f /etc/openbao/openbao.env
    fi
    echo "Generating new credentials for OpenBao database"
    db_conn_user="openbao$suffix"
    db_user=${db_conn_user%%@*}
    db_password="$(gen_pw)"
    db_database="openbao"
    db_schema="openbao"
    db_addr=""
    db_port=""
  fi
}

parse_database_host() {
  local db_host="$1"

  if [ -z "$db_addr" ] || [ -z "$db_port" ]; then
    local hosts
    IFS=',' read -ra hosts <<<"$db_host"
    db_addr=${hosts[0]%%:*}
    db_port=${hosts[0]##*:}
  fi
}

setup_psql_functions() {
  local root_properties="$1"
  local db_master_conn_user="$2"

  local_psql() { su -l -c "psql -qtA -p ${PGPORT:-$db_port} $*" postgres; }
  remote_psql() { psql -h "${PGHOST:-$db_addr}" -p "${PGPORT:-$db_port}" -qtA "$@"; }

  psql_dbuser() {
    PGDATABASE=$db_database PGUSER=$db_conn_user PGPASSWORD=$db_password remote_psql "$@"
  }

  if [[ -f ${root_properties} && $(get_prop ${root_properties} postgres.connection.password) != "" ]]; then
    local db_master_passwd=$(get_prop ${root_properties} postgres.connection.password)
    psql_master() {
      PGDATABASE="postgres" PGPASSWORD="${db_master_passwd}" PGUSER="${db_master_conn_user}" remote_psql "$@"
    }
  else
    psql_master() { local_psql "$@"; }
  fi
}

create_database_if_needed() {
  local db_host="$1"

  if PGCONNECT_TIMEOUT=5 psql_dbuser -c "\q" &>/dev/null; then
    echo "Database and user exists, skipping database creation."
    return 0
  fi

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
  } | psql_master || die "Creating database '${db_database}' on '${db_host}' failed, please check database availability and configuration"
}

configure_openbao_storage() {
  if [ ! -f /etc/openbao/openbao.hcl ]; then
    return 0
  fi

  if grep -q '^storage "file"' /etc/openbao/openbao.hcl; then
    echo "Configuring OpenBao to use PostgreSQL storage backend"
    sed -i.bak 's/^storage "file" {/storage "postgresql" {/' /etc/openbao/openbao.hcl
    sed -i '/^\s*path\s*=.*openbao.*data/d' /etc/openbao/openbao.hcl
  fi
}

create_env_file_if_needed() {
  if [ -f /etc/openbao/openbao.env ]; then
    echo "/etc/openbao/openbao.env already exists, preserving existing credentials"
    return 0
  fi

  echo "Creating /etc/openbao/openbao.env with database credentials"
  local db_options="?search_path=$db_schema"

  cat >/etc/openbao/openbao.env <<EOF
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
  chmod 0640 /etc/openbao/openbao.env
  chown openbao:xroad /etc/openbao/openbao.env
}

setup_database() {
  local db_properties=/etc/xroad/db.properties
  local root_properties=$(get_properties_files)
  local new_db_host="$1"
  local db_host="${new_db_host:-127.0.0.1:5432}"

  local db_master_conn_user=$(get_prop ${root_properties} postgres.connection.user 'postgres')
  local db_master_user=${db_master_conn_user%%@*}
  local suffix="${db_master_conn_user##$db_master_user}"

  load_or_generate_credentials "$suffix"

  if [ -f /etc/xroad/db_libpq.env ]; then
    source /etc/xroad/db_libpq.env
  fi

  if [[ ! -z $PGOPTIONS_EXTRA ]]; then
    PGOPTIONS_EXTRA=" ${PGOPTIONS_EXTRA}"
  fi

  export PGOPTIONS="-c client-min-messages=warning -c search_path=${db_schema}${PGOPTIONS_EXTRA-}"

  parse_database_host "$db_host"
  setup_psql_functions "$root_properties" "$db_master_conn_user"
  create_database_if_needed "$db_host"
  configure_openbao_storage
  create_env_file_if_needed
}

setup_database "$1"
