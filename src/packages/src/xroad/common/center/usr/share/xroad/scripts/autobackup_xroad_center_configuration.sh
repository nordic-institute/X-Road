#!/bin/bash

BACKUP_SCRIPT="/usr/share/xroad/scripts/backup_xroad_center_configuration.sh"
INSTANCE="$(source /usr/share/xroad/scripts/get_central_server_instance_id.sh)"
if [[ -n "${INSTANCE}" ]] ; then
  FILENAME="/var/lib/xroad/backup/cs-automatic-backup-$(date +%Y_%m_%d_%H%M%S).tar"
  NODE="$(source /usr/share/xroad/scripts/get_ha_node_name.sh)"
  if [[ -n "${NODE}" ]] ; then
    ${BACKUP_SCRIPT} -i ${INSTANCE} -n ${NODE} -f ${FILENAME}
  else
    ${BACKUP_SCRIPT} -i ${INSTANCE} -f ${FILENAME}
  fi
fi
