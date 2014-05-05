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

filename=$1

listf="`find /etc/sdsb/ -type f` /etc/nginx/sites-enabled/*"

echo "backing up old stuff and removing them ${listf}"
tar cf ${filename}.old-`date +%Y%m%d%H%M%S` ${listf}
rm ${listf}

echo "restoring files from backup ${filename}"
tar xfv ${filename} -C /

if [ -x /usr/share/sdsb/scripts/restore_db.sh ] &&  [ -e /var/lib/sdsb/dbdump.dat ]
then
  /usr/share/sdsb/scripts/restore_db.sh
fi


#force signer restart as file changed
kill `ps axfww | grep signer.jar | grep -v grep | awk '{print $1}'`

