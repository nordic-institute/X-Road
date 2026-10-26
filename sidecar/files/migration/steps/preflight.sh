#!/bin/bash
#
# Migration step 1: refuses to start unless the source is 7.8.x, a soft
# token's PIN is given and the target volumes are empty. Writes nothing.

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
  migration_preflight_check_source_version || return 1
  migration_preflight_check_target_config_empty || return 1
  migration_preflight_check_target_db_empty || return 1
  migration_preflight_check_softtoken_pin || return 1
  return 0
}
