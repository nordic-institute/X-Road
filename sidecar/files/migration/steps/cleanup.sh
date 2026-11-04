#!/bin/bash
#
# Migration step 5: deletes obsolete 7.8 files. The regular provisioning
# stops the local database.

# X-Road 8 ignores the obsolete files, so a file left behind only warns.
migration_cleanup_obsolete_files() {
  if ! XROAD_DELETE_OBSOLETE_FILES=yes bash "$MIGRATION_TASKS_DIR/tasks/migration/cleanup_obsolete_files.sh"; then
    echo "WARN: could not delete all obsolete 7.8 configuration files; remove them manually" >&2
  fi

  # The 7.8 sidecar autologin PIN file, in plain text.
  if ! rm -f "$TARGET_CONFIG_DIR/autologin"; then
    echo "WARN: could not delete $TARGET_CONFIG_DIR/autologin; delete it manually, it holds the 7.8 token PIN in plain text" >&2
  fi
}

step_cleanup() {
  migration_cleanup_obsolete_files
}
