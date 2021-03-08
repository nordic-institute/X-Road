#!/bin/bash
# Wrapper script for backing up the configuration of the X-Road security server.
# See $COMMON_BACKUP_SCRIPT for details.

source /usr/share/xroad/scripts/_backup_restore_common.sh

COMMON_BACKUP_SCRIPT=/usr/share/xroad/scripts/_backup_xroad.sh
THIS_FILE=$(pwd)/$0

usage () {
cat << EOF

Usage: $0 -s <security server ID> -f <path of tar archive> [-S]

Backup the configuration (files and database) of the X-Road security server to a tar archive.

OPTIONS:
    -h Show this message and exit.
    -b Treat all input values as encoded in base64.
    -s ID of the security server.
    -f Absolute path of the resulting tar archive.
    -S Skip database backup
EOF
}

execute_backup () {
  if [ -x $COMMON_BACKUP_SCRIPT ] ; then
    local args="-t security -s $SECURITY_SERVER_ID -f $BACKUP_FILENAME"
    if [[ $USE_BASE_64 = true ]] ; then
      args="${args} -b"
    fi
    if [[ $SKIP_DB_BACKUP = true ]] ; then
      args="${args} -S"
    fi
    if [[ $ENCRYPT_BACKUP = true ]] ; then
      args="${args} -E"
    fi
    ${COMMON_BACKUP_SCRIPT} ${args}
    if [ $? -ne 0 ] ; then
      echo "Failed to back up the configuration of the X-Road security server"
      exit 1
    fi
  else
    echo "Could not execute the backup script at $COMMON_BACKUP_SCRIPT"
    exit 1
  fi
}

while getopts ":s:f:SbhE" opt ; do
  case $opt in
    h)
      usage
      exit 0
      ;;
    S)
      SKIP_DB_BACKUP=true
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
    E)
      ENCRYPT_BACKUP=true
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
execute_backup

# vim: ts=2 sw=2 sts=2 et filetype=sh
