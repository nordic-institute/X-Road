#!/bin/bash
#
# Migration step 3: deduplicates serverconf.identifier before the schema
# migration in the regular provisioning creates its unique index. Reads the
# connection details from the target db.properties that step 2 copied and
# rewrote to the X-Road 8 keys; a 127.* host means a local database. Errors
# from PostgreSQL go to the container log.
#
# Local database: starts the target PostgreSQL 18, restores the operator's
# pg_dumpall of the 7.8 PostgreSQL 16 cluster into it and deduplicates there.
# External database: checks the server is PostgreSQL 15 or newer, sets its
# host in debconf and deduplicates in place.

PG18_BINDIR="${PG18_BINDIR:-/usr/lib/postgresql/18/bin}"
TARGET_DB_ROOT="${TARGET_DB_ROOT:-/var/lib/postgresql}"
DEDUP_SQL="${DEDUP_SQL:-/usr/share/xroad/scripts/sidecar/migration-tasks/tasks/migration/fix_serverconf_identifier_duplicates.sql}"

migration_database_read_credentials() {
  local db_props="$TARGET_CONFIG_DIR/db.properties"
  local prefix="xroad.db.serverconf.hibernate"
  local jdbc_url
  DB_USER="$(crudini --get "$db_props" '' "$prefix.connection.username" 2>/dev/null)"
  DB_PASSWORD="$(crudini --get "$db_props" '' "$prefix.connection.password" 2>/dev/null)"
  jdbc_url="$(crudini --get "$db_props" '' "$prefix.connection.url" 2>/dev/null)"

  if [[ -z "$DB_USER" || ! "$jdbc_url" =~ ^jdbc:postgresql://([^/]+)/([^?]*) ]]; then
    STEP_REASON="could not read the serverconf connection details from $db_props"
    return 1
  fi
  # Same reading as _entrypoint_common.sh: the URL may list several
  # host:port pairs; connections go to the first one.
  DB_HOSTS="${BASH_REMATCH[1]}"
  DB_NAME="${BASH_REMATCH[2]:-serverconf}"
  local first="${DB_HOSTS%%,*}"
  DB_HOST="${first%%:*}"
  DB_PORT=5432
  [[ "$first" == *:* ]] && DB_PORT="${first##*:}"
  [[ "$DB_HOST" == 127.* ]] && DB_LOCAL=true || DB_LOCAL=false

  # 7.8 writes currentSchema as "<schema>,public"; the deduplication SQL runs
  # in that schema.
  DB_SCHEMA="$(crudini --get "$db_props" '' "$prefix.hikari.dataSource.currentSchema" 2>/dev/null)"
  DB_SCHEMA="${DB_SCHEMA%%,*}"
  if [[ -z "$DB_SCHEMA" ]]; then
    STEP_REASON="no serverconf schema (currentSchema) in $db_props"
    return 1
  fi
  return 0
}

# As the serverconf user, like the native wizard: its triggers need its search_path.
migration_database_serverconf_psql() {
  PGPASSWORD="$DB_PASSWORD" "$PG18_BINDIR/psql" -X -q -v ON_ERROR_STOP=1 \
    -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" "$@"
}

# Prints the major version (e.g. 18) of the server the given psql function
# connects to.
migration_database_major_version() {
  local version
  version="$("$@" -tAc 'SHOW server_version_num')" || return 1
  [[ "$version" =~ ^[0-9]+$ ]] || return 1
  echo $((version / 10000))
}

migration_database_superuser_psql() {
  sudo -u postgres "$PG18_BINDIR/psql" -X -q -v ON_ERROR_STOP=1 -p 5432 "$@"
}

# Shared with the native upgrade wizard.
migration_database_deduplicate() {
  if ! migration_database_serverconf_psql -v schema="$DB_SCHEMA" -f "$DEDUP_SQL" >/dev/null; then
    STEP_REASON="serverconf.identifier deduplication failed"
    return 1
  fi
  return 0
}

# Migrated in place: the operator's backup of the server is the rollback
# point. The regular provisioning sets the database host in debconf only when
# it creates db.properties; with the copied one, the package postinst scripts
# would reconfigure against debconf's 127.0.0.1 default. They write the value
# back into the URL, so it gets the full host list.
migration_database_migrate_external() {
  echo "External database at $DB_HOST:$DB_PORT."

  local count=0
  while ((count++ < 60)) && ! "$PG18_BINDIR/pg_isready" -q -t 2 -h "$DB_HOST" -p "$DB_PORT"; do
    sleep 1
  done

  local major
  major="$(migration_database_major_version migration_database_serverconf_psql)"
  if [[ -z "$major" ]]; then
    STEP_REASON="could not read the PostgreSQL version of the external database at $DB_HOST:$DB_PORT (check the server is reachable and the serverconf credentials are correct)"
    return 1
  fi
  if ((major < 15)); then
    STEP_REASON="external database at $DB_HOST:$DB_PORT runs PostgreSQL $major, older than 15"
    return 1
  fi

  if ! echo "xroad-proxy xroad-common/database-host string $DB_HOSTS" | debconf-set-selections; then
    STEP_REASON="failed to set the external database host in debconf"
    return 1
  fi

  migration_database_deduplicate
}

migration_database_check_dump() {
  local dump="$SOURCE_DB_DUMP"

  if [[ ! -s "$dump" ]]; then
    STEP_REASON="7.8 database dump $dump is missing or empty"
    return 1
  fi
  if head -c 4096 "$dump" | grep -q $'\r'; then
    STEP_REASON="7.8 database dump $dump has CRLF line endings"
    return 1
  fi
  if ! head -n 5 "$dump" | grep -qx -- '-- PostgreSQL database cluster dump'; then
    STEP_REASON="$dump is not a pg_dumpall dump"
    return 1
  fi
  if ! tail -n 10 "$dump" | grep -qx -- '-- PostgreSQL database cluster dump complete'; then
    STEP_REASON="7.8 database dump $dump is incomplete: its end marker is missing"
    return 1
  fi
  return 0
}

migration_database_start_local() {
  local data="$TARGET_DB_ROOT/18/main"
  if [[ ! -s "$data/PG_VERSION" ]]; then
    mkdir -p "$data"
    chown postgres:postgres "$data"
    chmod 0700 "$data"
    if ! sudo -u postgres "$PG18_BINDIR/initdb" -D "$data" >/dev/null; then
      STEP_REASON="failed to initialize the PostgreSQL 18 data directory at $data"
      return 1
    fi
  fi

  if ! pg_ctlcluster 18 main start; then
    STEP_REASON="failed to start the local PostgreSQL 18"
    return 1
  fi
  return 0
}

# The postgres role already exists in the target, so its CREATE ROLE is
# dropped. Root reads the dump, so it need not be readable by the postgres user.
migration_database_migrate_local() {
  migration_database_check_dump || return 1
  migration_database_start_local || return 1

  local major
  major="$(migration_database_major_version migration_database_superuser_psql)"
  if [[ "$major" != 18 ]]; then
    STEP_REASON="local database runs PostgreSQL ${major:-of unknown version}, not 18"
    return 1
  fi

  if ! sed '/^CREATE ROLE postgres;$/d' "$SOURCE_DB_DUMP" \
    | migration_database_superuser_psql -d postgres >/dev/null; then
    STEP_REASON="restoring $SOURCE_DB_DUMP into PostgreSQL 18 failed"
    return 1
  fi

  migration_database_deduplicate
}

step_database() {
  migration_database_read_credentials || return 1

  if [[ "$DB_LOCAL" == true ]]; then
    migration_database_migrate_local
  else
    migration_database_migrate_external
  fi
}
