#!/bin/bash
# Mock script for restoring central server from backup

while getopts ":FSi:n:f:bh" opt ; do
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

echo "Restoring configuration from ${BACKUP_FILENAME}"
exit 0

# vim: ts=2 sw=2 sts=2 et filetype=sh
