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

# Source environment file to get database credentials
if [[ -f /etc/openbao/openbao.env ]]; then
  source /etc/openbao/openbao.env
else
  echo "Unable to find OpenBao environment file at /etc/openbao/openbao.env"
  exit 1
fi

# Use individual variables from env file
db_conn_user="${BAO_PG_USER}"
db_user=${db_conn_user%%@*}
db_password="${BAO_PG_PASSWORD}"
db_addr="${BAO_PG_HOST}"
db_port="${BAO_PG_PORT}"
db_database="${BAO_PG_DATABASE}"
db_schema="${BAO_PG_SCHEMA:-public}"

pg_options="-c client-min-messages=warning -c search_path=$db_schema"

# Reading custom libpq ENV variables
if [ -f /etc/xroad/db_libpq.env ]; then
  source /etc/xroad/db_libpq.env
fi

if [[ ! -z $PGOPTIONS_EXTRA ]]; then
  PGOPTIONS_EXTRA=" ${PGOPTIONS_EXTRA}"
fi

remote_psql() {
  psql -v ON_ERROR_STOP=1 -h "${PGHOST:-$db_addr}" -p "${PGPORT:-$db_port}" -qtA
}

psql_dbuser() {
  PGOPTIONS="$pg_options${PGOPTIONS_EXTRA-}" PGDATABASE="$db_database" PGUSER="$db_user" PGPASSWORD="$db_password" remote_psql
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
  { cat <<EOF
     DROP SCHEMA IF EXISTS "$db_schema" CASCADE;
EOF
  } | psql_dbuser || abort "Restoring database failed. Could not drop schema."
fi

{ cat <<EOF
CREATE SCHEMA IF NOT EXISTS "$db_schema";
EOF
} | psql_dbuser || abort "Restoring database failed. Could not create schema."

pgrestore || abort "Restoring database failed."

# PostgreSQL does not in all cases detect that prepared statements in open sessions
# need to be re-parsed. Therefore, try to forcibly close any serverconf connections.
{ cat <<EOF
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE usename='$db_user' and datname='$db_database' and pid <> pg_backend_pid();
EOF
} | psql_dbuser || true
