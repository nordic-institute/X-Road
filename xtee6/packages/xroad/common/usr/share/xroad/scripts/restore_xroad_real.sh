#!/bin/bash

die () {
    echo >&2 "$@"
    exit 1
}


if [ "$(id -nu )" != "root" ]
then
 die "ABORTED. This script must run under root user "
fi

[ "$#" -eq 1 ] || die "1 argument required, $# provided. Give path to backup file"

filename=$1

tar tf ${filename} > /dev/null

if [ "x$?" != "x0" ]
then
 die "${filename} is broken. Aborting restore!"
fi

tar --test-label --file ${filename} "XROAD_6.6"

if [ "x$?" != "x0" ]
then
 die "${filename} is not compatible backup file. Aborting restore!"
fi


echo "STOP ALL SERVICES EXCEPT JETTY"

SERVICES=$(initctl list | grep -E  "^xroad-|^xtee55-" | grep -v -- -jetty | cut -f 1 -d " "  )

for xrdservice in $SERVICES; do  initctl stop $xrdservice  ;done

##

listf="`find /etc/xroad/ -type f` /etc/nginx/sites-enabled/*"

echo "CREATING PRE-RESTORE BACKUP"

if [ -x /usr/share/xroad/scripts/backup_db.sh ]
then
  echo "Creating database dump to /var/lib/xroad/dbdump.dat"
  /usr/share/xroad/scripts/backup_db.sh
  if [ "x$?" != "x0" ]
   then
    die "Error occured while creating database backup"
  fi
  listf="${listf} /var/lib/xroad/dbdump.dat"
fi


echo -e "Backing up following files to  /var/lib/xroad/conf_prerestore_backup.tar \n ${listf}"
tar cf /var/lib/xroad/conf_prerestore_backup.tar ${listf}
rm ${listf}

echo -e "\n-----\n RESTORING CONFIGURATION FROM ${filename}\nRestoring files:\n"
tar xfv ${filename} -C /

if [ -x /usr/share/xroad/scripts/restore_db.sh ] && [ -e /var/lib/xroad/dbdump.dat ]
then
  echo -e "\nRESTORING DATABASE FROM /var/lib/xroad/dbdump.dat\n"
  /usr/share/xroad/scripts/restore_db.sh 1>/dev/null
  if [ "x$?" != "x0" ]
  then
    die "Failed to restore database!"
  fi
fi

echo -e "\nRESTARTING SERVICES\n"

for xrdservice in $SERVICES; do initctl start $xrdservice ;done


# v5.5 specific stuff
if [ -f /usr/xtee/etc/v6_xroad_installed ] && [ -f /usr/xtee/etc/v6_xroad_activated ]; then
  echo -e "\nEXPORTING INTERNAL TLS KEY AND CERTIFICATE TO 5.0 X-ROAD PROXY.."
  su - ui -c /usr/share/xroad/scripts/export_v6_internal_tls_key.sh

  if [ "x$?" != "x0" ]; then
	die "Failed to export internal TLS key and certificate to 5.0 X-Road proxy!"
  fi
fi

