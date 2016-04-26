#!/bin/bash
# Script for creating a database cluster for the X-Road central server.

DIR=/etc/xroad/cluster
LOGDIR=/var/log/xroad
LOGFILE=$LOGDIR/cluster_`date +%Y-%m-%d_%H:%M:%S`.log

SSHKEYFILE=hacluster.sshkey
SSH_OPTIONS="-i $SSHKEYFILE -q -o PasswordAuthentication=no"

NODESFILE=nodes
DUMPFILE=/var/lib/postgresql/9.3_dump.dat

APT_GET_INSTALL="apt-get install -y --force-yes"

REMOTE_SCRIPT_PREFIX="set -x"

POSTGRES_CONF_FILE="/etc/postgresql/9.4/main/postgresql.conf"
POSTGRES_LOG_FILE="/var/log/postgresql/postgresql-9.4-main.log"

# These are the steps for creating a cluster. See the comments of each function
# for details.
create_cluster () {
    usage
    start_log_file
    check_nodes_file
    check_and_generate_ssh_keys
    wait_for_authorized_keys
    test_ssh_connections
    prepare_environment
    check_existing_xroad_installation
    check_existing_postgres_installation
    install_and_configure_ntp
    install_helper_tools
    create_ca_directory
    create_ca
    create_tls_keys
    install_and_configure_postgres
    test_postgres_running
    test_node_connectivity
    configure_bdr_cluster
    configure_bdr_nodes
    check_bdr_status
}

die () {
  echo -e "ERROR $@\nABORTING" >/dev/tty
  echo -e "ERROR $@\nABORTING"
  exit 1
}

output () {
  echo -e "$@" >/dev/tty
  echo -e "$@"
}

usage() {
  if [[ `id -nu` != 'xroad' ]]
  then
    echo -e "\nThis script is intended to be run as xroad user. Try the following command\n"
    echo -e "sudo -i -u xroad $0\n"
    exit 1
  fi
}

# The detailed log of the commands run on the target machines will be saved to $LOGFILE.
# A less detailed log will be written to the console.
start_log_file() {
  test -d $DIR -a -w $DIR || die "Cannot write to working directory $DIR"
  cd $DIR
  touch $LOGFILE || die "Cannot start logfile $LOGFILE"

  exec 1<&-
  exec 2<&-
  exec 1<>$LOGFILE
  exec 2>&1

  output "\nDetailed logs are saved into $LOGFILE\n"
}

# The addresses of the machines that make up the cluster must be specified in
# $DIR/$NODESFILE, one per line. The first one will be used for setting up
# the BDR cluster for other nodes to join.
check_nodes_file() {
  if [[ -f $NODESFILE && -r $NODESFILE ]]
  then
    output "\nThe following machines will be used in the cluster:\n==="
    output "$(cat $NODESFILE)"
    output "===\nPress ENTER to continue, Ctrl-C to interrupt"
    read
  else
    die "Cannot access cluster node file at $DIR/$NODESFILE\n" \
        "ATTENTION! Write node IP addresses, one every line into $DIR/$NODESFILE\n" \
        "ATTENTION! Make sure the file is readable to xroad user"
  fi
}

# The root user must have key-based access to all the machines that make up the
# cluster while this script is being run. A special key pair is generated for this purpose.
check_and_generate_ssh_keys() {
  output "Checking SSH key file $DIR/$SSHKEYFILE ... "

  if [ -f $SSHKEYFILE ]
  then
    output "\nUsing the existing SSH key pair"
  else
    ssh-keygen -N "" -f $SSHKEYFILE
    output "\nGenerated a new SSH keypair"
  fi
}

# The generated public key must be manually copied to all the machines that make up
# the cluster, before the script can continue its work.
wait_for_authorized_keys() {
  output "\nATTENTION! Execute the following command in _ALL_ nodes:\n\n" \
         "sudo  -- sh -c \"mkdir -p /root/.ssh; echo '$(cat $SSHKEYFILE.pub)'" \
         " >> /root/.ssh/authorized_keys2\"\n\n"

  output "ATTENTION! Did you execute the above command in _ALL_ nodes???\n\n"
  output "If ready press ENTER. SSH connection test will follow"
  read
}

# Make sure that root's key-based access to all the machines that make up the
# cluster, works.
test_ssh_connections() {
  while read -u10 node
  do
    local command="ssh ${SSH_OPTIONS} root@${node} exit"
    output "\n$node: Testing SSH connection"
    output "Testing: $command"
    `$command`
    if [[ $? -ne 0 ]]
      then die "\nCannot access root@$node:" \
          "make sure the key in $SSHKEYFILE.pub has been copied over"
    else
      output "$node: SSH connection is OK"
    fi
  done 10<$NODESFILE
}

# Each machine must have an environment that facilitates the installation of the
# cluster as well as noise-free logging of the output of this script.
# A proper en_US.UTF-8 is a prerequisite for pg_createcluster to succeed.
prepare_environment() {
  while read -u10 node
  do
    output "\n$node: Ensuring the locale is UTF-8"
    # Create .hushlogin to avoid login-related noise in the log.
    ssh $SSH_OPTIONS root@$node <<EOF
$REMOTE_SCRIPT_PREFIX
touch /root/.hushlogin
grep -E "LC_ALL.*UTF-8" "/etc/environment" || echo LC_ALL=en_US.UTF-8 >> /etc/environment
EOF
  done 10<$NODESFILE
}

# If we detect any X-Road packages on nodes other than the first
# one, we won't continue. Only the first node can be migrated to an HA system if
# an existing Central server is found.
# All the rest of the machines must be cleaned up manually.
check_existing_xroad_installation() {
  local node_index=0

  while read -u10 node
  do
    output "\n$node: Looking for existing X-Road packages"
    ssh $SSH_OPTIONS root@$node <<EOF
$REMOTE_SCRIPT_PREFIX
dpkg -l | grep xroad | grep -v clusterhelper
EOF
    if [[ $? -eq 0 ]]
    then
      if [[ $node_index -gt 0 ]]
      then
        die "$node: Existing X-Road packages were found on a node that" \
            "is not the first one in $DIR/$NODESFILE." \
            "Purge all the X-Road and PostgreSQL packages!"
      else
        # Check if the first node is a central server.
        ssh $SSH_OPTIONS root@$node <<EOF
$REMOTE_SCRIPT_PREFIX
dpkg -l | grep 'xroad-center' | grep -v clusterhelper
EOF
        if [[ $? -eq 0 ]]
        then
          output "$node: Existing X-Road Central server packages were found on the" \
            "first node. The existing system will be migrated to HA"
        else
          die "$node: Existing X-Road packages were found on the first node." \
            "Only a central server can be migrated to HA. Purge all the X-Road and" \
            "PostgreSQL packages!"
        fi
      fi
    else
      output "$node: No X-Road packages were found"
    fi
    ((node_index++))
  done 10<$NODESFILE
}

# If we detect any PostgreSQL clusters or packages on nodes other than the first one, we
# won't continue. The machines must be cleaned up manually.
# The conditions for a PostgreSQL installation that will be migrated:
# * 9.3 packages are found
# * pg_lsclusters reports an existing, online 9.3 cluster
# * the centerui user is found
# The exit status of this function conforms to the general exit status logic of bash.
_check_existing_database() {
  local this_node=$1
  output "\n$this_node: Looking for existing PostgreSQL 9.3 packages"
  ssh $SSH_OPTIONS root@$this_node <<EOF
$REMOTE_SCRIPT_PREFIX
dpkg -l | cut -d ' ' -f 3 | grep postgresql-9.3
EOF
  if [[ $? -eq 0 ]]
  then
    local pg_cluster_info
    pg_cluster_info=$(ssh $SSH_OPTIONS root@$this_node pg_lsclusters -h)
    if [[ $? -eq 0 ]]
    then
      # Sample output:
      # 9.3 main 5432 online postgres /var/lib/postgresql/9.3/main /var/log/postgresql/postgresql-9.3-main.log
      local pg_version=`echo $pg_cluster_info | cut -d ' ' -f 1`
      local pg_port=`echo $pg_cluster_info | cut -d ' ' -f 3`
      local pg_status=`echo $pg_cluster_info | cut -d ' ' -f 4`
      if [[ $pg_version == "9.3" ]] && [[ $pg_port == "5432" ]] && \
          [[ $pg_status == "online" ]]
      then
        ssh $SSH_OPTIONS root@$this_node <<EOF
$REMOTE_SCRIPT_PREFIX
sudo -i -u postgres -- psql -tAc "SELECT 1 FROM pg_roles WHERE rolname='centerui'" | grep 1
EOF
        if [[ $? -eq 0 ]]
        then
          return 0 # Success, an existing running database is there.
        fi
      fi # end pg cluster info check
    fi # end pg cluster info available
  fi # end postgresql 9.3 installed
  return 1 # Failure, no existing running database was found.
}

check_existing_postgres_installation() {
  local node_index=0

  while read -u10 node
  do
    _check_existing_database $node
    if [[ $? -eq 0 ]] # an existing database was found
    then
      if [[ $node_index > 0 ]]
      then
        die "$node: Found an existing X-Road Central server database on a node that" \
            "is not the first one in $DIR/$NODESFILE." \
            "Stop all PostgreSQL processes then purge all the X-Road and PostgreSQL packages!"
      else
        output "$node: Found an existing X-Road Central server database on the first node." \
             "The existing database will be migrated to HA"
      fi
    else # no running database was found
      # If no proper running database was found, all the rest of the PostgreSQL
      # packages should be removed.
      ssh $SSH_OPTIONS root@$node <<EOF
$REMOTE_SCRIPT_PREFIX
dpkg -l | cut -d ' ' -f 3 | grep postgresql-9.3
EOF
      if [[ $? -eq 0 ]]
      then
        die "$node: Found PostgreSQL packages installed on the system." \
          "Purge all the X-Road and PostgreSQL packages!"
      else
        output "$node: No PostgreSQL 9.3 packages were found"
      fi
    fi
    ((node_index++))
  done 10<$NODESFILE
}

# NTP is required for correct functioning of the BDR cluster.
install_and_configure_ntp() {
  while read -u10 node
  do
    output "\n$node: Installing NTP and forcing NTP time update"
    ssh $SSH_OPTIONS root@$node <<EOF
$REMOTE_SCRIPT_PREFIX
$APT_GET_INSTALL ntp
service ntp stop
EOF
    sleep 2
    ssh $SSH_OPTIONS root@$node -- ntpd -gq
    if [[ $? -ne 0 ]]
    then
      die "$node: Cannot update time on"
    else
     output "$node: Time was updated successfully"
    fi
    ssh $SSH_OPTIONS root@$node service ntp start
  done 10<$NODESFILE
}

install_helper_tools() {
  while read -u10 node
  do
    output "\n$node: Installing helper tools"
    ssh $SSH_OPTIONS root@$node <<EOF
$REMOTE_SCRIPT_PREFIX
$APT_GET_INSTALL crudini wget netcat
EOF
    if [[ $? -ne 0 ]]
    then
      # Interrupting the script while apt is working may make it necessary
      # to run 'dpkg --configure' -a manually on the node.
      die "$node: Failed to install helper tools"
    else
      output "$node: helper tools were installed successfully"
    fi
  done 10<$NODESFILE
}

# A CA is needed for creating keys for secure communication of the database nodes.
create_ca_directory() {
  if [[ -d ca ]]
  then
    output "\nUsing the existing CA directory"
  else
    output "\nCreating a new CA directory"
    mkdir ca
  fi
}

create_ca() {
  cd ca

  if [[ -f root.key ]]
  then
    output "\nUsing the existing CA keys"
  else
    output "\nCreating a new CA"
    openssl req -new -x509 -days 7300 -$NODESFILE -out root.crt -keyout root.key -subj '/O=HACluster/CN=CA'
  fi
}

create_tls_keys() {
  while read -u10 node
  do
    if [[ -d $node ]]
    then
      output "\nDirectory of TLS keys for node $node seems to exist, not creating it again"
    else
      output "\nCreating TLS keys for node $node"
      mkdir $node
      openssl req -new -$NODESFILE -days 7300  -keyout $node/server.key -out $node/server.csr -subj "/O=HACluster/CN=$node"
      openssl x509 -req -CAcreateserial -days 7300 -in $node/server.csr -CA root.crt -CAkey root.key -out $node/server.crt
      openssl req -new -$NODESFILE -days 7300  -keyout $node/replicator.key -out $node/replicator.csr -subj "/O=HACluster/OU=$node/CN=replicator"
      openssl x509 -req -CAcreateserial -days 7300 -in $node/replicator.csr -CA root.crt -CAkey root.key -out $node/replicator.crt
    fi
  done 10<../$NODESFILE

  cd ..
}

_install_postgres() {
  local this_node=$1

  output "\n$node: Installing PostgreSQL 9.4 with BDR support"
  ssh $SSH_OPTIONS root@$this_node <<EOF
$REMOTE_SCRIPT_PREFIX
echo "deb http://packages.2ndquadrant.com/bdr/apt/ trusty-2ndquadrant main" > /etc/apt/sources.list.d/bdr.list
echo "deb http://apt.postgresql.org/pub/repos/apt/ trusty-pgdg main" >> /etc/apt/sources.list.d/bdr.list
wget --quiet -O - http://packages.2ndquadrant.com/bdr/apt/AA7A6805.asc | apt-key add -
wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | sudo apt-key add -
apt-get update
$APT_GET_INSTALL postgresql-bdr-9.4-bdr-plugin
EOF

  if [[ $? -ne 0 ]]
  then
    die "$node: Failed to install PostgreSQL. Check the detailed log"
  fi

  # TODO: pg_lscluster kontroll: versioon, port, staatus (online vs down)
}

_copy_tls_keys_and_certs() {
  local this_node=$1
  output "\n$this_node: Copying keys and certificates"
  ssh $SSH_OPTIONS root@$this_node mkdir -p /etc/postgresql/ssl/
  scp $SSH_OPTIONS ca/$this_node/* ca/root.crt root@$this_node:/etc/postgresql/ssl/
  ssh $SSH_OPTIONS root@$this_node  <<EOF
$REMOTE_SCRIPT_PREFIX
chown postgres.postgres -R "/etc/postgresql/ssl"
chmod  og-rwx  /etc/postgresql/ssl/*key
EOF
}

_edit_postgres_conf() {
  local this_node=$1
  output "\n$this_node: Editing PostgreSQL configuration for BDR support"
  ssh $SSH_OPTIONS root@$this_node << EOF
$REMOTE_SCRIPT_PREFIX
crudini --set $POSTGRES_CONF_FILE '' ssl true
crudini --set $POSTGRES_CONF_FILE '' listen_addresses \'*\'
crudini --set $POSTGRES_CONF_FILE '' shared_preload_libraries \'bdr\'
crudini --set $POSTGRES_CONF_FILE '' wal_level  \'logical\'
crudini --set $POSTGRES_CONF_FILE '' track_commit_timestamp  on
crudini --set $POSTGRES_CONF_FILE '' max_connections 100
crudini --set $POSTGRES_CONF_FILE '' max_wal_senders 10
crudini --set $POSTGRES_CONF_FILE '' max_replication_slots 10
crudini --set $POSTGRES_CONF_FILE '' max_worker_processes 10
crudini --set $POSTGRES_CONF_FILE '' ssl_cert_file \'/etc/postgresql/ssl/server.crt\'
crudini --set $POSTGRES_CONF_FILE '' ssl_key_file \'/etc/postgresql/ssl/server.key\'
crudini --set $POSTGRES_CONF_FILE '' ssl_ca_file \'/etc/postgresql/ssl/root.crt\'
crudini --set $POSTGRES_CONF_FILE '' default_sequenceam \'bdr\'
service postgresql restart 9.4
EOF
}

install_and_configure_postgres() {
  local configured_node_count=0

  while read -u10 node
  do
    output "\n$node: Checking if BDR has been configured already"
    ssh $SSH_OPTIONS root@$node << EOF
$REMOTE_SCRIPT_PREFIX
crudini --get $POSTGRES_CONF_FILE "" shared_preload_libraries | grep "bdr"
EOF
    if [[ $? -eq 0 ]]
    then
      output "$node has BDR configured, skipping installation"
      ((configured_node_count++))
      continue
    fi

    _install_postgres $node
    _copy_tls_keys_and_certs $node

    output "\n$node: Checking PostgreSQL configuration"
    if ssh $SSH_OPTIONS root@$node [[ ! -f $POSTGRES_CONF_FILE ]]
    then
      die "$node: PostgreSQL configuration file was not found at $POSTGRES_CONF_FILE" \
          "after installation"
    fi

    ssh $SSH_OPTIONS root@$node <<EOF
$REMOTE_SCRIPT_PREFIX
crudini --get /etc/postgresql/9.4/main/postgresql.conf "" port | grep "^5432"
EOF

    if [[ $? -ne 0 ]]
    then
      if [[ $configured_node_count == 0 ]]
      then
        # The expected port was not found in the configuration of Postgres 9.4.
        # Because this is the first node in the list, we'll try to back up the old
        # database in order to restore the data later -- we are migrating an
        # existing central server to an HA setup.
        output "$node: Wrong port configured for postgres. Old X-Road configuration?" \
            "Trying to dump old database to $DUMPFILE"
        ssh $SSH_OPTIONS root@$node sudo chown postgres:postgres /var/lib/postgresql
        ssh $SSH_OPTIONS root@$node sudo -i -u postgres \
            pg_dump -c -F p -f $DUMPFILE centerui_production
        ssh $SSH_OPTIONS root@$node -- head -2 $DUMPFILE | grep "PostgreSQL database dump"

        if [[ $? -ne 0 ]]
        then
          die "$node: Failed to dump old database"
        fi
        output "$node: Reconfiguring the old DB to listen on port 5433 and the new DB on 5432"
        ssh $SSH_OPTIONS root@$node <<EOF
service xroad-jetty stop
crudini --set  /etc/postgresql/9.4/main/postgresql.conf '' port 5432
crudini --set  /etc/postgresql/9.3/main/postgresql.conf '' port 5433
service postgresql stop 9.3
EOF

      else
        # This is not the first node in the list. Not going to backup and restore the
        # database if there are any.
        # Old installations should be purged.
        die "$node: There are problems with PostgreSQL configuration.\n" \
            "PostgreSQL not running on port 5432?!?\n"
      fi
   else
     output "\n$node: PostgreSQL is configured to port 5432. Good."
   fi

   _edit_postgres_conf $node
    ((configured_node_count++))
  done 10<$NODESFILE
}

test_postgres_running() {
  while read -u10 node
  do
    output "\n$node: Checking if PostgreSQL is actually running"
    ssh $SSH_OPTIONS root@$node <<EOF
$REMOTE_SCRIPT_PREFIX
sudo -i -u postgres -- psql -tAc "SELECT * FROM pg_roles"
EOF
    if [[ $? -ne 0 ]]
    then
      die "\n$node: PostgreSQL is not running. Check the detailed log"
    fi
  done 10<$NODESFILE
}

test_node_connectivity() {
  while read -u10 node
  do
    output "\n$node: Testing TCP connectivity"
    while read -u11 peer
    do
      ssh $SSH_OPTIONS root@$node nc -z -w5 $peer 5432
      if [[ $? -ne 0 ]]
      then
        die "$node: Cannot connect to $peer port 5432"
      else
        output "$node: Connection to $peer port 5432 OK"
     fi
    done 11<$NODESFILE
  done 10<$NODESFILE
}

configure_bdr_cluster() {
  while read -u10 node
  do
    output "\n$node: Configuring PostgreSQL cluster for HA"
    scp  $SSH_OPTIONS $NODESFILE root@$node:/etc/postgresql/9.4/main
    ssh  $SSH_OPTIONS root@$node <<EOF
$REMOTE_SCRIPT_PREFIX
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
    output "\n$node: Creating databases and roles"
    ssh $SSH_OPTIONS root@$node <<EOF
$REMOTE_SCRIPT_PREFIX
sudo -i -u postgres -- psql -tAc "SELECT 1 FROM pg_roles WHERE rolname='centerui'" | grep 1
EOF
    if [[ $? -eq 0 ]]
    then
      output "$node: The centerui schema is already present"
    else
      output "$node: Creating centerui and other schemas"
      ssh  $SSH_OPTIONS root@$node <<EOF
$REMOTE_SCRIPT_PREFIX
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
}

configure_bdr_nodes() {
  local current_node_id=0

  while read -u10 node
  do
    output "\n$node: Configuring BDR node, using node ID 'node_$current_node_id'"

    ssh  $SSH_OPTIONS root@$node <<EOF
$REMOTE_SCRIPT_PREFIX
sudo -i -u postgres -- psql centerui_production -tAc "SELECT  bdr.bdr_get_local_node_name()"  | grep "^node_"
EOF

    if [[ $? -eq 0 ]]
    then
      output "$node: The BDR node is already configured, skipping"
      if [[ $current_node_id == 0 ]]
      then
        # Put aside the name of the first node. This node will be used when other nodes
        # join the cluster.
        local node0=$node
      fi
      ((current_node_id++))
      continue
    fi

    if [[ $current_node_id == 0 ]]
    then
      # Create the BDR group that other nodes will join later.
      output "\n$node: Creating a BDR group for the cluster"
      ssh $SSH_OPTIONS root@$node <<EOF
$REMOTE_SCRIPT_PREFIX
sudo -i -u  postgres psql centerui_production <<EOP
SELECT bdr.bdr_group_create(
  local_node_name := 'node_$current_node_id',
  node_external_dsn := 'dbname=centerui_production user=replicator host=$node sslmode=verify-full sslcert=/etc/postgresql/ssl/replicator.crt sslkey=/etc/postgresql/ssl/replicator.key sslrootcert=/etc/postgresql/ssl/root.crt ');
EOP
EOF
      # Put aside the name of the first node. This node will be used when other nodes
      # join the cluster.
      local node0=$node

      output "\n$node: Creating a view for accessing BDR-related information"
      ssh  $SSH_OPTIONS root@$node <<EOF
$REMOTE_SCRIPT_PREFIX
crudini --set $POSTGRES_CONF_FILE '' bdr.skip_ddl_locking true
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

      ssh $SSH_OPTIONS root@$node <<EOF
$REMOTE_SCRIPT_PREFIX
head -2 $DUMPFILE | grep "PostgreSQL database dump"
EOF
      if [[ $? -eq 0 ]]
      then
        output "$node: Trying to restore OLD configuration database"
        ssh $SSH_OPTIONS root@$node <<EOF
cp $DUMPFILE $DUMPFILE.bak
sed -i -e "0,/^SELECT pg_catalog.setval/{s/\(^SELECT pg_catalog.setval\)/select pg_sleep(30);\n\1/}" $DUMPFILE
sed -i -e "{s/\(^SELECT pg_catalog.setval('\(.*\)', \([0-9]*\).*$\)/-- \1\nselect nextval('\2') from generate_series(1,\3);/}"  $DUMPFILE
sudo -i -u  postgres  -- sh -c "PGPASSWORD=centerui psql -h 127.0.0.1 -U centerui -e centerui_production < $DUMPFILE"
EOF
      fi
      ssh $SSH_OPTIONS root@$node <<EOF
mv $DUMPFILE $DUMPFILE.`date +%Y%m%d%H%M%S`.done
crudini --set $POSTGRES_CONF_FILE '' bdr.skip_ddl_locking false
service postgresql restart 9.4
service xroad-jetty restart
sleep 5
EOF

    else
      # We are dealing with some other node than the first one. Join the cluster.
      ssh $SSH_OPTIONS root@$node <<EOF
sudo -i -u  postgres psql centerui_production <<EOP
SELECT bdr.bdr_group_join(
  local_node_name := 'node_$current_node_id',
  node_external_dsn := 'dbname=centerui_production user=replicator host=$node sslmode=verify-full sslcert=/etc/postgresql/ssl/replicator.crt sslkey=/etc/postgresql/ssl/replicator.key sslrootcert=/etc/postgresql/ssl/root.crt ',
   join_using_dsn := 'dbname=centerui_production user=replicator host=${node0} sslmode=verify-full sslcert=/etc/postgresql/ssl/replicator.crt sslkey=/etc/postgresql/ssl/replicator.key sslrootcert=/etc/postgresql/ssl/root.crt ');
EOP
EOF
    fi
    ((current_node_id++))
  done 10<$NODESFILE
}

check_bdr_status() {
  output "\nGoing to check BDR status from all the nodes in 5 seconds"
  sleep 5

  while read -u10 node
  do
    output "\n$node: BDR node status:"
    local result=$(ssh $SSH_OPTIONS root@$node sudo -i -u postgres -- "psql -tAc \"SELECT 'Local node: '||bdr.bdr_get_local_node_name();\" centerui_production")
    output "$result; status of all the nodes as seen from $node:"
    result=$(ssh $SSH_OPTIONS root@$node sudo -i -u postgres -- "psql -tAc \"SELECT get_xroad_bdr_replication_info();\" centerui_production")
    output "$result"
  done 10<$NODESFILE
}

create_cluster

output "\nAll the steps have been completed. Date: `date -R`"
output "\nPlease continue with installing/upgrading X-Road center software\n"


# vim: ts=2 sw=2 sts=2 et filetype=sh
