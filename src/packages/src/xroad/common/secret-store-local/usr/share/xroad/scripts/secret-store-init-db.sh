#!/bin/bash

DB="openbao"
DB_USER="openbao"
DB_USER_PASSWORD=""
DB_SCHEMA="openbao"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --db)
      DB="$2"
      shift 2
      ;;
    --db-user)
      DB_USER="$2"
      shift 2
      ;;
    --db-schema)
      DB_SCHEMA="$2"
      shift 2
      ;;
    --db-user-password)
      DB_USER_PASSWORD="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1"
      exit 1
      ;;
  esac
done

if [[ -z "$DB_USER_PASSWORD" ]]; then
    echo "Openbao DB user's password was not provided"
    exit 1
fi

su -l postgres -c 'psql' <<EOF
\set ON_ERROR_STOP on
CREATE DATABASE "${DB}" ENCODING 'UTF8';
REVOKE ALL ON DATABASE "${DB}" FROM PUBLIC;
DO \$\$
BEGIN
  CREATE ROLE "${DB_USER}" LOGIN PASSWORD '${DB_USER_PASSWORD}';
  GRANT "${DB_USER}" to postgres;
  EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'User $DB_USER already exists';
END
\$\$;
GRANT CREATE,TEMPORARY,CONNECT ON DATABASE "${DB}" TO "${DB_USER}";
\c "${DB}"
CREATE SCHEMA "${DB_SCHEMA}" AUTHORIZATION "${DB_USER}";
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public to "${DB_USER}";
EOF

