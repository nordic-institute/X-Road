#!/usr/bin/env bash
set -e

scriptdir=$(dirname "$BASH_SOURCE")

$scriptdir/db/prepare_init_sql.sh | psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB"
