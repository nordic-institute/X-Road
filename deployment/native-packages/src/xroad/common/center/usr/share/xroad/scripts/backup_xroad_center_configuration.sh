#!/bin/bash
# Wrapper script for backing up the configuration of the X-Road central server.
# See $COMMON_BACKUP_SCRIPT for details.

source /usr/share/xroad/scripts/_backup_restore_common.sh

THIS_FILE=$(pwd)/$0

usage () {
cat << EOF

Usage: $0 -i <instance ID> [-n <HA node name>] -f <path of tar archive> [-S]

Backup the configuration (files and database) of the X-Road central server to a tar archive.

OPTIONS:
    -h Show this message and exit.
    -b Treat all input values as encoded in base64.
    -i Instance ID of the installation of X-Road.
    -n Node name of the central server if deployed in HA setup.
    -f Absolute path of the resulting tar archive.
    -S Skip database backup
EOF
}

read_backup_encryption_settings () {
  # shellcheck source=/dev/null
  source /usr/share/xroad/scripts/_read_cs_db_properties.sh
  prepare_db_props

  if [ -f /etc/xroad/db_libpq.env ]; then
    # shellcheck source=/dev/null
    source /etc/xroad/db_libpq.env
  fi

  export PGPASSWORD="$db_password"
  export PGOPTIONS="-c client-min-messages=warning -c search_path=${db_schema},public ${PGOPTIONS_EXTRA:-}"

  local psql_q=(psql -v ON_ERROR_STOP=1 -qAt -h "${PGHOST:-$db_host}" -p "${PGPORT:-$db_port}" -U "$db_user" -d "$db_database")

  ENCRYPT_BACKUP=$("${psql_q[@]}" -v k="xroad.backups.backup-encryption-enabled" \
    -c "SELECT property_value FROM configuration_properties WHERE property_key = :'k';")
  GPG_KEYIDS=$("${psql_q[@]}" -v k="xroad.backups.backup-encryption-keyids" \
    -c "SELECT property_value FROM configuration_properties WHERE property_key = :'k';")
}

execute_backup () {
  if [ -x ${COMMON_BACKUP_SCRIPT} ] ; then
    local args="-t central -i ${INSTANCE_ID} -f ${BACKUP_FILENAME}"
    if [[ $USE_BASE_64 = true ]] ; then
      args="${args} -b"
    fi
    if [ -n "${CENTRAL_SERVER_HA_NODE_NAME}" ] ; then
      args="${args} -n ${CENTRAL_SERVER_HA_NODE_NAME}"
    fi
    if [[ $SKIP_DB_BACKUP = true ]] ; then
      args="${args} -S"
    fi
    if [[ $ENCRYPT_BACKUP = true ]] ; then
      args="${args} -E encrypt"
    else
      args="${args} -E signonly"
    fi
    if [ -n "$GPG_KEYIDS" ]; then
      args="${args} -k ${GPG_KEYIDS}"
    fi
    ${COMMON_BACKUP_SCRIPT} ${args}
    if [ $? -ne 0 ] ; then
      echo "Failed to back up the configuration of the X-Road central server"
      exit 1
    fi
  else
    echo "Could not execute the backup script at ${COMMON_BACKUP_SCRIPT}"
    exit 1
  fi
}

while getopts ":i:n:f:Sbh" opt ; do
  case $opt in
    h)
      usage
      exit 0
      ;;
    S)
      SKIP_DB_BACKUP=true
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

check_user
check_instance_id
check_central_ha_node_name
check_backup_file_name

read_backup_encryption_settings
ENCRYPT_BACKUP=${ENCRYPT_BACKUP:-false}
echo "ENCRYPT_BACKUP=$ENCRYPT_BACKUP"
echo "GPG_KEYIDS=$GPG_KEYIDS"

execute_backup

# vim: ts=2 sw=2 sts=2 et filetype=sh
