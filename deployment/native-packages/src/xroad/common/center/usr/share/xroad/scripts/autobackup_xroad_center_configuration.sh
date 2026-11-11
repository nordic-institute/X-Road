#!/bin/bash

BACKUP_SCRIPT="/usr/share/xroad/scripts/backup_xroad_center_configuration.sh"
INSTANCE="$(source /usr/share/xroad/scripts/get_central_server_instance_id.sh)"
if [[ -n "${INSTANCE}" ]] ; then
  FILENAME="/var/lib/xroad/backup/cs-automatic-backup-$(date +%Y_%m_%d_%H%M%S).gpg"
  HA_NODE_NAME="$(crudini --get /etc/xroad/conf.d/node.ini node name 2>/dev/null || echo "")"
  if [[ -n "${HA_NODE_NAME//[[:space:]]/}" ]] ; then
    ${BACKUP_SCRIPT} -i "${INSTANCE}" -n "${HA_NODE_NAME}" -f "${FILENAME}"
  else
    ${BACKUP_SCRIPT} -i "${INSTANCE}" -f "${FILENAME}"
  fi
fi
