#!/bin/bash

# shellcheck disable=SC1091
source /usr/share/xroad/scripts/_node_name.sh

BACKUP_SCRIPT="/usr/share/xroad/scripts/backup_xroad_center_configuration.sh"
LOCAL_CONFIG="/etc/xroad/conf.d/local.yaml"
INSTANCE="$(source /usr/share/xroad/scripts/get_central_server_instance_id.sh)"
if [[ -n "${INSTANCE}" ]] ; then
  FILENAME="/var/lib/xroad/backup/cs-automatic-backup-$(date +%Y_%m_%d_%H%M%S).gpg"
  HA_NODE_NAME="$(/usr/share/xroad/scripts/yaml_helper.sh get-raw "${LOCAL_CONFIG}" "xroad.admin-service.ha-node-name" 2>/dev/null)"
  check_node_name "${HA_NODE_NAME}" "xroad.admin-service.ha-node-name in ${LOCAL_CONFIG}" || exit 1
  if [[ -n "${HA_NODE_NAME//[[:space:]]/}" ]] ; then
    ${BACKUP_SCRIPT} -i "${INSTANCE}" -n "${HA_NODE_NAME}" -f "${FILENAME}"
  else
    ${BACKUP_SCRIPT} -i "${INSTANCE}" -f "${FILENAME}"
  fi
fi
