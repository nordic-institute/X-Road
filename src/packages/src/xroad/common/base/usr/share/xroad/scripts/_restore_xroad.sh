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

RESTORE_DIR=/var/tmp/xroad/restore
TEMP_TAR_DIR=/var/tmp/xroad
TEMP_TAR_FILE=${TEMP_TAR_DIR}/decrypted_temporary.tar

RESTORE_LOCK_FILENAME="/var/lib/xroad/restore_lock"
RESTORE_IN_PROGRESS_FILENAME="/var/lib/xroad/restore_in_progress"

THIS_FILE=$(pwd)/$0
XROAD_SERVICES=

acquire_lock () {
    [ "${FLOCKER}" != "$0" ] && exec env FLOCKER="$0" flock -n $RESTORE_LOCK_FILENAME "$0" "$@" || true
    touch "${RESTORE_IN_PROGRESS_FILENAME}"
}

check_is_correct_tarball () {
  if ! tar tf "${BACKUP_FILENAME}" > /dev/null; then
    die "Invalid tar archive in ${BACKUP_FILENAME}. Aborting restore!"
  fi
}

check_restore_options () {
  if ! tar tf "$BACKUP_FILENAME" var/lib/xroad/dbdump.dat &>/dev/null; then
    echo "The backup archive does not contain database dump. Skipping database restore."
    SKIP_DB_RESTORE=true
  fi
}

decrypt_tarball_if_encrypted () {
    if [[ $ENCRYPTED_BACKUP = true ]] ; then
      rm  -f ${TEMP_TAR_FILE}
      mkdir -p ${TEMP_TAR_DIR}
      GPG_FILENAME=${BACKUP_FILENAME}
      BACKUP_FILENAME=${TEMP_TAR_FILE}
      echo "Exctracting encrypted tarball to ${BACKUP_FILENAME}"
      # gpg --decrypt can handle files that are only signed!
      gpg --homedir /etc/xroad/gpghome --decrypt --output ${BACKUP_FILENAME} ${GPG_FILENAME}
      if [ $? != 0 ] ; then
        die "Decrypting backup archive failed"
      fi
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
  ipcrm -m "$(ipcs -m | grep xroad | awk '{print $2}')" 2>/dev/null || true
  ipcrm -s "$(ipcs -s | grep xroad | awk '{print $2}')" 2>/dev/null || true
}

select_commands () {
  if has_command initctl
  then
    STOP_CMD="initctl stop"
    START_CMD="initctl start"
  elif has_command systemctl
  then
    STOP_CMD="systemctl stop"
    START_CMD="systemctl start"
  else
    die "Cannot control X-Road services (initctl/systemctl not found). Aborting restore"
  fi
}

stop_services () {
  echo "STOPPING REGISTERED SERVICES"
  select_commands
  for entry in "/etc/xroad/backup.d/"* ; do
    if  [[ -f ${entry} ]] ; then
      servicename=`basename "$entry" | sed 's/.*_//'`
      echo ${STOP_CMD} "${servicename}"
      ${STOP_CMD} "${servicename}"
    fi
  done
}

create_pre_restore_backup () {
  echo "CREATING PRE-RESTORE BACKUP"
  # we will run this through eval to get a multi-line list
  local backed_up_files_cmd="find /etc/xroad -not -path '/etc/xroad/postgresql/*' \
    -not -path '/etc/xroad/services/*.conf' -not -path '/etc/xroad/gpghome/*' -type f; \
    find /etc/nginx/ -name \"*xroad*\""

  if [ -x ${DATABASE_BACKUP_SCRIPT} ] ; then
    echo "Creating database dump to ${PRE_RESTORE_DATABASE_DUMP_FILENAME}"
    ${DATABASE_BACKUP_SCRIPT} ${PRE_RESTORE_DATABASE_DUMP_FILENAME}
    if [ $? -ne 0 ] ; then
      # allow force restore even when schema does not exist
      if [[ $FORCE_RESTORE == true ]] ; then
        echo "Ignoring pre restore db backup errors"
      else
        die "Error occured while creating pre-restore database backup" \
            "to ${PRE_RESTORE_DATABASE_DUMP_FILENAME}"
      fi
    fi
    backed_up_files_cmd="${backed_up_files_cmd}; echo ${PRE_RESTORE_DATABASE_DUMP_FILENAME}"
  else
    die "Failed to execute database backup script at ${DATABASE_BACKUP_SCRIPT} for" \
        "doing pre-restore backup"
  fi

  CONF_FILE_LIST=$(eval ${backed_up_files_cmd})

  echo "Creating pre-restore backup archive to ${PRE_RESTORE_TARBALL_FILENAME}:"
  tar --create -v \
    --label "${TARBALL_LABEL}" --file ${PRE_RESTORE_TARBALL_FILENAME} -T  <(echo "${CONF_FILE_LIST}")
  if [ $? != 0 ] ; then
    die "Creating pre-restore backup archive to ${PRE_RESTORE_TARBALL_FILENAME} failed"
  fi
}

remove_old_existing_files () {
  if [[ $SKIP_REMOVAL != true ]] ; then
    echo "Removing old existing files"
    echo "$CONF_FILE_LIST" | xargs -I {} rm {}
    if [ $? -ne 0 ] ; then
      die "Failed to remove files before restore"
    fi
  else
    echo "Skipping existing file removal"
  fi
}

setup_tmp_restore_dir() {
  rm -rf ${RESTORE_DIR}
  mkdir -p ${RESTORE_DIR}
}

extract_to_tmp_restore_dir () {
  # Restore to temporary directory and fix permissions before copying
  # etc/xroad is always included in the backup, etc/nginx only when backup is for CS
  tar xfv ${BACKUP_FILENAME} -C ${RESTORE_DIR} --exclude="*.conf" --exclude="gpghome/*" etc/xroad || die "Extracting etc/xroad failed"
  if tar -tf ${BACKUP_FILENAME} etc/nginx >/dev/null 2>&1; then
    echo "Extracting tar archive to etc/nginx"
    tar xfv ${BACKUP_FILENAME} -C ${RESTORE_DIR} etc/nginx || die "Extracting etc/nginx failed"
  else
    echo "No etc/nginx in backup"
  fi

  # dbdump is optional
  if [[ $SKIP_DB_RESTORE != true ]] ; then
    tar xfv ${BACKUP_FILENAME} -C ${RESTORE_DIR} var/lib/xroad/dbdump.dat
  fi
  # keep existing db.properties
  if [ -f /etc/xroad/db.properties ]
  then
      mv ${RESTORE_DIR}/etc/xroad/db.properties ${RESTORE_DIR}/etc/xroad/db.properties.restored
      cp /etc/xroad/db.properties ${RESTORE_DIR}/etc/xroad/db.properties
  fi
  chown -R xroad:xroad ${RESTORE_DIR}/*
  # reset permissions of all files to fixec, "safe" values
  chmod -R a-x,o=,u=rwX,g=rX "$RESTORE_DIR"
}

restore_configuration_files () {
  echo "RESTORING CONFIGURATION FROM ${BACKUP_FILENAME}"
  echo "Restoring files:"
  # restore files
  Z=""
  if cp --help | grep -q "\-Z"; then
    Z="-Z"
  fi

  cp -v -a ${Z} ${RESTORE_DIR}/etc/xroad -t /etc
  cp -v -r ${Z} ${RESTORE_DIR}/etc/nginx -t /etc
  if [[ $SKIP_DB_RESTORE != true ]] ; then
    cp -v -a ${Z} ${RESTORE_DIR}/var/lib/xroad/dbdump.dat -t /var/lib/xroad/
  fi
}

restore_database () {
  if [[ -n ${SKIP_DB_RESTORE} && ${SKIP_DB_RESTORE} = true ]] ; then
    echo "SKIPPING DB RESTORE AS REQUESTED"
  else
    if [[ -x ${DATABASE_RESTORE_SCRIPT} && -e ${DATABASE_DUMP_FILENAME} ]] ; then
      echo "RESTORING DATABASE FROM ${DATABASE_DUMP_FILENAME}"
      if [[ $FORCE_RESTORE == true ]] ; then
        RESTORE_FLAGS=-F
      fi
      if ! ${DATABASE_RESTORE_SCRIPT} ${RESTORE_FLAGS} "${DATABASE_DUMP_FILENAME}" 1>/dev/null; then
        die "Failed to restore database!"
      fi
    else
      die "Failed to execute database restore script at ${DATABASE_RESTORE_SCRIPT}"
    fi
  fi
}

remove_tmp_files() {
  rm -f ${RESTORE_IN_PROGRESS_FILENAME}
  rm -rf ${RESTORE_DIR}
  rm -rf ${TEMP_TAR_DIR}
}

restart_services () {
  echo "RESTARTING REGISTERED SERVICES"
  files=("/etc/xroad/backup.d/"*)
  for ((i=${#files[@]}-1; i>=0; i--)); do
    if  [[ -f ${files[$i]} ]] ; then
      servicename=`basename "${files[$i]}" | sed 's/.*_//'`
      echo ${START_CMD} "${servicename}"
      ${START_CMD} "${servicename}"
    fi
  done
}

while getopts ":RFSt:i:s:n:f:bE" opt ; do
  case ${opt} in
    R)
      SKIP_REMOVAL=true
      ;;
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
    E)
      ENCRYPTED_BACKUP=true
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

trap remove_tmp_files EXIT

acquire_lock "$@"
check_server_type
decrypt_tarball_if_encrypted
check_is_correct_tarball
check_restore_options
make_tarball_label
check_tarball_label
clear_shared_memory
stop_services
create_pre_restore_backup
setup_tmp_restore_dir
extract_to_tmp_restore_dir
remove_old_existing_files
restore_configuration_files
restore_database
restart_services

# vim: ts=2 sw=2 sts=2 et filetype=sh
