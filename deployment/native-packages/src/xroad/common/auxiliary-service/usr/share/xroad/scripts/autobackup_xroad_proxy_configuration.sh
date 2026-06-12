#!/bin/bash

source /usr/share/xroad/scripts/_backup_restore_common.sh

ID=$(source /usr/share/xroad/scripts/get_security_server_id.sh)
if [[ -n "${ID}" ]] ; then
  SCRIPT="/usr/share/xroad/scripts/backup_xroad_proxy_configuration.sh"
  FILENAME="/var/lib/xroad/backup/ss-automatic-backup_${BACKUP_FORMAT_VERSION_LABEL}_$(date +%Y_%m_%d_%H%M%S).gpg"
  ${SCRIPT} -s "${ID}" -f "${FILENAME}"
fi
