#!/bin/bash
# Script for modifying a database cluster for the X-Road central server.

DIR=/etc/xroad/cluster
LOGDIR=/var/log/xroad
LOGFILE=$LOGDIR/cluster_`date +%Y-%m-%d_%H:%M:%S`.log

SSHKEYFILE=$DIR/hacluster.sshkey
SSH_OPTIONS="-i $SSHKEYFILE -q -o PasswordAuthentication=no"

NODESFILE=nodes
NODESNEWFILE=nodes.new

REMOTE_SCRIPT_PREFIX="set -x"

POSTGRES_CONF_FILE="/etc/postgresql/9.4/main/postgresql.conf"
POSTGRES_LOG_FILE="/var/log/postgresql/postgresql-9.4-main.log"

# These are the steps for modifying a cluster. See the comments of each function
# for details.
create_cluster () {

    usage
    start_log_file
    check_nodes_file
    check_nodesnew_file
    replace_nodes_file
#    check_and_generate_ssh_keys
    test_ssh_connections
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


check_nodesnew_file() {
  if [[ -f $NODESNEWFILE && -r $NODESNEWFILE ]]
  then
    output "\nThe following machines will be used in the cluster:\n==="
    output "$(cat $NODESNEWFILE)"
    output "===\nPress ENTER to continue, Ctrl-C to interrupt"
    read
  else
    die "Cannot access cluster node file at $DIR/$NODESNEWFILE\n" \
        "ATTENTION! Write replacable IP addresses,\none pair separated by space (oldIP newIP) every line into $DIR/$NODESNEWFILE\n" \
        "ATTENTION! Make sure the file is readable to xroad user"
  fi
}

#
replace_nodes_file() {

  while read -u11 command
  do
    IFS=" " read old new <<< $command
    output replace $old $new
    output "sed -i 's/$old/$new/' $NODESFILE"
    sed -i -e "s#$old#$new#" $NODESFILE
  done 11<$NODESNEWFILE

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

create_tls_keys() {
  cd $DIR/ca
  while read -u10 node
  do
    if [[ -d $node ]]
    then
      output "\nDirectory of TLS keys for node $node seems to exist, not creating it again"
    else
      output "\nCreating TLS keys for node $node"
      mkdir $node
      openssl req -new -nodes -days 7300  -keyout $node/server.key -out $node/server.csr -subj "/O=HACluster/CN=$node"
      openssl x509 -req -CAcreateserial  -days 7300 -in $node/server.csr -CA root.crt -CAkey root.key -out $node/server.crt
      openssl req -new -nodes -keyout $node/replicator.key -out $node/replicator.csr -subj "/O=HACluster/OU=$node/CN=replicator"
      openssl x509 -req -CAcreateserial -days 7300 -in $node/replicator.csr -CA root.crt -CAkey root.key -out $node/replicator.crt
      _copy_tls_keys_and_certs $node
    fi
  done 10<../$NODESFILE

  cd $DIR
}

_copy_tls_keys_and_certs() {
  local this_node=$1
  output "\n$this_node: Copying keys and certificates"
  ssh $SSH_OPTIONS root@$this_node mkdir -p /etc/postgresql/ssl/
  scp $SSH_OPTIONS $this_node/* ca/root.crt root@$this_node:/etc/postgresql/ssl/
  ssh $SSH_OPTIONS root@$this_node  <<EOF
$REMOTE_SCRIPT_PREFIX
chown postgres.postgres -R "/etc/postgresql/ssl"
chmod  og-rwx  /etc/postgresql/ssl/*key
EOF
    if [[ $? -ne 0 ]]
    then
      die "\n$this_node: Key copy unsuccessful. Check the detailed log"
    fi
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
    scp  $SSH_OPTIONS $NODESNEWFILE root@$node:/etc/postgresql/9.4/main
    while read -u20 peer
    do
      IFS=" " read old new <<< $peer
      ssh  $SSH_OPTIONS root@$node <<EOF
      $REMOTE_SCRIPT_PREFIX
       sed -i -e "s#$old#$new#" /etc/postgresql/9.4/main/pg_hba.conf
      service postgresql restart 9.4
EOF
    if [[ $? -ne 0 ]]
    then
      die "\n$node: PostgreSQL configuration change rejected. Check the detailed log"
    fi
    done 20</etc/postgresql/9.4/main/$NODESNEWFILE
  done 10<$NODESFILE

  sleep 5

}

configure_bdr_nodes() {

  while read -u10 node
  do
    output "\n$node: Configuring BDR node "

while read -u20 peer
do
 IFS=" " read old new <<< $peer
  sed -i -e "s#$old#$new#" /etc/postgresql/9.4/main/pg_hba.conf

  ssh $SSH_OPTIONS root@$node <<EOF
    $REMOTE_SCRIPT_PREFIX
    sudo -i -u  postgres psql centerui_production <<EOP
    update bdr.bdr_nodes set node_local_dsn=replace(node_local_dsn,'host=$old','host=$new');
    update bdr.bdr_connections set conn_dsn=replace(conn_dsn,'host=$old','host=$new');
EOP
EOF
    if [[ $? -ne 0 ]]
    then
      die "\n$node: BDR reconfiguration failed. Check the detailed log"
    fi

done 20<$NODESNEWFILE
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

# vim: ts=2 sw=2 sts=2 et filetype=sh
