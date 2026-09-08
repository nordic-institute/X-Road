#!/bin/bash

# The value in this file must be increased when we introduce changes that make earlier
# backup files incompatible with the current system. BackupMetadataService (Java) reads
# the same file to determine backup compatibility for the admin UI, so this is the
# single place to bump when the backup format changes.
BACKUP_FORMAT_VERSION_LABEL="$(cat /usr/share/xroad/scripts/containerised/backup_format_version)"

make_tarball_label () {
  TARBALL_LABEL="security_${BACKUP_FORMAT_VERSION_LABEL}_${SECURITY_SERVER_ID}"
}

write_backup_metadata_json () {
  local metadata_file="$1"
  local version="$2"
  local server_type="$3"
  echo "{\"version\":\"${version}\",\"server_type\":\"${server_type}\"}" > "${metadata_file}"
}
