#!/bin/bash

abort() { local rc=$?; echo -e "FATAL: $*" >&2; exit $rc; }

while getopts "F" opt ; do
  case ${opt} in
    F)
      FORCE_RESTORE=true
      ;;
    \?)
      echo "Invalid option $OPTARG -- did you use the correct wrapper script?"
      exit 2
      ;;
  esac
done

shift $(($OPTIND - 1))

dump_file="$1"
env_file=/etc/openbao/xroad-openbao.env

. /usr/share/xroad/scripts/_openbao_env.sh

if [[ ! -f $env_file ]]; then
  echo "Unable to find OpenBao environment file at $env_file"
  exit 1
fi

# Read the database credentials from the env file as data
db_conn_user=$(read_env_value "$env_file" BAO_PG_USER)
db_user=${db_conn_user%%@*}
db_password=$(read_env_value "$env_file" BAO_PG_PASSWORD)
db_addr=$(read_env_value "$env_file" BAO_PG_HOST)
db_port=$(read_env_value "$env_file" BAO_PG_PORT)
db_database=$(read_env_value "$env_file" BAO_PG_DATABASE)
db_schema=$(read_env_value "$env_file" BAO_PG_SCHEMA)
db_schema="${db_schema:-public}"

pg_options="-c client-min-messages=warning -c search_path=$db_schema"

# Reading custom libpq ENV variables
if [ -f /etc/xroad/db_libpq.env ]; then
  source /etc/xroad/db_libpq.env
fi

if [[ ! -z $PGOPTIONS_EXTRA ]]; then
  PGOPTIONS_EXTRA=" ${PGOPTIONS_EXTRA}"
fi

remote_psql() {
  psql -v ON_ERROR_STOP=1 -h "${PGHOST:-$db_addr}" -p "${PGPORT:-$db_port}" -qtA "$@"
}

psql_dbuser() {
  PGOPTIONS="$pg_options${PGOPTIONS_EXTRA-}" PGDATABASE="$db_database" PGUSER="$db_user" PGPASSWORD="$db_password" remote_psql "$@"
}

pgrestore() {
  # no --clean for force restore
  if [[ $FORCE_RESTORE == true ]] ; then
    PGHOST="${PGHOST:-$db_addr}" PGPORT="${PGPORT:-$db_port}" PGUSER="$db_user" PGPASSWORD="$db_password" \
      pg_restore --no-owner --single-transaction -d "$db_database" --schema="$db_schema" "$dump_file"
  else
    PGHOST="${PGHOST:-$db_addr}" PGPORT="${PGPORT:-$db_port}" PGUSER="$db_user" PGPASSWORD="$db_password" \
      pg_restore --no-owner --single-transaction --clean -d "$db_database" --schema="$db_schema" "$dump_file"
  fi
}

if [[ $FORCE_RESTORE == true ]] ; then
  psql_dbuser -v "bao_schema=${db_schema}" <<'EOF' || abort "Restoring database failed. Could not drop schema."
DROP SCHEMA IF EXISTS :"bao_schema" CASCADE;
EOF
fi

psql_dbuser -v "bao_schema=${db_schema}" <<'EOF' || abort "Restoring database failed. Could not create schema."
CREATE SCHEMA IF NOT EXISTS :"bao_schema";
EOF

pgrestore || abort "Restoring database failed."

# PostgreSQL does not in all cases detect that prepared statements in open sessions
# need to be re-parsed. Therefore, try to forcibly close any serverconf connections.
psql_dbuser -v "bao_user=${db_user}" -v "bao_db=${db_database}" <<'EOF' || true
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE usename = :'bao_user' AND datname = :'bao_db' AND pid <> pg_backend_pid();
EOF
