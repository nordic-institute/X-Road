#!/bin/sh

die () {
    echo >&2 "$@"
    exit 1
}


if [ "$(id -nu )" != "xroad" ]
then
 die "This script must run under xroad user "
fi

[ "$#" -eq 1 ] || die "1 argument required, $# provided"


list="/etc/xroad/ /etc/nginx/conf.d/*xroad*.conf"
if [ -d "/etc/nginx/sites-enabled" ]
then
    list="$list /etc/nginx/sites-enabled/*xroad*"
fi

filename=$1

echo "CREATING BACKUP TO ${filename}\n"

if [ -x /usr/share/xroad/scripts/backup_db.sh ]
then
  echo "Creating database dump to /var/lib/xroad/dbdump.dat\n"
  /usr/share/xroad/scripts/backup_db.sh
    if [ "x$?" != "x0" ]
  then
    die "Database backup failed! Please check messages and fix them before trying again!"
  fi
  list="${list} /var/lib/xroad/dbdump.dat"
fi


echo "Backing up following files:\n"

tar --create -v --label "XROAD_6.6" --file ${filename} ${list}

if [ "x$?" != "x0" ]
then
 die "Creating ${filename} backup encountered an error! Please check messages and fix them before trying again!"
fi

