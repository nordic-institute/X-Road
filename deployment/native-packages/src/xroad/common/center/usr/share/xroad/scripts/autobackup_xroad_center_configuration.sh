#!/bin/bash

source /usr/share/xroad/scripts/_backup_restore_common.sh

BACKUP_SCRIPT="/usr/share/xroad/scripts/backup_xroad_center_configuration.sh"
INSTANCE="$(source /usr/share/xroad/scripts/get_central_server_instance_id.sh)"
if [[ -n "${INSTANCE}" ]] ; then
  FILENAME="/var/lib/xroad/backup/cs-automatic-backup_${BACKUP_FORMAT_VERSION_LABEL}_$(date +%Y_%m_%d_%H%M%S).gpg"
  ${BACKUP_SCRIPT} -i ${INSTANCE} -f ${FILENAME}
fi
