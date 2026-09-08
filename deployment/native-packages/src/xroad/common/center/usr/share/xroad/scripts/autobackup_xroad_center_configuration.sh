#!/bin/bash

BACKUP_SCRIPT="/usr/share/xroad/scripts/backup_xroad_center_configuration.sh"
INSTANCE="$(source /usr/share/xroad/scripts/get_central_server_instance_id.sh)"
if [[ -n "${INSTANCE}" ]] ; then
  FILENAME="/var/lib/xroad/backup/cs-automatic-backup-$(date +%Y_%m_%d_%H%M%S).gpg"
  HA_NODE_NAME="$(/usr/share/xroad/scripts/yaml_helper.sh get /etc/xroad/conf.d/local.yaml "xroad.admin-service.ha-node-name" 2>/dev/null)"
  if [[ -n "${HA_NODE_NAME}" ]] ; then
    ${BACKUP_SCRIPT} -i ${INSTANCE} -n ${HA_NODE_NAME} -f ${FILENAME}
  else
    ${BACKUP_SCRIPT} -i ${INSTANCE} -f ${FILENAME}
  fi
fi
