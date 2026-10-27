#!/bin/bash
#
# Migration step 1: refuses to start unless the source is 7.8.x, a soft
# token's PIN is given and the target volumes are empty. Writes nothing.
#
# Also detects the database shape the later steps branch on: SOURCE_DB_HOST
# and SOURCE_DB_PORT, parsed from the source db.properties, are a local
# 127.0.0.1 address for a local database and the remote server's address for
# an external one.

migration_preflight_detect_db_shape() {
  local db_props="$SOURCE_CONFIG_DIR/db.properties"
  local jdbc_url
  jdbc_url="$(crudini --get "$db_props" '' 'serverconf.hibernate.connection.url' 2>/dev/null)"

  SOURCE_DB_HOST="127.0.0.1"
  SOURCE_DB_PORT="5432"
  if [[ "$jdbc_url" =~ ^jdbc:postgresql://([^/:]+)(:([0-9]+))?/ ]]; then
    SOURCE_DB_HOST="${BASH_REMATCH[1]}"
    SOURCE_DB_PORT="${BASH_REMATCH[3]:-5432}"
  fi

  if [[ "$SOURCE_DB_HOST" != 127.* ]]; then
    echo "External database detected at $SOURCE_DB_HOST:$SOURCE_DB_PORT: migrated in place, the operator's backup of it is the rollback point."
  fi
}

migration_preflight_check_source_version() {
  local version_file="$SOURCE_CONFIG_DIR/VERSION"

  if [[ ! -f "$version_file" ]]; then
    STEP_REASON="source configuration version not found: $version_file is missing"
    return 1
  fi

  local version
  version="$(tr -d '[:space:]' <"$version_file")"

  if [[ ! "$version" =~ ^7\.8\. ]]; then
    STEP_REASON="source configuration version is not 7.8.x (found: ${version:-empty})"
    return 1
  fi

  return 0
}

# Docker seeds a new volume with the image's /etc/xroad, so "empty" means
# never provisioned: no db.properties and no TLS material.
migration_preflight_check_target_config_empty() {
  local dir="$TARGET_CONFIG_DIR"

  if [[ ! -d "$dir" ]]; then
    STEP_REASON="target volume $dir does not exist"
    return 1
  fi

  if [[ -f "$dir/db.properties" ]]; then
    STEP_REASON="target volume $dir is not empty: db.properties already exists"
    return 1
  fi

  if compgen -G "$dir/ssl/*.crt" >/dev/null \
    || compgen -G "$dir/ssl/*.key" >/dev/null \
    || compgen -G "$dir/ssl/*.p12" >/dev/null; then
    STEP_REASON="target volume $dir is not empty: TLS material already exists under $dir/ssl"
    return 1
  fi

  return 0
}

# Step 2 restores into the target's 18/main. A never-booted volume holds only
# the built-in databases (OIDs 1, 4, 5); anything else is refused.
migration_preflight_check_target_db_empty() {
  local copy_data="$TARGET_DB_ROOT/16/main"
  local new_data="$TARGET_DB_ROOT/18/main"

  if [[ -d "$copy_data" && -n "$(ls -A "$copy_data")" ]]; then
    STEP_REASON="target database volume $TARGET_DB_ROOT is not empty: $copy_data already exists"
    return 1
  fi

  local db_dir
  for db_dir in "$new_data"/base/*; do
    [[ -e "$db_dir" ]] || continue
    case "${db_dir##*/}" in
      1 | 4 | 5) ;;
      *)
        STEP_REASON="target database volume $TARGET_DB_ROOT is not empty: $new_data already holds a database"
        return 1
        ;;
    esac
  done

  return 0
}

# The schema migration (Liquibase) and the deduplication run in place on an
# external server, so it must already be new enough to take them.
migration_preflight_check_external_db_version() {
  [[ "$SOURCE_DB_HOST" != 127.* ]] || return 0

  local db_props="$SOURCE_CONFIG_DIR/db.properties"
  local user password jdbc_url db_name
  user="$(crudini --get "$db_props" '' 'serverconf.hibernate.connection.username' 2>/dev/null)"
  password="$(crudini --get "$db_props" '' 'serverconf.hibernate.connection.password' 2>/dev/null)"
  jdbc_url="$(crudini --get "$db_props" '' 'serverconf.hibernate.connection.url' 2>/dev/null)"
  db_name="$(sed -E 's|^jdbc:postgresql://[^/]*/([^?]*).*|\1|' <<<"$jdbc_url")"
  db_name="${db_name:-serverconf}"

  local version rc
  version="$(PGCONNECT_TIMEOUT=5 PGPASSWORD="$password" "${PG18_BINDIR:-/usr/lib/postgresql/18/bin}/psql" -X -q \
    -h "$SOURCE_DB_HOST" -p "$SOURCE_DB_PORT" -U "$user" -d "$db_name" -tAc 'SHOW server_version_num' 2>/dev/null)"
  rc=$?

  if [[ $rc -ne 0 ]]; then
    STEP_REASON="could not connect to the external database at $SOURCE_DB_HOST:$SOURCE_DB_PORT to check its PostgreSQL version (check the server is reachable and the serverconf credentials are correct)"
    return 1
  fi

  if ! [[ "$version" =~ ^[0-9]+$ ]]; then
    STEP_REASON="external database at $SOURCE_DB_HOST:$SOURCE_DB_PORT returned an unexpected server_version_num: ${version:-empty}"
    return 1
  fi

  if [[ "$version" -lt 150000 ]]; then
    STEP_REASON="external database at $SOURCE_DB_HOST:$SOURCE_DB_PORT runs PostgreSQL older than 15 (version: $version)"
    return 1
  fi

  return 0
}

# The migration CLI's keyconf step needs the soft token PIN once.
migration_preflight_check_softtoken_pin() {
  local softtoken_p12="$SOURCE_CONFIG_DIR/signer/softtoken/.softtoken.p12"

  if [[ ! -f "$softtoken_p12" || -n "${XROAD_MIGRATION_SOFTTOKEN_PIN:-}" ]]; then
    return 0
  fi

  STEP_REASON="a 7.8 soft token exists at $softtoken_p12 but XROAD_MIGRATION_SOFTTOKEN_PIN is not set"
  return 1
}

step_preflight() {
  migration_preflight_detect_db_shape
  migration_preflight_check_source_version || return 1
  migration_preflight_check_target_config_empty || return 1
  if [[ "$SOURCE_DB_HOST" == 127.* ]]; then
    migration_preflight_check_target_db_empty || return 1
  fi
  migration_preflight_check_external_db_version || return 1
  migration_preflight_check_softtoken_pin || return 1
  return 0
}
