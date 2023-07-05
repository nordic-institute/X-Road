#!/bin/bash
# Wrapper script for backing up the configuration of the X-Road central server.
# See $COMMON_BACKUP_SCRIPT for details.

source /usr/share/xroad/scripts/_backup_restore_common.sh

THIS_FILE=$(pwd)/$0

usage () {
cat << EOF

Usage: $0 -i <instance ID> [-n <HA node name>] -f <path of tar archive> [-S]

Backup the configuration (files and database) of the X-Road central server to a tar archive.

OPTIONS:
    -h Show this message and exit.
    -b Treat all input values as encoded in base64.
    -i Instance ID of the installation of X-Road.
    -n Node name of the central server if deployed in HA setup.
    -f Absolute path of the resulting tar archive.
    -S Skip database backup
EOF
}

execute_backup () {
  if [ -x ${COMMON_BACKUP_SCRIPT} ] ; then
    local args="-t central -i ${INSTANCE_ID} -f ${BACKUP_FILENAME}"
    if [[ $USE_BASE_64 = true ]] ; then
      args="${args} -b"
    fi
    if [ -n "${CENTRAL_SERVER_HA_NODE_NAME}" ] ; then
      args="${args} -n ${CENTRAL_SERVER_HA_NODE_NAME}"
    fi
    if [[ $SKIP_DB_BACKUP = true ]] ; then
      args="${args} -S"
    fi
    if [[ $ENCRYPT_BACKUP = true ]] ; then
      args="${args} -E encrypt"
    else
      args="${args} -E signonly"
    fi
    if [ -n "$GPG_KEYIDS" ]; then
      args="${args} -k ${GPG_KEYIDS}"
    fi
    ${COMMON_BACKUP_SCRIPT} ${args}
    if [ $? -ne 0 ] ; then
      echo "Failed to back up the configuration of the X-Road central server"
      exit 1
    fi
  else
    echo "Could not execute the backup script at ${COMMON_BACKUP_SCRIPT}"
    exit 1
  fi
}

while getopts ":i:n:f:Sbh" opt ; do
  case $opt in
    h)
      usage
      exit 0
      ;;
    S)
      SKIP_DB_BACKUP=true
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

ENCRYPT_BACKUP=$(get_server_prop center.ini center "backup-encryption-enabled" false)
echo "ENCRYPT_BACKUP=$ENCRYPT_BACKUP"
GPG_KEYIDS=$(get_server_prop center.ini center "backup-encryption-keyids")
echo "GPG_KEYIDS=$GPG_KEYIDS"

execute_backup

# vim: ts=2 sw=2 sts=2 et filetype=sh
