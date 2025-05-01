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

db_connection_url=$(awk '/storage "postgresql"/,/\}/' /etc/openbao/openbao.hcl | grep 'connection_url' | cut -d'"' -f2)
pat='^postgres:\/\/([^:]+):([^@]+)@([^:]+):([0-9]+)\/([^?]+)(\?(.*))?$'
if [[ $db_connection_url =~ $pat ]]; then
  db_conn_user="${BASH_REMATCH[1]}"
  db_user=${db_conn_user%%@*}
  db_password="${BASH_REMATCH[2]}"
  db_addr="${BASH_REMATCH[3]}"
  db_port="${BASH_REMATCH[4]}"
  db_database="${BASH_REMATCH[5]}"
  db_options="${BASH_REMATCH[7]}" # full query string after '?', if present
  schema_pat='(^|&)search_path=([^&]*)'
  if [[ $db_options =~ $schema_pat ]]; then
    db_schema="${BASH_REMATCH[2]}"
  else
    db_schema="public"
  fi
  pg_options="-c client-min-messages=warning -c search_path=$db_schema"
else
  echo "Unable to determine OpenBao PostgreSQL connection URL in /etc/openbao/openbao.hcl"
  exit 1
fi

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

# PostgreSQL 9.2 and earlier do not support CREATE SCHEMA IF NOT EXISTS
{ cat <<EOF
DO \$\$
BEGIN
    IF NOT EXISTS(
        SELECT schema_name
          FROM information_schema.schemata
          WHERE schema_name = '$db_schema'
      )
    THEN
      EXECUTE 'CREATE SCHEMA "$db_schema"';
    END IF;
END
\$\$;
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
