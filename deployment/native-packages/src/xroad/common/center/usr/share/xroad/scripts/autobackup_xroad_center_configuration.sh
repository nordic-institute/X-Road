#!/bin/bash

# shellcheck disable=SC1091
source /usr/share/xroad/scripts/_node_name.sh

BACKUP_SCRIPT="/usr/share/xroad/scripts/backup_xroad_center_configuration.sh"
INSTANCE="$(source /usr/share/xroad/scripts/get_central_server_instance_id.sh)"
if [[ -n "${INSTANCE}" ]] ; then
  FILENAME="/var/lib/xroad/backup/cs-automatic-backup-$(date +%Y_%m_%d_%H%M%S).gpg"
  HA_NODE_NAME="$(read_node_ini_node_name)" || exit 1
  if [[ -n "${HA_NODE_NAME//[[:space:]]/}" ]] ; then
    ${BACKUP_SCRIPT} -i "${INSTANCE}" -n "${HA_NODE_NAME}" -f "${FILENAME}"
  else
    ${BACKUP_SCRIPT} -i "${INSTANCE}" -f "${FILENAME}"
  fi
fi
