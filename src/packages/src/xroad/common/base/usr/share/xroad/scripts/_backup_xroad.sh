#!/bin/bash
# Script for backing up the configuration files and database of the
# X-Road system.
# This script should be invoked by the wrapper script installed at the server
# that is being backed up.

source /usr/share/xroad/scripts/_backup_restore_common.sh

doesFirstFileExist(){
    test -e "$1"
}

BACKED_UP_PATHS="/etc/xroad/"
# only backup /etc/nginx/conf.d/*xroad*.conf and /etc/nginx/sites-enabled/*xroad* if such files exist
# this is to adapt to both SS and CS backups, when SS does not have nginx configuration
if doesFirstFileExist /etc/nginx/conf.d/*xroad*.conf
then
    BACKED_UP_PATHS="$BACKED_UP_PATHS /etc/nginx/conf.d/*xroad*.conf"
fi

if doesFirstFileExist /etc/nginx/sites-enabled/*xroad*
then
    BACKED_UP_PATHS="$BACKED_UP_PATHS /etc/nginx/sites-enabled/*xroad*"
fi

THIS_FILE=$(pwd)/$0

die () {
    echo >&2 "$@"
    exit 1
}

create_database_backup () {
  if [[ $SKIP_DB_BACKUP = true ]] ; then
    echo "SKIPPING DB BACKUP AS REQUESTED"
  else
    if [ -x ${DATABASE_BACKUP_SCRIPT} ] ; then
      echo "CREATING DATABASE DUMP TO ${DATABASE_DUMP_FILENAME}"
      if ! $DATABASE_BACKUP_SCRIPT "$DATABASE_DUMP_FILENAME"; then
        die "Database backup failed!" \
            "Please check the error messages and fix them before trying again!"
      fi
      BACKED_UP_PATHS="${BACKED_UP_PATHS} ${DATABASE_DUMP_FILENAME}"
    else
      die "Failed to execute the database backup script at ${DATABASE_BACKUP_SCRIPT}"
    fi
  fi
}

create_backup_tarball () {
  if [[ $ENCRYPT_BACKUP = true ]] ; then
    echo "CREATING ENCRYPTED TAR ARCHIVE TO ${BACKUP_FILENAME}"
    tar --create -v --label "${TARBALL_LABEL}" \
        --exclude="tmp*.tmp" --exclude="/etc/xroad/postgresql" --exclude="/etc/xroad/backupkeys/gpghome" \
        ${BACKED_UP_PATHS} \
    | gpg --homedir /etc/xroad/backupkeys/gpghome --encrypt --sign --recipient backup@xroad --output ${BACKUP_FILENAME}

  else
    echo "CREATING TAR ARCHIVE TO ${BACKUP_FILENAME}"
    tar --create -v --label "${TARBALL_LABEL}" --file ${BACKUP_FILENAME} --exclude="tmp*.tmp" --exclude="/etc/xroad/postgresql" ${BACKED_UP_PATHS}
  fi
  if [ $? != 0 ] ; then
    echo "Removing incomplete backup archive"
    rm -v ${BACKUP_FILENAME}
    die "Creating a backup file to ${BACKUP_FILENAME} failed!" \
        "Please check the error messages and fix them before trying again!"
  fi
  echo "Backup file saved to ${BACKUP_FILENAME}"
}

# TODO this should be in setup somewhere, also this function is not robust enough
generate_private_key_if_needed () {
  if [[ $ENCRYPT_BACKUP = true ]] ; then
    if [ ! -d "/etc/xroad/backupkeys/gpghome" ] ; then
      echo "GENERATING NEW KEYPAIR"
      mkdir -p /etc/xroad/backupkeys/gpghome
      chmod 700 /etc/xroad/backupkeys/gpghome

      gpg --homedir /etc/xroad/backupkeys/gpghome --batch --gen-key <<EOF
Key-Type: 1
Key-Length: 4096
Name-Real: XRoad Backup
Name-Email: backup@xroad
Expire-Date: 0
%no-protection
EOF

    fi
  fi
}

while getopts ":t:i:s:n:f:bSE" opt ; do
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
      ENCRYPT_BACKUP=true
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
generate_private_key_if_needed
create_backup_tarball

# vim: ts=2 sw=2 sts=2 et filetype=sh
