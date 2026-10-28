#!/bin/bash
#
# Migration step 3: copies the 7.8 configuration volume to the target and
# rewrites db.properties to the X-Road 8 keys with the native wizard's task.

MIGRATION_TASKS_DIR="${MIGRATION_TASKS_DIR:-/usr/share/xroad/scripts/sidecar/migration-tasks}"

migration_copy_tree() {
  local src="$1"
  local dst="$2"

  if ! cp -a "$src/." "$dst/"; then
    STEP_REASON="failed to copy $src to $dst"
    return 1
  fi

  return 0
}

migration_rewrite_db_properties() {
  local db_properties="$TARGET_CONFIG_DIR/db.properties"

  if [[ ! -f "$db_properties" ]]; then
    # The copy has no db.properties when the source configuration volume did
    # not have one; nothing to rewrite, and not a failure of this step.
    return 0
  fi

  # The task logs to stderr as well, which reaches the container log; its
  # log file would land in the working directory, which may be read-only.
  if ! XROAD_INSTALLER_LOG_FILE=/dev/null \
    bash "$MIGRATION_TASKS_DIR/tasks/migration/migrate_db_properties.sh"; then
    STEP_REASON="failed to rewrite db.properties keys at $db_properties"
    return 1
  fi

  return 0
}

step_config_copy() {
  migration_copy_tree "$SOURCE_CONFIG_DIR" "$TARGET_CONFIG_DIR" || return 1
  migration_rewrite_db_properties || return 1
  return 0
}
