#!/bin/bash

XROAD_VERSION_LABEL="XROAD_8.0"

RESTORE_LOCK_FILENAME="/var/lib/xroad/restore_lock"
RESTORE_IN_PROGRESS_FILENAME="/var/lib/xroad/restore_in_progress"

PRE_RESTORE_TARBALL_FILENAME="/var/lib/xroad/conf_prerestore_backup.tar"

SERVERCONF_DATABASE_DUMP_FILENAME="/var/lib/xroad/serverconf_dbdump.dat"
SERVERCONF_DATABASE_BACKUP_SCRIPT="/usr/share/xroad/scripts/containerised/backup_serverconf_db.sh"
SERVERCONF_DATABASE_RESTORE_SCRIPT="/usr/share/xroad/scripts/containerised/restore_serverconf_db.sh"

OPENBAO_DATABASE_DUMP_FILENAME="/var/lib/xroad/openbao_dbdump.dat"
OPENBAO_DATABASE_BACKUP_SCRIPT="/usr/share/xroad/scripts/containerised/backup_openbao_db.sh"
OPENBAO_DATABASE_RESTORE_SCRIPT="/usr/share/xroad/scripts/containerised/restore_openbao_db.sh"

RESTORE_DIR=/var/tmp/xroad/restore
TEMP_GPG_DIR=/var/tmp/xroad/gpgtmp
TEMP_TAR_FILE=${TEMP_GPG_DIR}/decrypted_temporary.tar
TEMP_GPG_STATUS=${TEMP_GPG_DIR}/gpg_status.tar


ENCRYPTED_BACKUP=true

declare -a deployed_services

die () {
  echo >&2 "$@"
  exit 1
}

# copy/paste from _backup_restore_common.sh
check_security_server_id () {
  if [[ -z ${FORCE_RESTORE} && -z "${SECURITY_SERVER_ID}" ]] ; then
    echo "Missing value of security server ID"
    usage
    exit 2
  fi
  if [[ $USE_BASE_64 = true ]] ; then
    SECURITY_SERVER_ID=$(echo "$SECURITY_SERVER_ID" | base64 --decode)
  fi
}

check_backup_file_name () {
  if [ -z "${BACKUP_FILENAME}" ] ; then
    echo "Missing value of backup tar file name"
    usage
    exit 2
  fi
  if [[ $USE_BASE_64 = true ]] ; then
    BACKUP_FILENAME=$(echo "$BACKUP_FILENAME" | base64 --decode)
  fi
}

# copy/paste from _restore_xroad.sh
check_is_correct_tarball () {
  if ! tar tf "${BACKUP_FILENAME}" > /dev/null; then
    die "Invalid tar archive in ${BACKUP_FILENAME}. Aborting restore!"
  fi
}

# copy/paste from _restore_xroad.sh
acquire_lock () {
    if [ "${FLOCKER}" != "$0" ] ; then
      exec env FLOCKER="$0" flock -n $RESTORE_LOCK_FILENAME "$0" "$@"
    fi
    touch "${RESTORE_IN_PROGRESS_FILENAME}"
}

# copy/paste from _restore_xroad.sh
check_restore_options () {
  if ! tar tf "$BACKUP_FILENAME" var/lib/xroad/serverconf_dbdump.dat &>/dev/null; then
    echo "The backup archive does not contain database dump. Skipping database restore."
    SKIP_DB_RESTORE=true
  fi

  if ! tar tf "$BACKUP_FILENAME" var/lib/xroad/openbao_dbdump.dat &>/dev/null; then
    echo "The backup archive does not contain OpenBao database dump or configuration. Skipping OpenBao restore."
    SKIP_OPENBAO_RESTORE=true
  fi
}

# copy/paste from _restore_xroad.sh
decrypt_tarball_if_encrypted () {
  if [[ $ENCRYPTED_BACKUP = true ]] ; then
    rm  -f ${TEMP_TAR_FILE}
    mkdir -p ${TEMP_GPG_DIR}
    GPG_FILENAME=${BACKUP_FILENAME}
    BACKUP_FILENAME=${TEMP_TAR_FILE}
    if [[ $SKIP_SIGNATURE_CHECK = true ]] ; then
      VERIFYARG=("--skip-verify")
    else
      VERIFYARG=("--status-file" "${TEMP_GPG_STATUS}")
    fi

    echo "Exctracting encrypted tarball to ${BACKUP_FILENAME}"
    # gpg --decrypt can also handle files that are only signed!
    if ! gpg --batch --no-tty --homedir /etc/xroad/gpghome --decrypt --output "${BACKUP_FILENAME}" "${VERIFYARG[@]}" "${GPG_FILENAME}" ; then
      die "Decrypting backup archive failed"
    fi
    # GPG happily decrypts encrypted files without signature and there is no way to force errors when file is not signed
    # so we have to parse gpg output to make sure that signature was indeed verified
    if [[ $SKIP_SIGNATURE_CHECK != true ]] ; then
      while IFS=' ' read -r prefix status lineend
      do
        # see gnupg/doc/DETAILS
        if [[ $status = GOODSIG ]] ; then
          SIGNATURE_VERIFY_SUCCESS=true
        fi
      done < $TEMP_GPG_STATUS
      if [[ $SIGNATURE_VERIFY_SUCCESS != true ]] ; then
        die "Could not verify archive signature"
      fi
    fi
  fi
}

# from _backup_restore_common.sh, modified
make_tarball_label () {
  TARBALL_LABEL="security_${XROAD_VERSION_LABEL}_${SECURITY_SERVER_ID}"
}

create_pre_restore_backup () {
  echo "CREATING PRE-RESTORE BACKUP"
  # we will run this through eval to get a multi-line list

  if [ -x "${SERVERCONF_DATABASE_BACKUP_SCRIPT}" ] ; then
    echo "Creating database dump to ${SERVERCONF_DATABASE_DUMP_FILENAME}"
    if ! ${SERVERCONF_DATABASE_BACKUP_SCRIPT} "${SERVERCONF_DATABASE_DUMP_FILENAME}" ; then
      # allow force restore even when schema does not exist
      if [[ $FORCE_RESTORE == true ]] ; then
        echo "Ignoring pre restore db backup errors"
      else
        die "Error occured while creating pre-restore serverconf database backup" \
            "to ${SERVERCONF_DATABASE_DUMP_FILENAME}"
      fi
    fi
    CONF_FILE_LIST=("${SERVERCONF_DATABASE_DUMP_FILENAME}")
  else
    die "Failed to execute database backup script at ${SERVERCONF_DATABASE_BACKUP_SCRIPT} for" \
        "doing pre-restore backup"
  fi

  if [ -z "$XROAD_OPENBAO_DB_PASSWORD" ]; then
    echo "Skipping openbao database backup"
  else
    if [ -x "${OPENBAO_DATABASE_BACKUP_SCRIPT}" ] ; then
      echo "CREATING OPENBAO DATABASE DUMP TO ${OPENBAO_DATABASE_DUMP_FILENAME}"
      if ! $OPENBAO_DATABASE_BACKUP_SCRIPT "$OPENBAO_DATABASE_DUMP_FILENAME"; then
        if [[ $FORCE_RESTORE == true ]] ; then
          echo "Ignoring pre restore db backup errors"
        else
          die "Error occured while creating pre-restore openbao database backup" \
              "to ${OPENBAO_DATABASE_DUMP_FILENAME}"
        fi
      fi
      CONF_FILE_LIST+=("${OPENBAO_DATABASE_DUMP_FILENAME}")
    else
      die "Failed to execute OpenBao database backup script at ${OPENBAO_DATABASE_BACKUP_SCRIPT} for doing pre-restore backup"
    fi
  fi

  echo "Creating pre-restore backup archive to ${PRE_RESTORE_TARBALL_FILENAME}:"
  if ! tar --create -v \
    --label "${TARBALL_LABEL}" --file ${PRE_RESTORE_TARBALL_FILENAME} -T  <(echo "${CONF_FILE_LIST}") ; then
    die "Creating pre-restore backup archive to ${PRE_RESTORE_TARBALL_FILENAME} failed"
  fi
}

# copy/paste from _restore_xroad.sh
setup_tmp_restore_dir() {
  rm -rf ${RESTORE_DIR}
  mkdir -p ${RESTORE_DIR}
}

extract_to_tmp_restore_dir () {
  # dbdump is optional
  if [[ $SKIP_DB_RESTORE != true ]] ; then
    tar xfv ${BACKUP_FILENAME} -C ${RESTORE_DIR} var/lib/xroad/serverconf_dbdump.dat
  fi

  # OpenBao is optional
  if [[ $SKIP_OPENBAO_RESTORE != true ]] ; then
    tar xfv ${BACKUP_FILENAME} -C ${RESTORE_DIR} var/lib/xroad/openbao_dbdump.dat
  fi
}

restore_serverconf_database () {
  if [[ -n ${SKIP_DB_RESTORE} && ${SKIP_DB_RESTORE} = true ]] ; then
    echo "SKIPPING DB RESTORE AS REQUESTED"
  else
    cp -v -a ${Z} ${RESTORE_DIR}/var/lib/xroad/serverconf_dbdump.dat -t /var/lib/xroad/

    if [[ -x ${SERVERCONF_DATABASE_RESTORE_SCRIPT} && -e ${SERVERCONF_DATABASE_DUMP_FILENAME} ]] ; then
      echo "RESTORING DATABASE FROM ${SERVERCONF_DATABASE_DUMP_FILENAME}"
      if [[ $FORCE_RESTORE == true ]] ; then
        RESTORE_FLAGS=-F
      fi
      if ! ${SERVERCONF_DATABASE_RESTORE_SCRIPT} ${RESTORE_FLAGS} "${SERVERCONF_DATABASE_DUMP_FILENAME}"; then
        die "Failed to restore database!"
      fi
    else
      die "Failed to execute database restore script at ${SERVERCONF_DATABASE_RESTORE_SCRIPT}"
    fi
  fi
}

restore_openbao_database () {
  if [[ -n ${SKIP_OPENBAO_RESTORE} && ${SKIP_OPENBAO_RESTORE} = true ]] ; then
    echo "SKIPPING OPENBAO RESTORE AS REQUESTED"
  else
    # Restore database
    cp -v -a ${Z} ${RESTORE_DIR}/var/lib/xroad/openbao_dbdump.dat -t /var/lib/xroad/
    if [[ -x ${OPENBAO_DATABASE_RESTORE_SCRIPT} && -e ${OPENBAO_DATABASE_DUMP_FILENAME} ]] ; then
      echo "RESTORING OPENBAO DATABASE FROM ${OPENBAO_DATABASE_DUMP_FILENAME}"
      if [[ $FORCE_RESTORE == true ]] ; then
        RESTORE_FLAGS=-F
      fi
      if ! ${OPENBAO_DATABASE_RESTORE_SCRIPT} ${RESTORE_FLAGS} "${OPENBAO_DATABASE_DUMP_FILENAME}" 1>/dev/null; then
        die "Failed to restore OpenBao database!"
      fi
    else
      die "Failed to execute database restore script at ${OPENBAO_DATABASE_RESTORE_SCRIPT}"
    fi
  fi
}

stop_services () {
  if [ -n "$KUBERNETES_SERVICE_HOST" ] || [ -f /var/run/secrets/kubernetes.io/serviceaccount/token ]; then
    echo "Stopping services in Kubernetes environment"
    while read -r deploy; do
      name=$(basename "$deploy")
      # do not stop backup-manager and proxy-ui-api services
      # todo: do not skip signer after migrating data to DB
      if [[ "$name" == "backup-manager" || "$name" == "proxy-ui-api" || "$name" == "signer " ]]; then
        echo "Skipping $name"
        continue
      fi

      replicas=$(kubectl get "$deploy" -o jsonpath='{.spec.replicas}')
      deployed_services+=("$deploy $replicas")
      kubectl scale "$deploy" --replicas=0
    done < <(kubectl get deploy -o name)
  else
    echo "Skipping stop services, not in Kubernetes environment"
  fi
}

restart_services () {
  echo "Triggering restart of services"
  if [ -n "$KUBERNETES_SERVICE_HOST" ] || [ -f /var/run/secrets/kubernetes.io/serviceaccount/token ]; then
    echo "Starting services in Kubernetes environment"
    for entry in "${deployed_services[@]}"; do
      read -r deploy replicas <<< "$entry"
      kubectl scale "$deploy" --replicas="$replicas"
    done
  else
    echo "Skip starting up services, not in Kubernetes environment"
  fi
}

remove_tmp_files() {
  rm -f "${RESTORE_IN_PROGRESS_FILENAME}"
  rm -rf "${RESTORE_DIR}"
  rm -rf "${TEMP_GPG_DIR}"
}

trap remove_tmp_files EXIT

Z=""
if cp --help | grep -q "\-Z"; then
  Z="-Z"
fi


while getopts ":RFs:f:bhPN" opt ; do
  case $opt in
    h)
      usage
      exit 0
      ;;
    R)
      SKIP_REMOVAL=true
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
    P)
      ENCRYPTED_BACKUP=false
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

check_security_server_id
check_backup_file_name
acquire_lock "$@"
decrypt_tarball_if_encrypted
check_is_correct_tarball
check_restore_options
make_tarball_label
stop_services
create_pre_restore_backup
setup_tmp_restore_dir
extract_to_tmp_restore_dir
restore_serverconf_database
restore_openbao_database
restart_services
