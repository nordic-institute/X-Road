#!/bin/bash
# Wrapper script for restoring the configuration of the X-Road central server.
# See $COMMON_RESTORE_SCRIPT for details.

source /usr/share/xroad/scripts/_backup_restore_common.sh

COMMON_RESTORE_SCRIPT=/usr/share/xroad/scripts/_restore_xroad.sh
THIS_FILE=$(pwd)/$0

usage () {
cat << EOF

Usage: $0 -i <instance ID> [-n <HA node name>] -f <path of tar archive> [-F] [-S]

Restore the configuration (files and database) of the X-Road central server
from a tar archive.

OPTIONS:
    -h Show this message and exit.
    -b Treat all input values as encoded in base64.
    -i Instance ID of the installation of X-Road. Mandatory if -F is not used.
    -n Node name of the central server if deployed in HA setup.
       Mandatory in HA setup if -F is not used.
    -f Absolute path of the tar archive to be used for restoration. Mandatory.
    -F Force restoration, taking only the type of server into account.
    -P Backup archive is in unencrypted TAR format NOT in GPG format (not encrypted nor signed).
    -N Skip GPG signature verification
    -S Skip database restoration.
EOF
}

execute_restore () {
  if [ -x ${COMMON_RESTORE_SCRIPT} ] ; then
    local args="-t central -f ${BACKUP_FILENAME}"
    if [ -n "${FORCE_RESTORE}" ] && [[ ${FORCE_RESTORE} = true ]] ; then
      args="${args} -F"
    else
      args="${args} -i ${INSTANCE_ID}"
      if [[ $USE_BASE_64 = true ]] ; then
        args="${args} -b"
      fi
      if [ -n "${CENTRAL_SERVER_HA_NODE_NAME}" ] ; then
        args="${args} -n ${CENTRAL_SERVER_HA_NODE_NAME}"
      fi
    fi
    if [ -n "${SKIP_DB_RESTORE}" ] && [[ ${SKIP_DB_RESTORE} = true ]] ; then
      args="${args} -S"
    fi
    if [[ $PLAIN_BACKUP != true ]] ; then
      args="${args} -E"
    fi
    if [[ $SKIP_SIGNATURE_CHECK = true ]] ; then
      args="${args} -N"
    fi
    sudo -u root ${COMMON_RESTORE_SCRIPT} ${args} 2>&1
    if [ $? -ne 0 ] ; then
      echo "Failed to restore the configuration of the X-Road central server"
      exit 1
    fi
  else
    echo "Could not execute the restore script at ${COMMON_RESTORE_SCRIPT}"
    exit 1
  fi
}

while getopts ":FSi:n:f:bhPN" opt ; do
  case $opt in
    h)
      usage
      exit 0
      ;;
    F)
      FORCE_RESTORE=true
      ;;
    S)
      SKIP_DB_RESTORE=true
      ;;
    i)
      INSTANCE_ID=$OPTARG
      ;;
    n)
      CENTRAL_SERVER_HA_NODE_NAME=$OPTARG
      ;;
    f)
      BACKUP_FILENAME=$OPTARG
      ;;
    b)
      USE_BASE_64=true
      ;;
    P)
      PLAIN_BACKUP=true
      ;;
    N)
      SKIP_SIGNATURE_CHECK=true
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
check_instance_id
check_central_ha_node_name
check_backup_file_name
execute_restore

# vim: ts=2 sw=2 sts=2 et filetype=sh
