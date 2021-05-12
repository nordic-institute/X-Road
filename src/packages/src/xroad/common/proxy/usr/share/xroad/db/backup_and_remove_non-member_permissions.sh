#!/bin/bash
filename="/var/lib/xroad/conf_backup_`date +%Y%m%d-%H%M%S`_presubsystem.tar"
instance=`cat /etc/xroad/globalconf/instance-identifier`
hostname=`hostname -f`

if [[ "$instance" == "" ]]
then
    #Special case, upgrading an unconfigured system. Skip backup.
    echo -e "No instance identifier found, skipping backup"
else
    echo -e "Creating backup before removing non-subsystem accessrights related objects.\nBackup can be found: $filename"
    su - xroad -c sh -c "/usr/share/xroad/scripts/backup_xroad_proxy_configuration.sh -s \"${instance}\" -f \"${filename}\""

    if [[ $? -ne 0 ]]
    then
        echo -e "Backup failed\n"
    exit 1
    fi
fi

echo -e "Cleaning non-subsystem relations\n"

PW=$(crudini --get /etc/xroad/db.properties '' serverconf.hibernate.connection.password)
USER=$(crudini --get /etc/xroad/db.properties '' serverconf.hibernate.connection.username)
su - postgres -c "PGPASSWORD=${PW:-serverconf} psql -h 0 serverconf ${USER:-serverconf}" <<EOF
begin;
delete from groupmember where localgroup_id in (select localgroup.id from localgroup join client on localgroup.client_id=client.id join identifier on client.identifier=identifier.id where identifier.type='MEMBER');
delete from groupmember where groupmemberid in (select groupmember.groupmemberid from groupmember join identifier on groupmember.groupmemberid=identifier.id where identifier.type='MEMBER');
delete from localgroup where id in (select localgroup.id from localgroup join client on localgroup.client_id=client.id join identifier on client.identifier=identifier.id where identifier.type='MEMBER');

delete from certificate where id in (select certificate.id from certificate join client on certificate.client_id=client.id join identifier on client.identifier=identifier.id where identifier.type='MEMBER');

delete from accessright where id in ( select accessright.id from accessright join client on accessright.client_id=client.id join identifier on client.identifier=identifier.id where identifier.type='MEMBER');
delete from accessright where id in ( select accessright.id from accessright join identifier on accessright.subjectid=identifier.id where identifier.type='MEMBER');

delete from service where servicedescription_id in (select servicedescription.id from servicedescription join client on servicedescription.client_id=client.id join identifier on client.identifier=identifier.id where identifier.type='MEMBER');
delete from servicedescription where id in (select servicedescription.id from servicedescription join client on servicedescription.client_id=client.id join identifier on client.identifier=identifier.id where identifier.type='MEMBER');
delete from identifier where identifier.type='MEMBER' and id not in (select client.identifier from client);
commit;
EOF

if [[ $? -ne 0 ]]
then
  echo -e "Cleaning DB failed"
  exit 1
fi

echo -e "Done."

