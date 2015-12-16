#!/bin/bash
# Script for restoring the database and configuration files of the X-Road system.
# The suitability of the backup file for a given server is determined by comparing
# the label of the backup tarball with the expected label constructed based on user
# input.
# This script should be invoked by the wrapper script installed at the server
# that is being backed up.

source /usr/share/xroad/scripts/_backup_restore_common.sh

# XXX Keep the pre-restore database dump named just like the rest of the dumps.
# This allows the user to restore the pre-restore backup just like any other
# backup.
PRE_RESTORE_DATABASE_DUMP_FILENAME=${DATABASE_DUMP_FILENAME}
PRE_RESTORE_TARBALL_FILENAME="/var/lib/xroad/conf_prerestore_backup.tar"

V55_XROAD6_INSTALLED="/usr/xtee/etc/v6_xroad_installed"
V55_XROAD6_ACTIVATED="/usr/xtee/etc/v6_xroad_activated"
V6_INTERNAL_TLS_KEY_EXPORTER="/usr/share/xroad/scripts/export_v6_internal_tls_key.sh"

THIS_FILE=$(pwd)/$0
XROAD_SERVICES=

check_is_correct_tarball () {
  tar tf ${BACKUP_FILENAME} > /dev/null
  if [ $? -ne 0 ] ; then
    die "Invalid tar archive in ${BACKUP_FILENAME}. Aborting restore!"
  fi
}

check_tarball_label () {
  # Expecting the value has been validated and the error message has been given in
  # wrapper scripts.
  echo "CHECKING THE LABEL OF THE TAR ARCHIVE"
  if [[ $FORCE_RESTORE = true ]] ; then
    # In forced mode, the restore script can be run on blank systems as long as
    # the type of server matches that in the tarball label.
    local existing_label=$(tar tf ${BACKUP_FILENAME} | head -1)
    if [[ ${existing_label} != ${SERVER_TYPE}* ]] ; then
      die "The beginning of the label does not contain the correct server type"
    fi
  else
    tar --test-label --file ${BACKUP_FILENAME} --label "${TARBALL_LABEL}"
    if [ $? -ne 0 ] ; then
      die "The expected label (${TARBALL_LABEL}) and the actual label of the" \
          "tarball ${BACKUP_FILENAME} do not match. Aborting restore!"
    fi
  fi
  echo "RESTORING CONFIGURATION FROM ${BACKUP_FILENAME}"
}

clear_shared_memory () {
  echo "CLEARING SHARED MEMORY"
  ipcrm -m `ipcs -m | grep xroad | awk '{print $2}'` 2>/dev/null || true
  ipcrm -s `ipcs -s | grep xroad | awk '{print $2}'` 2>/dev/null || true
}

stop_services () {
  echo "STOPPING ALL SERVICES EXCEPT JETTY"
  XROAD_SERVICES=$(initctl list | grep -E  "^xroad-|^xtee55-" | grep -v -- -jetty | cut -f 1 -d " ")
  for service in ${XROAD_SERVICES} ; do
    initctl stop ${service}
  done
}

create_pre_restore_backup () {
  echo "CREATING PRE-RESTORE BACKUP"
  # FIXME: deal with spaces in file names when using find and tar and combining
  # the result with other file names.
  #local backed_up_files="$(find /etc/xroad/ -type f) /etc/nginx/sites-enabled/*"
  local backed_up_files="/etc/xroad/ /etc/nginx/sites-enabled/"

  if [ -x ${DATABASE_BACKUP_SCRIPT} ] ; then
    echo "Creating database dump to ${PRE_RESTORE_DATABASE_DUMP_FILENAME}"
    ${DATABASE_BACKUP_SCRIPT} ${PRE_RESTORE_DATABASE_DUMP_FILENAME}
    if [ $? -ne 0 ] ; then
      die "Error occured while creating pre-restore database backup" \
          "to ${PRE_RESTORE_DATABASE_DUMP_FILENAME}"
    fi
    backed_up_files="${backed_up_files} ${PRE_RESTORE_DATABASE_DUMP_FILENAME}"
  else
    die "Failed to execute database backup script at ${DATABASE_BACKUP_SCRIPT} for" \
        "doing pre-restore backup"
  fi

  echo "Creating pre-restore backup archive to ${PRE_RESTORE_TARBALL_FILENAME}:"
  tar --create -v \
    --label "${TARBALL_LABEL}" --file ${PRE_RESTORE_TARBALL_FILENAME} ${backed_up_files}
  if [ $? != 0 ] ; then
    die "Creating pre-restore backup archive to ${PRE_RESTORE_TARBALL_FILENAME} failed"
  fi
  # FIXME: ei tohi koristada nginxi kataloogi ennast, aga kasuta backed_up_files sisu
  # Vt. eelmist FIXME-d.
  rm -rf /etc/xroad/*
  rm -rf /etc/nginx/sites-enabled/*
  #rm -r ${backed_up_files}
  #if [ $? -ne 0 ] ; then
  #  die "Failed to remove files before restore"
  #fi
}

restore_configuration_files () {
  echo "RESTORING CONFIGURATION FROM ${BACKUP_FILENAME}"
  echo "Restoring files:"
  tar xfv ${BACKUP_FILENAME} -C /
}

restore_database () {
  if [ -n ${SKIP_DB_RESTORE} ] && [[ ${SKIP_DB_RESTORE} = true ]] ; then
    echo "SKIPPING DB RESTORE AS REQUESTED"
  else
    if [ -x ${DATABASE_RESTORE_SCRIPT} ] && [ -e ${DATABASE_DUMP_FILENAME} ] ; then
      echo "RESTORING DATABASE FROM ${DATABASE_DUMP_FILENAME}"
      ${DATABASE_RESTORE_SCRIPT} ${DATABASE_DUMP_FILENAME} 1>/dev/null
      if [ $? -ne 0 ] ; then
        die "Failed to restore database!"
      fi
    else
      die "Failed to execute database restore script at ${DATABASE_RESTORE_SCRIPT}"
    fi
  fi
}

restart_services () {
  echo "RESTARTING SERVICES"
  for service in ${XROAD_SERVICES} ; do
    initctl start $service
  done
}

export_v55_key_and_cert () {
  if [ -f ${V55_XROAD6_INSTALLED} ] && [ -f ${V55_XROAD6_ACTIVATED} ] ; then
    echo "EXPORTING INTERNAL TLS KEY AND CERTIFICATE TO 5.0 X-ROAD PROXY"
    su - ui -c ${V6_INTERNAL_TLS_KEY_EXPORTER}
    if [ $? -ne 0 ] ; then
      die "Failed to export the internal TLS key and certificate to 5.0 X-Road proxy!"
    fi
  fi
}

while getopts ":FSt:i:s:n:f:b" opt ; do
  case $opt in
    F)
      FORCE_RESTORE=true
      ;;
    S)
      SKIP_DB_RESTORE=true
      ;;
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
check_is_correct_tarball
make_tarball_label
check_tarball_label
clear_shared_memory
stop_services
create_pre_restore_backup
restore_configuration_files
restore_database
restart_services
export_v55_key_and_cert

# vim: ts=2 sw=2 sts=2 et filetype=sh
