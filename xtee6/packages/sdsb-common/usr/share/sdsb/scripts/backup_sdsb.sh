#!/bin/sh

die () {
    echo >&2 "$@"
    exit 1
}


if [ "$(id -nu )" != "sdsb" ] 
then
 die "this script must run under sdsb user "
fi

[ "$#" -eq 1 ] || die "1 argument required, $# provided"


list="/etc/sdsb/ /etc/nginx/sites-enabled/"

if [ -x /usr/share/sdsb/scripts/backup_db.sh ]
then
  /usr/share/sdsb/scripts/backup_db.sh
  list="${list} /var/lib/sdsb/dbdump.dat"
fi


filename=$1

tar cfv ${filename} ${list}
