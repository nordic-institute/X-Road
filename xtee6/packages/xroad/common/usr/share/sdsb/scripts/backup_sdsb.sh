#!/bin/sh

die () {
    echo >&2 "$@"
    exit 1
}


if [ "$(id -nu )" != "sdsb" ] 
then
 die "This script must run under sdsb user "
fi

[ "$#" -eq 1 ] || die "1 argument required, $# provided"


list="/etc/sdsb/ /etc/nginx/sites-enabled/"
filename=$1

echo "CREATING BACKUP TO ${filename}\n"

if [ -x /usr/share/sdsb/scripts/backup_db.sh ]
then
  echo "Creating database dump to /var/lib/sdsb/dbdump.dat\n"
  /usr/share/sdsb/scripts/backup_db.sh
    if [ "x$?" != "x0" ]
  then
    die "Database backup failed! Please check messages and fix them before trying again!"
  fi
  list="${list} /var/lib/sdsb/dbdump.dat"
fi


echo "Backing up following files:\n"

tar cfv ${filename} ${list}

if [ "x$?" != "x0" ]
then
 die "Creating ${filename} backup encountered an error! Please check messages and fix them before trying again!"
fi

