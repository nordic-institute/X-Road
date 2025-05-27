#!/bin/bash

XROAD_VERSION_LABEL="XROAD_8.0"

SERVERCONF_DATABASE_DUMP_FILENAME="/var/lib/xroad/serverconf_dbdump.dat"
SERVERCONF_DATABASE_BACKUP_SCRIPT="/usr/share/xroad/scripts/containerised/backup_serverconf_db.sh"

OPENBAO_DATABASE_DUMP_FILENAME="/var/lib/xroad/openbao_dbdump.dat"
OPENBAO_DATABASE_BACKUP_SCRIPT="/usr/share/xroad/scripts/containerised/backup_openbao_db.sh"

BACKED_UP_PATHS=()

die() {
  log "$@"
  exit 1
}

create_serverconf_db_backup () {
  if [[ $SKIP_SERVERCONF_DB_BACKUP = true ]] ; then
    echo "SKIPPING DB BACKUP AS REQUESTED"
  else
    if [ -x "${SERVERCONF_DATABASE_BACKUP_SCRIPT}" ] ; then
      echo "CREATING DATABASE DUMP TO ${SERVERCONF_DATABASE_DUMP_FILENAME}"
      if ! $SERVERCONF_DATABASE_BACKUP_SCRIPT "$SERVERCONF_DATABASE_DUMP_FILENAME"; then
        die "Database backup failed!" \
            "Please check the error messages and fix them before trying again!"
      fi
      BACKED_UP_PATHS+=("${SERVERCONF_DATABASE_DUMP_FILENAME}")
    else
      die "Failed to execute the database backup script at ${SERVERCONF_DATABASE_BACKUP_SCRIPT}"
    fi
  fi
}

create_openbao_db_backup () {
  if [[ $SKIP_OPENBAO_BACKUP = true ]] ; then
    echo "SKIPPING OPENBAO BACKUP"
  elif [[ -f "${OPENBAO_DATABASE_BACKUP_SCRIPT}" ]] ; then
    if [ -x "${OPENBAO_DATABASE_BACKUP_SCRIPT}" ] ; then
      echo "CREATING OPENBAO DATABASE DUMP TO ${OPENBAO_DATABASE_DUMP_FILENAME}"
      if ! $OPENBAO_DATABASE_BACKUP_SCRIPT "$OPENBAO_DATABASE_DUMP_FILENAME"; then
        die "OpenBao database backup failed!" \
            "Please check the error messages and fix them before trying again!"
      fi
      BACKED_UP_PATHS+=("${OPENBAO_DATABASE_DUMP_FILENAME}")
    else
      die "Failed to execute OpenBao database backup script at ${OPENBAO_DATABASE_BACKUP_SCRIPT}"
    fi
  fi
}

# XXX The tarball label is simply an underscore-separated list of the input
# parameters.
make_tarball_label () {
  TARBALL_LABEL="security_${XROAD_VERSION_LABEL}_${SECURITY_SERVER_ID}"
}

create_backup_tarball () {
  if [ "$ENCRYPT_MODE" = "encrypt" ] || [ "$ENCRYPT_MODE" = "signonly" ] ; then
    local ENCRYPTION_ARGS=()
    if [ "$ENCRYPT_MODE" = "encrypt" ] ; then
      echo "CREATING ENCRYPTED AND SIGNED TAR ARCHIVE TO ${BACKUP_FILENAME}"
      local PUBCOUNT=0
      local FIRST_RECEIPIENT=${SECURITY_SERVER_ID}
      if [ -z "${FIRST_RECEIPIENT}" ] ; then
        FIRST_RECEIPIENT=${INSTANCE_ID}
      fi
      ENCRYPTION_ARGS=(--encrypt --trust-model direct --cipher-algo AES256 --no-auto-key-locate -r "${FIRST_RECEIPIENT}")
      if [ -n "${GPG_KEYIDS}" ] ; then
        local recipients=()
        IFS=", " read -ra recipients <<< "${GPG_KEYIDS}"
        for keyid in "${recipients[@]}"; do
          (( PUBCOUNT++ )) || true
          ENCRYPTION_ARGS+=(-r "$keyid")
        done
        echo "Encrypting archive with servers public key and $PUBCOUNT extra recipients"
      fi
    else
      echo "CREATING SIGNED TAR ARCHIVE TO ${BACKUP_FILENAME}"
    fi

    tar --create -v --label "${TARBALL_LABEL}" \
        "${BACKED_UP_PATHS[@]}" \
    | gpg --batch --no-tty --homedir /etc/xroad/gpghome --sign --digest-algo SHA256 "${ENCRYPTION_ARGS[@]}" --output "${BACKUP_FILENAME}"

  else
    echo "CREATING TAR ARCHIVE TO ${BACKUP_FILENAME}"
    tar --create -v --label "${TARBALL_LABEL}" --file "${BACKUP_FILENAME}" \
      "${BACKED_UP_PATHS[@]}"
  fi
  if [ $? != 0 ] ; then
    echo "Removing incomplete backup archive"
    rm -v "${BACKUP_FILENAME}"
    die "Creating a backup file to ${BACKUP_FILENAME} failed!" \
        "Please check the error messages and fix them before trying again!"
  fi
  echo "Backup file saved to ${BACKUP_FILENAME}"
}

check_backup_file_name () {
  if [ -z "${BACKUP_FILENAME}" ] ; then
    echo "Missing value of backup tar file name"
    usage
    exit 2
  fi
}

ENCRYPT_MODE="signonly"

if [ -z "$XROAD_OPENBAO_DB_PASSWORD" ]; then
  SKIP_OPENBAO_BACKUP=true
fi

while getopts ":i:s:f:E:k:S" opt ; do
  case $opt in
    S)
      SKIP_SERVERCONF_DB_BACKUP=true
      ;;
    i)
      INSTANCE_ID=$OPTARG
      ;;
    s)
      SECURITY_SERVER_ID=$OPTARG
      ;;
    f)
      BACKUP_FILENAME=$OPTARG
      ;;
    E)
      ENCRYPT_MODE=$OPTARG
      ;;
    k)
      GPG_KEYIDS=$OPTARG
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

check_backup_file_name
create_serverconf_db_backup
create_openbao_db_backup
make_tarball_label
create_backup_tarball
