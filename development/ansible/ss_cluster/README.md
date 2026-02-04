# Security server cluster setup

This ansible playbook configures a master (1) - replica (n) security server cluster. In addition, setting up a load balancer (out of scope) is needed.

The playbook has been tested in AWS EC2 using stock RHEL 7, Ubuntu 20.04 and Ubuntu 22.04 AMIs running default X-Road security server installation. Other environments might require modifications to the playbook.

## Prerequisites

* One security server that acts as primary
* One or more replica security servers.
* The replica server(s) have network access to primary ssh port (tcp/22)
* The replica server(s) have network access to primary serverconf database (default: tcp/5433)
* X-Road security server packages have been installed on each server
    * It is not necessary to configure the servers
    * The primary server configuration is preserved, so it is possible to create a cluster using an existing security server that is already attached to an X-Road instance.
* The control host executing this playbook has ssh access with sudo privileges on all the hosts.
    * Ansible version >2.1 required
    * The control host can be one of the cluster servers (e.g. the primary node), but a separate control host is recommended.
* Decide names for the cluster members and configure the nodes in the ansible inventory. 
    * See hosts/cluster-example.txt for an example (nodename parameter)
    * Node names are related to certificate DN's, see "Set up SSL keys" for specifics
* Change the serverconf_password in group_vars/all and preferably encrypt the file using ansible vault. 
    * The serverconf_password is used to authenticate the local connections to the serverconf database. The default is 'serverconf'.

All the servers in a cluster should have the same operating system (Ubuntu 20.04, Ubuntu 22.04 or RHEL 7). The setup also assumes that the servers are in the same subnet. If that is not the case, one needs to modify primary's pg_hba.nconf so that it accepts replication configurations from the correct network(s).

## Set up SSL keys certificates for PostgreSQL replication connections

Create a CA certificate and store it in PEM format as ca.crt in the "ca" folder. Create TLS key and certificate (PEM) signed by the CA for each node and store those as ca/"nodename"/server.key and ca/"nodename"/server.crt. The server keys must not have a passphrase, but one can and should use ansible-vault to protect
the keys.

Note that the common name (CN) part of the certificate subject's DN must be the *nodename* defined in the host inventory file.

The ca directory contains two scripts that can be used to generate the keys and certificates.
* init.sh creates a CA key and self-signed certificate.
* add-node.sh creates a key and a certificate signed by the CA.

## Running the playbook

Remember to back up the servers before proceeding.

```
ansible-playbook --ask-vault-pass -i hosts/example.txt xroad_ss_cluster.yml
```
If testing the setup in a lxd container:
```
ansible-playbook --ask-vault-pass -c lxd --become-method=su -i hosts/example.txt xroad_ss_cluster.yml
```

The playbook does the following operations
* sets up a separate serverconf database on the primary hosts and configures it
  for streaming replication
* sets up a separate serverconf hot-standby database on the replica hosts
* configures the security servers to use the serverconf database
* creates ssh keys for the xroad user on the replica hosts
* creates an user account (xroad-slave) on the primary host and allows ssh access from replicas using public key authentication
* installs upstart/systemd tasks on the replicas that replicates /etc/xroad from the primary to replicas (using rsync over ssh)
* installs /etc/xroad/conf.d/node.ini file and sets replica or primary mode on each node
* restarts xroad securityserver
