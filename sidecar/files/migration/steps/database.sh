#!/bin/bash
#
# Migration step 2: dumps the 7.8 PostgreSQL 16 data into the target
# PostgreSQL 18 cluster and deduplicates serverconf.identifier there. The
# source is never started; a temporary copy of it is.

PG16_BINDIR="${PG16_BINDIR:-/usr/lib/postgresql/16/bin}"
PG18_BINDIR="${PG18_BINDIR:-/usr/lib/postgresql/18/bin}"
TARGET_DB_ROOT="${TARGET_DB_ROOT:-/var/lib/postgresql}"
DEDUP_SQL="${DEDUP_SQL:-/usr/share/xroad/scripts/sidecar/migration-tasks/tasks/migration/fix_serverconf_identifier_duplicates.sql}"
# For the client-count result check in step 6.
MIGRATION_CLIENT_COUNT_FILE="${MIGRATION_CLIENT_COUNT_FILE:-/var/tmp/xroad-migration-source-client-count}"
MIGRATION_DB_LOG="/var/log/xroad/migration-database.log"
PG16_PORT=5433

migration_database_read_source_credentials() {
  local db_props="$SOURCE_CONFIG_DIR/db.properties"
  DB_USER="$(crudini --get "$db_props" '' 'serverconf.hibernate.connection.username' 2>/dev/null)"
  DB_PASSWORD="$(crudini --get "$db_props" '' 'serverconf.hibernate.connection.password' 2>/dev/null)"
  local jdbc_url
  jdbc_url="$(crudini --get "$db_props" '' 'serverconf.hibernate.connection.url' 2>/dev/null)"

  if [[ -z "$DB_USER" || -z "$jdbc_url" ]]; then
    STEP_REASON="could not read the serverconf connection details from $db_props"
    return 1
  fi

  DB_NAME="$(sed -E 's|^jdbc:postgresql://[^/]*/([^?]*).*|\1|' <<<"$jdbc_url")"
  DB_NAME="${DB_NAME:-serverconf}"
  return 0
}

# PostgreSQL cannot start on the read-only source mount.
migration_database_copy_source() {
  local source_data="$SOURCE_DB_DIR/16/main"
  local copy_data="$TARGET_DB_ROOT/16/main"

  mkdir -p "$copy_data"
  if ! cp -a "$source_data/." "$copy_data/"; then
    STEP_REASON="failed to copy $source_data to $copy_data"
    return 1
  fi
  chown -R postgres:postgres "$copy_data"

  # The 7.8 cluster keeps its config files in /etc/postgresql/16/main, which is
  # not mounted; pg_ctl needs them in the data directory.
  : >"$copy_data/postgresql.conf"
  : >"$copy_data/pg_ident.conf"
  cat >"$copy_data/pg_hba.conf" <<'EOF'
host all all 127.0.0.1/32 trust
EOF
  chown postgres:postgres "$copy_data"/{postgresql.conf,pg_ident.conf,pg_hba.conf}
  chmod 0600 "$copy_data"/{postgresql.conf,pg_ident.conf,pg_hba.conf}
  return 0
}

# PostgreSQL 18 first: pg_ctlcluster creates the socket directory both use.
migration_database_start_servers() {
  local new_data="$TARGET_DB_ROOT/18/main"
  if [[ ! -s "$new_data/PG_VERSION" ]]; then
    mkdir -p "$new_data"
    chown postgres:postgres "$new_data"
    chmod 0700 "$new_data"
    if ! sudo -u postgres "$PG18_BINDIR/initdb" -D "$new_data" >>"$MIGRATION_DB_LOG" 2>&1; then
      STEP_REASON="failed to initialize the PostgreSQL 18 data directory at $new_data"
      return 1
    fi
  fi

  if ! pg_ctlcluster 18 main start >>"$MIGRATION_DB_LOG" 2>&1; then
    STEP_REASON="failed to start the target PostgreSQL 18"
    return 1
  fi

  if ! sudo -u postgres "$PG16_BINDIR/pg_ctl" -D "$TARGET_DB_ROOT/16/main" -w \
    -o "-p $PG16_PORT -c listen_addresses=127.0.0.1" start >>"$MIGRATION_DB_LOG" 2>&1; then
    STEP_REASON="failed to start PostgreSQL 16 on the copy of the source database"
    return 1
  fi
  return 0
}

migration_database_stop_servers() {
  pg_ctlcluster 18 main stop >>"$MIGRATION_DB_LOG" 2>&1 || true
  sudo -u postgres "$PG16_BINDIR/pg_ctl" -D "$TARGET_DB_ROOT/16/main" -w stop >>"$MIGRATION_DB_LOG" 2>&1 || true
}

migration_database_psql18() {
  sudo -u postgres "$PG18_BINDIR/psql" -X -q -v ON_ERROR_STOP=1 -p 5432 "$@"
}

# The postgres role already exists in the target, so its CREATE ROLE is dropped.
migration_database_dump_and_restore() {
  if ! sudo -u postgres "$PG18_BINDIR/pg_dumpall" -h 127.0.0.1 -p "$PG16_PORT" 2>>"$MIGRATION_DB_LOG" \
    | sed '/^CREATE ROLE postgres;$/d' \
    | migration_database_psql18 -d postgres >>"$MIGRATION_DB_LOG" 2>&1; then
    STEP_REASON="dump and restore from PostgreSQL 16 to 18 failed, see $MIGRATION_DB_LOG"
    return 1
  fi
  return 0
}

# As the serverconf user, like the native wizard: its triggers need its search_path.
migration_database_serverconf_psql() {
  PGPASSWORD="$DB_PASSWORD" "$PG18_BINDIR/psql" -X -q -v ON_ERROR_STOP=1 \
    -h 127.0.0.1 -p 5432 -U "$DB_USER" -d "$DB_NAME" "$@"
}

migration_database_deduplicate_and_count() {
  if ! migration_database_serverconf_psql -f "$DEDUP_SQL" >>"$MIGRATION_DB_LOG" 2>&1; then
    STEP_REASON="serverconf.identifier deduplication failed, see $MIGRATION_DB_LOG"
    return 1
  fi

  local count
  count="$(migration_database_serverconf_psql -tA -c 'SELECT COUNT(*) FROM serverconf.client' 2>>"$MIGRATION_DB_LOG")"
  if [[ -z "$count" ]]; then
    STEP_REASON="failed to read the client count"
    return 1
  fi
  echo "$count" >"$MIGRATION_CLIENT_COUNT_FILE"
  return 0
}

step_database() {
  mkdir -p /var/log/xroad
  migration_database_read_source_credentials || return 1
  migration_database_copy_source || return 1

  local rc=0
  migration_database_start_servers || rc=1
  if [[ $rc -eq 0 ]]; then
    migration_database_dump_and_restore || rc=1
  fi
  if [[ $rc -eq 0 ]]; then
    migration_database_deduplicate_and_count || rc=1
  fi
  migration_database_stop_servers

  rm -rf "$TARGET_DB_ROOT/16/main"
  return $rc
}
