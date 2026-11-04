#!/bin/bash
# Helper functions and common variables for the backup and restore scripts of X-Road.

# XXX Don't change this file name without a reason -- that will break backwards compatibilty
# with existing tarballs because the restore scripts expect to find a file with this
# name after unpacking the tarball.
umask 027

set -o pipefail

DATABASE_DUMP_FILENAME="/var/lib/xroad/dbdump.dat"
OPENBAO_DATABASE_DUMP_FILENAME="/var/lib/xroad/openbao_dbdump.dat"

DATABASE_BACKUP_SCRIPT="/usr/share/xroad/scripts/backup_db.sh"
DATABASE_RESTORE_SCRIPT="/usr/share/xroad/scripts/restore_db.sh"
OPENBAO_DATABASE_BACKUP_SCRIPT="/usr/share/xroad/scripts/backup_openbao_db.sh"
OPENBAO_DATABASE_RESTORE_SCRIPT="/usr/share/xroad/scripts/restore_openbao_db.sh"
COMMON_BACKUP_SCRIPT="/usr/share/xroad/scripts/_backup_xroad.sh"

# shellcheck source=/dev/null
source /usr/share/xroad/scripts/_node_name.sh

# The value in this file must be increased when we introduce changes that make earlier
# backup files incompatible with the current system. BackupMetadataService (Java) reads
# the same file to determine backup compatibility for the admin UI, so this is the
# single place to bump when the backup format changes.
BACKUP_FORMAT_VERSION_LABEL="$(cat /usr/share/xroad/scripts/_backup_format_version)"

die () {
    echo >&2 "$@"
    exit 1
}

check_user () {
  if [ "$(id -nu )" != "xroad" ] ; then
    echo "This script (${THIS_FILE}) must be run by the xroad user"
    exit 2
  fi
}

check_instance_id () {
  if [[ -z ${FORCE_RESTORE} && -z "${INSTANCE_ID}" ]] ; then
    echo "Missing value of instance ID"
    usage
    exit 2
  fi
  if [[ $USE_BASE_64 = true ]] ; then
    INSTANCE_ID=$(echo "$INSTANCE_ID" | base64 --decode)
  fi
}

check_central_ha_node_name () {
  local node_name

  # A forced restore neither forwards the node name nor checks the tarball label against it.
  if [[ -n $FORCE_RESTORE ]] ; then
    return 0
  fi
  if [[ $USE_BASE_64 = true ]] ; then
    CENTRAL_SERVER_HA_NODE_NAME=$(echo "$CENTRAL_SERVER_HA_NODE_NAME" | base64 --decode)
  fi
  check_node_name "$CENTRAL_SERVER_HA_NODE_NAME" "the -n option" || exit 2
  if [[ -z "${CENTRAL_SERVER_HA_NODE_NAME//[[:space:]]/}" ]] ; then
    CENTRAL_SERVER_HA_NODE_NAME=""
  fi
  node_name=$(read_local_yaml_node_name "/etc/xroad/conf.d/local.yaml") || exit 2
  if [[ -z "${node_name//[[:space:]]/}" ]] ; then
    node_name=""
  fi
  if [[ -n $node_name && "$node_name" != "$CENTRAL_SERVER_HA_NODE_NAME" ]]; then
    echo "Expected '$node_name', but HA node name was '$CENTRAL_SERVER_HA_NODE_NAME'"
    usage
    exit 2
  fi
}

check_security_server_id () {
  if [[ -z ${FORCE_RESTORE} && -z "${SECURITY_SERVER_ID}" ]] ; then
    echo "Missing value of security server ID"
    usage
    exit 2
  fi
  if [[ $USE_BASE_64 = true ]] ; then
    SECURITY_SERVER_ID=$(echo "$SECURITY_SERVER_ID" | base64 --decode)
  fi
}

check_backup_file_name () {
  if [ -z "${BACKUP_FILENAME}" ] ; then
    echo "Missing value of backup tar file name"
    usage
    exit 2
  fi
  if [[ $USE_BASE_64 = true ]] ; then
    BACKUP_FILENAME=$(echo "$BACKUP_FILENAME" | base64 --decode)
  fi
}

check_server_type () {
  # We don't support base64 in the type of server because the allowed values
  # are a predefined set anyway.
  case ${SERVER_TYPE} in
    security)
      if [[ -z ${FORCE_RESTORE} && -z ${SECURITY_SERVER_ID} ]] ; then
        die "Missing security server ID -- did you use the correct wrapper script?"
      fi
      ;;
    central)
      ;;
    *)
      die "Invalid server type -- did you use the correct wrapper script?"
      ;;
  esac
}

# The tarball label encodes server type, backup format version, and server
# identity: <server_type>_<format_version>_<identity>.
make_tarball_label () {
  case ${SERVER_TYPE} in
    security)
      TARBALL_LABEL="security_${BACKUP_FORMAT_VERSION_LABEL}_${SECURITY_SERVER_ID}"
      ;;
    central)
      TARBALL_LABEL="central_${BACKUP_FORMAT_VERSION_LABEL}_${INSTANCE_ID}"
      if [ -n "${CENTRAL_SERVER_HA_NODE_NAME}" ] ; then
        TARBALL_LABEL="${TARBALL_LABEL}_${CENTRAL_SERVER_HA_NODE_NAME}"
      fi
      ;;
    *)
      # The type of server has been checked already.
      ;;
  esac
}

has_command () {
    command -v "$1" &>/dev/null
}

write_backup_metadata_json () {
  local metadata_file="$1"
  local version="$2"
  local server_type="$3"
  echo "{\"version\":\"${version}\",\"server_type\":\"${server_type}\"}" > "${metadata_file}"
}

get_server_prop () {
  local value
  if ! value=$(crudini --get /etc/xroad/conf.d/local.ini "$2" "$3" 2>/dev/null); then
    value=$(crudini --get /etc/xroad/conf.d/"$1" "$2" "$3" 2>/dev/null || echo "$4")
  fi
  echo "$value"
}

# vim: ts=2 sw=2 sts=2 et filetype=sh
