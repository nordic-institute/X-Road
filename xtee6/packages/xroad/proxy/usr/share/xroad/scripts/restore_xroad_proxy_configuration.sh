#!/bin/bash
# Wrapper script for restoring the configuration of the X-Road central server.
# See $COMMON_RESTORE_SCRIPT for details.

source /usr/share/xroad/scripts/_backup_restore_common.sh

COMMON_RESTORE_SCRIPT=/usr/share/xroad/scripts/_restore_xroad.sh
THIS_FILE=$(pwd)/$0

usage () {
cat << EOF

Usage: $0 -s <security server ID> -f <path of tar archive> [-F]

Restore the configuration (files and database) of the X-Road security server
from a tar archive.

OPTIONS:
    -h Show this message and exit.
    -b Treat all input values as encoded in base64.
    -s ID of the security server. Mandatory if -F is not used.
    -f Absolute path of the tar archive to be used for restoration. Mandatory.
    -F Force restoration, taking only the type of server into account.
EOF
}

execute_restore () {
  if [ -x ${COMMON_RESTORE_SCRIPT} ] ; then
    local args="-t security -f ${BACKUP_FILENAME}"
    if [ -n ${FORCE_RESTORE} ] && [[ ${FORCE_RESTORE} = true ]] ; then
      args="${args} -F"
    else
      args="${args} -s ${SECURITY_SERVER_ID}"
      if [[ $USE_BASE_64 = true ]] ; then
        args="${args} -b"
      fi
    fi
    sudo -u root ${COMMON_RESTORE_SCRIPT} ${args} 2>&1
    if [ $? -ne 0 ] ; then
      echo "Failed to restore the configuration of the X-Road security server"
      exit 1
    fi
  else
    echo "Could not execute the restore script at ${COMMON_RESTORE_SCRIPT}"
    exit 1
  fi
}

while getopts ":Fs:f:bh" opt ; do
  case $opt in
    h)
      usage
      exit 0
      ;;
    F)
      FORCE_RESTORE=true
      ;;
    s)
      SECURITY_SERVER_ID=$OPTARG
      ;;
    f)
      BACKUP_FILENAME=$OPTARG
      ;;
    b)
      USE_BASE_64=true
      ;;
    \?)
      echo "Invalid option $OPTARG"
      usage
      exit 2
      ;;
    :)
      echo "Option -$OPTARG requires an argument"
      usage
      exit 2
      ;;
  esac
done

check_user
check_security_server_id
check_backup_file_name
execute_restore

# vim: ts=2 sw=2 sts=2 et filetype=sh
