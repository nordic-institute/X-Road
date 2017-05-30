#!/bin/bash

die () {
    echo >&2 "$@"
    exit 1
}

has_command () {
    command -v $1 &>/dev/null
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

if has_command initctl
then
    LIST_CMD="initctl list | grep -E  '^xroad-|^xtee55-' | cut -f 1 -d ' '"
    STOP_CMD="initctl stop"
    START_CMD="initctl start"
elif has_command systemctl
then
    LIST_CMD="systemctl --plain -qt service list-units | grep -E 'xroad-.*.service\s' | sed 's/^\s*//' | cut -d' ' -f1"
    STOP_CMD="systemctl stop"
    START_CMD="systemctl start"
else
    die "Cannot control X-Road services (initctl/systemctl not found). Aborting restore"
fi

echo "STOPING ALL SERVICES EXCEPT JETTY"

SERVICES=$(eval $LIST_CMD | grep -v -- -jetty)

for xrdservice in $SERVICES; do  $STOP_CMD $xrdservice  ;done

##

listf="`find /etc/xroad/ -type f` `find /etc/nginx/ -name *xroad*`"

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

echo -e "\n-----\n RESTORING CONFIGURATION FROM ${filename}\nRestoring files:\n"
RESTOREDIR=/var/tmp/xroad/restore
rm -rf $RESTOREDIR
mkdir -p $RESTOREDIR
# Restore to temporary directory and fix permissions before copying
tar xfv ${filename} -C $RESTOREDIR etc/xroad etc/nginx || die "Extracting backup failed"
# dbdump is optional
tar xfv ${filename} -C $RESTOREDIR var/lib/xroad/dbdump.dat
# keep existing db.properties
if [ -f /etc/xroad/db.properties ]
then
    mv $RESTOREDIR/etc/xroad/db.properties $RESTOREDIR/etc/xroad/db.properties.restored
    cp /etc/xroad/db.properties $RESTOREDIR/etc/xroad/db.properties
fi
chown -R xroad:xroad $RESTOREDIR/*

# remove old configuration files
rm ${listf}

# restore files
Z=""
if cp --help | grep -q "\-Z"; then
Z="-Z"
fi

cp -a $Z $RESTOREDIR/etc/xroad -t /etc
cp -r $Z $RESTOREDIR/etc/nginx -t /etc
cp -a $Z $RESTOREDIR/var/lib/xroad -t /var/lib
rm -rf $RESTOREDIR

if [ -x /usr/share/xroad/scripts/restore_db.sh ] &&  [ -e /var/lib/xroad/dbdump.dat ]
then
  echo -e "\nRESTORING DATABASE FROM /var/lib/xroad/dbdump.dat\n"
  /usr/share/xroad/scripts/restore_db.sh 1>/dev/null
  if [ "x$?" != "x0" ]
   then
    die "Failed to restore database!"
  fi
fi

echo -e "\nRESTARTING SERVICES\n"

for xrdservice in $SERVICES; do $START_CMD $xrdservice ;done

