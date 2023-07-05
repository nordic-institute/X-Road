#!/bin/bash
# Script for backing up the configuration files and database of the
# X-Road system.
# This script should be invoked by the wrapper script installed at the server
# that is being backed up.

source /usr/share/xroad/scripts/_backup_restore_common.sh

shopt -s nullglob
BACKED_UP_PATHS=(/etc/xroad/ /etc/nginx/conf.d/*xroad*.conf /etc/nginx/sites-enabled/*xroad*)
shopt -u nullglob

THIS_FILE="$(pwd)/$0"

die () {
    echo >&2 "$@"
    exit 1
}

create_database_backup () {
  if [[ $SKIP_DB_BACKUP = true ]] ; then
    echo "SKIPPING DB BACKUP AS REQUESTED"
  else
    if [ -x "${DATABASE_BACKUP_SCRIPT}" ] ; then
      echo "CREATING DATABASE DUMP TO ${DATABASE_DUMP_FILENAME}"
      if ! $DATABASE_BACKUP_SCRIPT "$DATABASE_DUMP_FILENAME"; then
        die "Database backup failed!" \
            "Please check the error messages and fix them before trying again!"
      fi
      BACKED_UP_PATHS+=("${DATABASE_DUMP_FILENAME}")
    else
      die "Failed to execute the database backup script at ${DATABASE_BACKUP_SCRIPT}"
    fi
  fi
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
        --exclude="tmp*.tmp" \
        --exclude="/etc/xroad/services/*.conf" \
        --exclude="/etc/xroad/postgresql" \
        --exclude="/etc/xroad/gpghome"  \
        --exclude="/etc/xroad/xroad.properties" \
        "${BACKED_UP_PATHS[@]}" \
    | gpg --batch --no-tty --homedir /etc/xroad/gpghome --sign --digest-algo SHA256 "${ENCRYPTION_ARGS[@]}" --output "${BACKUP_FILENAME}"

  else
    echo "CREATING TAR ARCHIVE TO ${BACKUP_FILENAME}"
    tar --create -v --label "${TARBALL_LABEL}" --file "${BACKUP_FILENAME}" \
      --exclude="tmp*.tmp" \
      --exclude="/etc/xroad/services/*.conf" \
      --exclude="/etc/xroad/postgresql" \
      --exclude="/etc/xroad/gpghome" \
      --exclude="/etc/xroad/xroad.properties" \
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

ENCRYPT_MODE="none"

while getopts ":t:i:s:n:f:E:k:bS" opt ; do
  case $opt in
    S)
      SKIP_DB_BACKUP=true
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

check_server_type
create_database_backup
make_tarball_label
create_backup_tarball

# vim: ts=2 sw=2 sts=2 et filetype=sh
