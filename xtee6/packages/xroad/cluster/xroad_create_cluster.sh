#!/bin/bash


if [[ `id -nu` != 'xroad' ]]
then
  echo -e "\nThis script is itended to be run as xroad user. Try following command\n"
  echo -e "sudo -i -u xroad $0\n"
  exit 1
fi


DIR=/etc/xroad/cluster
LOGDIR=/var/log/xroad/
LOGFILE=$LOGDIR/cluster_`date +%Y-%m-%d_%H:%M:%S`.log


SSHKEYFILE=hacluster.sshkey
SSH_OPTIONS="-i $SSHKEYFILE -q -o PasswordAuthentication=no"

NODESFILE=nodes
DUMPFILE=/var/lib/postgresql/9.3_dump`date +%Y%m%d%H%M%S`.dat

die () {
    echo -e "ERROR $@\nABORTING" >/dev/tty
    echo -e "ERROR $@\nABORTING"
    exit 1
}


output () {
    echo -e "$@" >/dev/tty
    echo -e "$@"
}


test -d  $DIR -a -w $DIR  || die "cannot write to working directory $DIR"
cd $DIR
touch $LOGFILE || die "cannot start logfile $LOGFILE"

exec 1<&-
exec 2<&-
exec 1<>$LOGFILE
exec 2>&1


output "\nDetailed logs are saved into $LOGFILE\n"

test -f $NODESFILE || die "no cluster node file at $DIR/$NODESFILE\n Write node IP addresses one every line into file"

output "current nodes file contents:\n==="
output "$(cat $NODESFILE)"

output "===\nEnter to continue, Ctrl-C to interrupt"
read


output "testing SSH key file $SSHKEYFILE ... "

test -f $SSHKEYFILE ||  ssh-keygen -N "" -f $SSHKEYFILE && output "generated new SSH keypair"

output "execute following command in _ALL_ nodes\n\nsudo  -- sh -c \"mkdir -p /root/.ssh; echo '$(cat $SSHKEYFILE.pub)' >> /root/.ssh/authorized_keys2\"\n\n"

output "If ready press ENTER. SSH connection test will follow"
read 

while read -u10 node
do 
output "testing ssh connecion to $node"
ssh $SSH_OPTIONS root@$node <<EOF
grep -q -E "LC_ALL.*UTF-8" "/etc/environment" || echo LC_ALL=en_US.UTF-8 >> /etc/environment
EOF
if [[ $? -ne 0 ]] 
 then die "cannot access root@$node. check ssh keys"
else
 output " $node OK"
fi
done 10<$NODESFILE


ouptut "all nodes are accessible via SSH"


while read -u10 node
do
output "Installing NTP on $node, forcing NTP time update"
ssh $SSH_OPTIONS root@$node <<EOF
apt-get install ntp
service ntp stop
EOF
ssh $SSH_OPTIONS root@$node -- ntpd -gq
if [[ $? -ne 0 ]]
 then die "Cannot update time"
else
 output "$node time OK"
fi
ssh $SSH_OPTIONS root@$node service ntp start
done 10<$NODESFILE



if [[ -d ca ]] 
then 
  output "CA directory exists"
else
  output "creating CA directory"
  mkdir ca
fi

cd ca

if [[ -f root.key ]]
then
  output "using existing CA keys" 
else
  output "creating new CA"
  openssl req -new -x509 -days 7300 -$NODESFILE -out root.crt -keyout root.key -subj '/O=HACluster/CN=CA' 
fi

while read -u10 node
do
  if [[ -d $node ]]
  then
    output "TLS keys directory for node $node seems to be exist, not creating new"
  else
    output "create TLS keys for $node"
    mkdir $node
    openssl req -new -$NODESFILE -days 7300  -keyout $node/server.key -out $node/server.csr -subj "/O=HACluster/CN=$node"
    openssl x509 -req -CAcreateserial -in $node/server.csr -CA root.crt -CAkey root.key -out $node/server.crt 
    openssl req -new -$NODESFILE -days 7300  -keyout $node/replicator.key -out $node/replicator.csr -subj "/O=HACluster/OU=$node/CN=replicator"
    openssl x509 -req -CAcreateserial -in $node/replicator.csr -CA root.crt -CAkey root.key -out $node/replicator.crt 
  fi
done 10<../$NODESFILE

cd ..

output "start installing clustered Postgres"
count=0
while read -u10 node
do

  ssh $SSH_OPTIONS root@$node -- crudini --get /etc/postgresql/9.4/main/postgresql.conf "" shared_preload_libraries | grep -q "bdr"
  if [[ $? -eq 0 ]]; then output "$node has BDR configured, skipping installation"; continue; fi
  output "starting installation for $node"
  ssh $SSH_OPTIONS root@$node   <<EOF
  echo "deb http://packages.2ndquadrant.com/bdr/apt/ trusty-2ndquadrant main" > /etc/apt/sources.list.d/bdr.list
  echo "deb http://apt.postgresql.org/pub/repos/apt/ trusty-pgdg main" >> /etc/apt/sources.list.d/bdr.list 
  wget --quiet -O - http://packages.2ndquadrant.com/bdr/apt/AA7A6805.asc | apt-key add - 
  wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | sudo apt-key add - 
  apt-get update  
  apt-get install -y --force-yes postgresql-bdr-9.4-bdr-plugin crudini netcat
EOF

  output "populate keys and certificates to $node"
  ssh $SSH_OPTIONS root@$node mkdir /etc/postgresql/ssl/
  scp $SSH_OPTIONS ca/$node/* ca/root.crt root@$node:/etc/postgresql/ssl/
  ssh $SSH_OPTIONS root@$node  <<EOF
  chown postgres.postgres -R "/etc/postgresql/ssl"
  chmod  og-rwx  /etc/postgresql/ssl/*key
EOF
 output "checking node $node PostgreSQL configuration"
 ssh $SSH_OPTIONS root@$node << EOF
 crudini --get /etc/postgresql/9.4/main/postgresql.conf "" port | grep -q "^5432"
EOF
 if [[ $? -ne 0 ]]
 then
  if [[ $count == 0 ]]
  then 
   output "$node has wrong port configured for postgres. Old X-Road configuration? Trying to dump old database to $DUMPFILE"
   ssh $SSH_OPTIONS root@$node sudo chown postgres:postgres /var/lib/postgres
   ssh $SSH_OPTIONS root@$node sudo -i -u postgres pg_dump -c -F p -f $DUMPFILE centerui_production
   ssh $SSH_OPTIONS root@$node -- head -2 $DUMPFILE | grep -q "PostgreSQL database dump"
   if [[ $? -ne 0 ]]; then die "$node failed to dump old database."; fi
   output "reconfigure old DB to listen on port 5433 and new on 5432"
   ssh $SSH_OPTIONS root@$node <<EOF
   service xroad-jetty stop
   crudini --set  /etc/postgresql/9.4/main/postgresql.conf '' port 5432
   crudini --set  /etc/postgresql/9.3/main/postgresql.conf '' port 5433
   service postgresql stop 9.3
EOF

  else
   die "$node has some problem with PostgreSQL configuration. Note: old X-Road center must be first on nodes file."
  fi
 else
   output "node $node PostgreSQL is configured to port 5432. Good."
 fi
ssh $SSH_OPTIONS root@$node << EOF
  crudini --set  /etc/postgresql/9.4/main/postgresql.conf '' listen_addresses \'*\'
  crudini --set  /etc/postgresql/9.4/main/postgresql.conf '' shared_preload_libraries \'bdr\'
  crudini --set  /etc/postgresql/9.4/main/postgresql.conf '' wal_level  \'logical\'
  crudini --set  /etc/postgresql/9.4/main/postgresql.conf '' track_commit_timestamp  on
  crudini --set  /etc/postgresql/9.4/main/postgresql.conf '' max_connections 100
  crudini --set  /etc/postgresql/9.4/main/postgresql.conf '' max_wal_senders 10
  crudini --set  /etc/postgresql/9.4/main/postgresql.conf '' max_replication_slots 10
  crudini --set  /etc/postgresql/9.4/main/postgresql.conf '' max_worker_processes 10
  crudini --set  /etc/postgresql/9.4/main/postgresql.conf '' ssl_cert_file \'/etc/postgresql/ssl/server.crt\'
  crudini --set  /etc/postgresql/9.4/main/postgresql.conf '' ssl_key_file \'/etc/postgresql/ssl/server.key\'
  crudini --set  /etc/postgresql/9.4/main/postgresql.conf '' ssl_ca_file \'/etc/postgresql/ssl/root.crt\' 
  crudini --set  /etc/postgresql/9.4/main/postgresql.conf '' default_sequenceam \'bdr\'
  service postgresql restart 9.4
EOF
((count++))
done 10<$NODESFILE

sleep 10

while read -u10 node
do
  output "testing $node TCP connectivity"
  while read -u11 peer
  do
   ssh  $SSH_OPTIONS root@$node nc -z -w5 $peer 5432
   if [[ $? -ne 0 ]]; 
     then die "  $node cannot connect to $peer port 5432"
     else output "  $node connects to $peer port 5432 OK"
   fi
  done 11<$NODESFILE
done 10<$NODESFILE



while read -u10 node
do
  output "configure postgres for $node"
  scp  $SSH_OPTIONS $NODESFILE root@$node:/etc/postgresql/9.4/main
  ssh  $SSH_OPTIONS root@$node   <<EOF
  set -e
   while read -u20 peer
   do
   echo "checking/adding \$peer access"
   grep -q -E "replication.*replicator.*\${peer}" "/etc/postgresql/9.4/main/pg_hba.conf" || echo -e "hostssl\treplication\treplicator\t\${peer}/32\tcert\tclientcert=1" >> /etc/postgresql/9.4/main/pg_hba.conf
   grep -q -E "centerui_production.*replicator.*\${peer}" "/etc/postgresql/9.4/main/pg_hba.conf" || echo -e "hostssl\tcenterui_production\treplicator\t\${peer}/32\tcert\tclientcert=1" >> /etc/postgresql/9.4/main/pg_hba.conf 
  done 20</etc/postgresql/9.4/main/$NODESFILE
service postgresql restart 9.4
EOF
done 10<$NODESFILE

sleep 5

while read -u10 node
do
  output "create databases and roles at $node"
  ssh  $SSH_OPTIONS root@$node <<EOF
  sudo -i -u postgres -- psql -tAc "SELECT 1 FROM pg_roles WHERE rolname='centerui'" | grep -q 1
EOF
  if [[ $? -eq 0 ]]
  then
   output "$node has centerui schema"
  else
   output "$node creating centerui and other schemas"
  ssh  $SSH_OPTIONS root@$node <<EOF
sudo -i -u postgres createuser -s --replication replicator
sudo -i -u  postgres psql <<EOP
CREATE ROLE centerui LOGIN PASSWORD 'centerui';
EOP
sudo -i -u postgres createdb -O centerui -E UTF-8 centerui_production
sudo -i -u postgres psql -d centerui_production <<EOP
CREATE EXTENSION hstore;
CREATE EXTENSION btree_gist;
CREATE EXTENSION bdr; 
GRANT SELECT ON ALL TABLES IN SCHEMA bdr TO centerui;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA bdr TO centerui;
EOP
EOF
fi
done  10<$NODESFILE

c=0
while read -u10 node
do
  output "configure cluster for $node called node_$c."

ssh  $SSH_OPTIONS root@$node <<EOF
sudo -i -u postgres -- psql centerui_production -tAc "SELECT  bdr.bdr_get_local_node_name()"  | grep "^node_"
EOF
if [[ $? -eq 0 ]]
then 
 output "$node is already configured, skipping to next node"
 if [[ $c == 0 ]]; then node0=$node;fi
 ((c++))
 continue
fi

if [[ $c == 0 ]]   
then
ssh  $SSH_OPTIONS root@$node <<EOF
sudo -i -u  postgres psql centerui_production <<EOP
SELECT bdr.bdr_group_create(
local_node_name := 'node_$c',
node_external_dsn := 'dbname=centerui_production user=replicator host=$node sslmode=verify-full sslcert=/etc/postgresql/ssl/replicator.crt sslkey=/etc/postgresql/ssl/replicator.key sslrootcert=/etc/postgresql/ssl/root.crt '); 
EOP
EOF
node0=$node

ssh  $SSH_OPTIONS root@$node <<EOF
crudini --set  /etc/postgresql/9.4/main/postgresql.conf '' bdr.skip_ddl_locking true
service postgresql restart 9.4
sleep 5
sudo -i -u  postgres psql centerui_production <<EOP
create or replace view xroad_bdr_replication_info as
  SELECT  bdr.bdr_nodes.node_name, bdr.bdr_nodes.node_status,  pg_replication_slots.active AS replication_active,  pg_stat_replication.client_addr,   pg_stat_replication.state AS replication_state,  pg_xlog_location_diff(pg_current_xlog_insert_location(), pg_stat_replication.flush_location) AS replication_lag_bytes FROM bdr.bdr_nodes
LEFT JOIN pg_stat_replication  ON pg_stat_replication.application_name LIKE '%' || bdr.bdr_nodes.node_sysid || '%'
LEFT JOIN pg_replication_slots  ON pg_replication_slots.slot_name LIKE '%' || bdr.bdr_nodes.node_sysid || '%' ;
create function get_xroad_bdr_replication_info()  RETURNS SETOF xroad_bdr_replication_info as
  \\$\\$ select * from xroad_bdr_replication_info; \\$\\$
  LANGUAGE sql VOLATILE SECURITY DEFINER;
grant EXECUTE on FUNCTION get_xroad_bdr_replication_info() to centerui;
EOP
EOF

ssh $SSH_OPTIONS root@$node -- head -2 $DUMPFILE | grep -q "PostgreSQL database dump"
if [[ $? -eq 0 ]]
then output "$node trying to restore OLD configuration database"
 ssh $SSH_OPTIONS root@$node <<EOF
cp $DUMPFILE $DUMPFILE.bak
sed -i -e "0,/^SELECT pg_catalog.setval/{s/\(^SELECT pg_catalog.setval\)/select pg_sleep(30);\n\1/}" $DUMPFILE
sed -i -e "{s/\(^SELECT pg_catalog.setval('\(.*\)', \([0-9]*\).*$\)/-- \1\nselect nextval('\2') from generate_series(1,\3);/}"  $DUMPFILE
sudo -i -u  postgres  -- sh -c "PGPASSWORD=centerui psql -h 127.0.0.1 -U centerui -e centerui_production < $DUMPFILE"
EOF
fi
ssh $SSH_OPTIONS root@$node <<EOF
crudini --set  /etc/postgresql/9.4/main/postgresql.conf '' bdr.skip_ddl_locking false
service postgresql restart 9.4
service xroad-jetty restart
sleep 5
EOF

else

ssh  $SSH_OPTIONS root@$node <<EOF
sudo -i -u  postgres psql centerui_production <<EOP
SELECT bdr.bdr_group_join(
  local_node_name := 'node_$c',
  node_external_dsn := 'dbname=centerui_production user=replicator host=$node sslmode=verify-full sslcert=/etc/postgresql/ssl/replicator.crt sslkey=/etc/postgresql/ssl/replicator.key sslrootcert=/etc/postgresql/ssl/root.crt ',
 join_using_dsn := 'dbname=centerui_production user=replicator host=${node0} sslmode=verify-full sslcert=/etc/postgresql/ssl/replicator.crt sslkey=/etc/postgresql/ssl/replicator.key sslrootcert=/etc/postgresql/ssl/root.crt '); 
EOP

EOF
fi

((c++))
done 10<$NODESFILE


  output "checking BDR status from all nodes in 5 seconds"
sleep 5
while read -u10 node
do
  output " $node reports following:"
  output `ssh  $SSH_OPTIONS root@$node sudo -i -u postgres -- "psql -tAc \"select '      Local node: '||bdr.bdr_get_local_node_name();\" centerui_production"`
  output `ssh  $SSH_OPTIONS root@$node sudo -i -u postgres -- "psql -tAc \"select '     Node status: '||get_xroad_bdr_replication_info();\" centerui_production"`
done 10<$NODESFILE


output  "\nAll steps are completed. Date: `date -R` \nConfigured nodes are ready. Continue with installing/upgrading X-Road center software\n"
