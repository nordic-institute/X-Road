#!/bin/bash
# Wrapper script for restoring the configuration of the X-Road central server.
# See $COMMON_RESTORE_SCRIPT for details.

source /usr/share/xroad/scripts/_backup_restore_common.sh

COMMON_RESTORE_SCRIPT=/usr/share/xroad/scripts/_restore_xroad.sh
THIS_FILE=$(pwd)/$0

usage () {
cat << EOF

Usage: $0 -s <security server ID> -f <path of tar archive> [-F] [-P] [-N] [-R]

Restore the configuration (files and database) of the X-Road security server
from a tar archive.

OPTIONS:
    -h Show this message and exit.
    -b Treat all input values as encoded in base64.
    -s ID of the security server. Mandatory if -F is not used.
    -f Absolute path of the tar archive to be used for restoration. Mandatory.
    -F Force restoration, taking only the type of server into account.
    -P Backup archive is in unencrypted TAR format NOT in GPG format (not encrypted nor signed).
    -N Skip GPG signature verification
    -R Skip removal of old files and just copy the backup on top of the existing configuration.
EOF
}

execute_restore () {
  if [ -x ${COMMON_RESTORE_SCRIPT} ] ; then
    local args=(-t security -f "${BACKUP_FILENAME}")
    if [[ -n ${FORCE_RESTORE} ]] && [[ ${FORCE_RESTORE} = true ]] ; then
      args+=(-F)
    else
      args+=(-s "${SECURITY_SERVER_ID}")
      if [[ $USE_BASE_64 = true ]] ; then
        args+=(-b)
      fi
    fi
    if [[ -n ${SKIP_REMOVAL} ]] && [[ ${SKIP_REMOVAL} = true ]] ; then
      args+=(-R)
    fi
    if [[ $PLAIN_BACKUP != true ]] ; then
      args+=(-E)
    fi
    if [[ $SKIP_SIGNATURE_CHECK = true ]] ; then
      args+=(-N)
    fi
    if ! sudo -u root ${COMMON_RESTORE_SCRIPT} "${args[@]}" 2>&1 ; then
      echo "Failed to restore the configuration of the X-Road security server"
      exit 1
    fi
  else
    echo "Could not execute the restore script at ${COMMON_RESTORE_SCRIPT}"
    exit 1
  fi
}

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
      PLAIN_BACKUP=true
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

warn_about_incompatibility

check_user
check_security_server_id
check_backup_file_name
execute_restore

# vim: ts=2 sw=2 sts=2 et filetype=sh
