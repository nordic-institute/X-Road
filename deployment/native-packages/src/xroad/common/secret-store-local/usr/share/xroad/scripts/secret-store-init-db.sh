#!/bin/bash

# Usage: secret-store-init-db.sh [--central-server] [DATABASE_HOST]
# --central-server adds the database address checks of the Central Server installer.
central_server=false
if [ "$1" = "--central-server" ]; then
  central_server=true
  shift
fi

. /usr/share/xroad/scripts/_openbao_env.sh
# The helper has its own setup_database; the definition below replaces it, so the helper is sourced first.
if [ "$central_server" = true ]; then
  . /usr/share/xroad/scripts/_setup_db.sh
fi

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

  if [ -f /etc/openbao/xroad-openbao.env ] && [ -s /etc/openbao/xroad-openbao.env ]; then
    echo "Found existing /etc/openbao/xroad-openbao.env, attempting to load credentials"
    BAO_PG_USER=$(read_env_value /etc/openbao/xroad-openbao.env BAO_PG_USER)
    BAO_PG_PASSWORD=$(read_env_value /etc/openbao/xroad-openbao.env BAO_PG_PASSWORD)
    BAO_PG_HOST=$(read_env_value /etc/openbao/xroad-openbao.env BAO_PG_HOST)
    BAO_PG_PORT=$(read_env_value /etc/openbao/xroad-openbao.env BAO_PG_PORT)
    BAO_PG_DATABASE=$(read_env_value /etc/openbao/xroad-openbao.env BAO_PG_DATABASE)
    BAO_PG_SCHEMA=$(read_env_value /etc/openbao/xroad-openbao.env BAO_PG_SCHEMA)

    if [ -n "${BAO_PG_USER}" ] && [ -n "${BAO_PG_PASSWORD}" ] && [ -n "${BAO_PG_DATABASE}" ]; then
      require_env_name BAO_PG_USER "${BAO_PG_USER}"
      require_env_name BAO_PG_DATABASE "${BAO_PG_DATABASE}"
      require_env_name BAO_PG_SCHEMA "${BAO_PG_SCHEMA}"
      echo "Successfully loaded credentials from /etc/openbao/xroad-openbao.env"
      db_conn_user="${BAO_PG_USER}"
      db_user=${db_conn_user%%@*}
      db_password="${BAO_PG_PASSWORD}"
      db_addr="${BAO_PG_HOST}"
      db_port="${BAO_PG_PORT}"
      db_database="${BAO_PG_DATABASE}"
      db_schema="${BAO_PG_SCHEMA}"
    else
      echo "WARNING: /etc/openbao/xroad-openbao.env exists but is incomplete, regenerating"
      rm -f /etc/openbao/xroad-openbao.env
      db_conn_user="openbao$suffix"
      db_user=${db_conn_user%%@*}
      db_password="$(gen_pw)"
      db_database="openbao"
      db_schema="openbao"
      db_addr=""
      db_port=""
    fi
  else
    if [ -f /etc/openbao/xroad-openbao.env ]; then
      echo "WARNING: /etc/openbao/xroad-openbao.env exists but is empty, regenerating"
      rm -f /etc/openbao/xroad-openbao.env
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
  local port="${PGPORT:-$db_port}"

  if [[ ! $port =~ ^[0-9]+$ ]] || ((10#$port < 1 || 10#$port > 65535)); then
    die "Invalid database port ${port@Q}, expected a number from 1 to 65535"
  fi

  local_psql() { su -l -c 'port=$1; shift; exec psql -qtA -p "$port" "$@"' postgres -- _ "${PGPORT:-$db_port}" "$@"; }
  remote_psql() { psql -h "${PGHOST:-$db_addr}" -p "${PGPORT:-$db_port}" -qtA "$@"; }

  psql_dbuser() {
    PGDATABASE=$db_database PGUSER=$db_conn_user PGPASSWORD=$db_password remote_psql "$@"
  }

  if [[ -f ${root_properties} && $(get_prop ${root_properties} postgres.connection.password) != "" ]]; then
    db_master_passwd=$(get_prop ${root_properties} postgres.connection.password)
    psql_master() {
      PGDATABASE="postgres" PGPASSWORD="${db_master_passwd}" PGUSER="${db_master_conn_user}" remote_psql "$@"
    }
  else
    psql_master() { local_psql "$@"; }
  fi
}

psql_set_literal() {
  local name="$1"
  local value="$2"

  value=${value//\\/\\\\}
  value=${value//\'/\\\'}
  printf '\\set %s '"'"'%s'"'"'\n' "$name" "$value"
}

create_database_if_needed() {
  local db_host="$1"

  if PGCONNECT_TIMEOUT=5 psql_dbuser -c "\q" &>/dev/null; then
    echo "Database and user exists, skipping database creation."
    return 0
  fi

  if ! {
    printf '%s\n' '\set ON_ERROR_STOP on'
    psql_set_literal bao_password "${db_password}"
    cat <<'EOF'
CREATE DATABASE :"bao_db" ENCODING 'UTF8';
REVOKE ALL ON DATABASE :"bao_db" FROM PUBLIC;
SET xroad_init.role_name = :'bao_user';
SET xroad_init.role_password = :'bao_password';
DO $$
BEGIN
  EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', current_setting('xroad_init.role_name'), current_setting('xroad_init.role_password'));
  EXECUTE format('GRANT %I TO postgres', current_setting('xroad_init.role_name'));
  EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'User % already exists', current_setting('xroad_init.role_name');
END
$$;
GRANT CREATE,TEMPORARY,CONNECT ON DATABASE :"bao_db" TO :"bao_user";
\c :"bao_db"
CREATE SCHEMA :"bao_schema" AUTHORIZATION :"bao_user";
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public to :"bao_user";
EOF
  } | psql_master -v "bao_db=${db_database}" -v "bao_user=${db_user}" -v "bao_schema=${db_schema}"; then
    die "Creating database '${db_database}' on '${db_host}' failed, please check database availability and configuration"
  fi
}

# Group xroad read is required, not incidental: the backup driver runs as the
# xroad user (xroad-auxiliary-service, which execs
# backup_xroad_proxy_configuration.sh -> _backup_xroad.sh ->
# backup_openbao_db.sh) and both read this file for the pg_dump credentials
# and archives /etc/openbao. No privileged path exists for that dump, so
# narrowing this to root-only breaks backups. It grants raw OpenBao storage
# access, not secret disclosure — the root token and unseal keys stay root-only.
enforce_env_file_permissions() {
  chmod 0640 /etc/openbao/xroad-openbao.env
  chown openbao:xroad /etc/openbao/xroad-openbao.env
}

create_env_file_if_needed() {
  if [ -f /etc/openbao/xroad-openbao.env ]; then
    echo "/etc/openbao/xroad-openbao.env already exists, preserving existing credentials"
    enforce_env_file_permissions
    return 0
  fi

  echo "Creating /etc/openbao/xroad-openbao.env with database credentials"
  local db_options="?search_path=$db_schema"

  cat >/etc/openbao/xroad-openbao.env <<EOF
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
  enforce_env_file_permissions
}

setup_database() {
  local db_properties=/etc/xroad/db.properties
  local root_properties=$(get_properties_files)
  local new_db_host="$1"
  local central_server="$2"
  local db_host="${new_db_host:-127.0.0.1:5432}"

  if [ "$central_server" = true ]; then
    check_db_host_answer "$db_host" fresh
  fi

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
  if [ "$central_server" = true ]; then
    check_pg_version psql_master
    check_db_writable psql_master "${db_addr}:${db_port}"
  fi
  create_database_if_needed "$db_host"
  create_env_file_if_needed
}

setup_database "$1" "$central_server"
