#!/bin/bash
# Script for backing up the configuration files and database of the
# X-Road system.
# This script should be invoked by the wrapper script installed at the server
# that is being backed up.

source /usr/share/xroad/scripts/_backup_restore_common.sh

BACKED_UP_PATHS="/etc/xroad/ /etc/nginx/conf.d/*xroad*.conf"
if [ -d "/etc/nginx/sites-enabled" ]
then
    BACKED_UP_PATHS="$BACKED_UP_PATHS /etc/nginx/sites-enabled/*xroad*"
fi

THIS_FILE=$(pwd)/$0

die () {
    echo >&2 "$@"
    exit 1
}

create_database_backup () {
  if [ -x ${DATABASE_BACKUP_SCRIPT} ] ; then
    echo "CREATING DATABASE DUMP TO ${DATABASE_DUMP_FILENAME}"
    ${DATABASE_BACKUP_SCRIPT} ${DATABASE_DUMP_FILENAME}
    if [ $? -ne 0 ] ; then
      die "Database backup failed!" \
          "Please check the error messages and fix them before trying again!"
    fi
    BACKED_UP_PATHS="${BACKED_UP_PATHS} ${DATABASE_DUMP_FILENAME}"
  else
    die "Failed to execute the database backup script at ${DATABASE_BACKUP_SCRIPT}"
  fi
}

create_backup_tarball () {
  echo "CREATING TAR ARCHIVE TO ${BACKUP_FILENAME}"
  tar --create -v --label "${TARBALL_LABEL}" --file ${BACKUP_FILENAME} --exclude="/etc/xroad/postgresql" ${BACKED_UP_PATHS}
  if [ $? != 0 ] ; then
    echo "Removing incomplete backup archive"
    rm -v ${BACKUP_FILENAME}
    die "Creating a backup file to ${BACKUP_FILENAME} failed!" \
        "Please check the error messages and fix them before trying again!"
  fi
  echo "Backup file saved to ${BACKUP_FILENAME}"
}

while getopts ":t:i:s:n:f:b" opt ; do
  case $opt in
    t)
      SERVER_TYPE=$OPTARG
      ;;
    i)
      INSTANCE_ID=$OPTARG
      ;;
    s)
      SECURITY_SERVER_ID=$OPTARG
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
      echo "Invalid option $OPTARG -- did you use the correct wrapper script?"
      exit 2
      ;;
    :)
      echo "Option -$OPTARG requires an argument -- did you use the correct wrapper script?"
      exit 2
  esac
done

check_server_type
create_database_backup
make_tarball_label
create_backup_tarball

# vim: ts=2 sw=2 sts=2 et filetype=sh
