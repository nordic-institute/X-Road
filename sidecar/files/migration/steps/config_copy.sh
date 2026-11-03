#!/bin/bash
#
# Migration step 2: copies the 7.8 configuration volume to the target and
# rewrites db.properties to the X-Road 8 keys with the native wizard's task.

migration_configuration_copy() {
  if ! cp -a "$SOURCE_CONFIG_DIR/." "$TARGET_CONFIG_DIR/"; then
    STEP_REASON="failed to copy $SOURCE_CONFIG_DIR to $TARGET_CONFIG_DIR"
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

  if ! bash "$MIGRATION_TASKS_DIR/tasks/migration/migrate_db_properties.sh"; then
    STEP_REASON="failed to rewrite db.properties keys at $db_properties"
    return 1
  fi

  return 0
}

step_config_copy() {
  migration_configuration_copy || return 1
  migration_rewrite_db_properties || return 1
  return 0
}
