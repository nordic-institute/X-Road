# Central Server High Availability Installation Guide <!-- omit in toc -->
**X-ROAD 7**

Version: 1.20
Doc. ID: IG-CSHA

---


## Version history <!-- omit in toc -->

| Date       | Version | Description                                                                                          | Author             |
|------------|---------|------------------------------------------------------------------------------------------------------|--------------------|
| 31.08.2015 | 0.1     | Initial version created.                                                                             |                    |
| 15.09.2015 | 1.0     | Minor fixes done.                                                                                    |                    |
| 20.09.2015 | 1.1     | Editorial changes made                                                                               |                    |
| 16.12.2015 | 1.2     | Added recovery information                                                                           |                    |
| 17.12.2015 | 1.3     | Editorial changes made                                                                               |                    |
| 20.02.2017 | 1.4     | Converted to Github flavoured Markdown, added license text, adjusted tables for better output in PDF | Toomas Mölder      |
| 05.03.2018 | 1.5     | Added terms and abbreviations references and document links                                          | Tatu Repo          |
| 03.10.2018 | 1.6     | Added the chapter "Changing Nodes' IP Addresses in HA Cluster"                                       |                    |
| 19.12.2018 | 1.7     | Minor changes related to Ubuntu 18 support                                                           |                    |
| 03.01.2019 | 1.8     | Removed forced NTP installation.                                                                     | Jarkko Hyöty       |
| 27.09.2019 | 1.9     | Minor fix                                                                                            | Petteri Kivimäki   |
| 03.12.2019 | 1.10    | Removed dependency on BDR                                                                            | Jarkko Hyöty       |
| 30.12.2019 | 1.11    | Add instructions for setting up a replicated PostgreSQL database                                     | Jarkko Hyöty       |
| 18.03.2020 | 1.12    | Add instructions for Central Server HA Migration                                                     | Ilkka Seppälä      |
| 16.04.2020 | 1.13    | Update cluster status output                                                                         | Jarkko Hyöty       |
| 10.08.2021 | 1.14    | Update HA installation instructions. Remove obsolete BDR references.                                 | Ilkka Seppälä      |
| 25.08.2021 | 1.15    | Update X-Road references from version 6 to 7                                                         | Caro Hautamäki     |
| 17.04.2023 | 1.16    | Updated HA installation instructions. Added conf updated for newer postgres versions (>=12).         | Mikk-Erik Bachmann |
| 19.04.2023 | 1.17    | Removed unused properties from db.properties                                                         | Mikk-Erik Bachmann |
| 02.06.2023 | 1.18    | Minor updates                                                                                        | Justas Samuolis    |
| 05.06.2023 | 1.19    | Update HA cluster status endpoint path                                                               | Andres Rosenthal   |
| 28.06.2023 | 1.20    | Update database properties to follow new Spring datasource style                                     | Raido Kaju         |


## Table of Contents <!-- omit in toc -->

<!-- toc -->
<!-- vim-markdown-toc GFM -->

- [License](#license)
- [1 Introduction](#1-introduction)
  - [1.1 High Availability for X-Road Central Server](#11-high-availability-for-x-road-central-server)
  - [1.2 Target Audience](#12-target-audience)
  - [1.3 Terms and abbreviations](#13-terms-and-abbreviations)
  - [1.4 References](#14-references)
- [2 Key Points and Known Limitations for X-Road Central Server HA Deployment](#2-key-points-and-known-limitations-for-x-road-central-server-ha-deployment)
- [3 Requirements and Workflows for HA Configuration](#3-requirements-and-workflows-for-ha-configuration)
  - [3.1 Requirements](#31-requirements)
  - [3.2 Workflow for a New X-Road Instance Setup](#32-workflow-for-a-new-x-road-instance-setup)
  - [3.3 Workflow for Upgrading an Existing X-Road Central Server to an HA Configuration](#33-workflow-for-upgrading-an-existing-x-road-central-server-to-an-ha-configuration)
  - [3.4 Workflow for Adding New Nodes to an Existing HA Configuration](#34-workflow-for-adding-new-nodes-to-an-existing-ha-configuration)
  - [3.5 Post-Configuration Steps](#35-post-configuration-steps)
- [4 General Installation of HA Support](#4-general-installation-of-ha-support)
- [5 Monitoring HA State on a Node](#5-monitoring-ha-state-on-a-node)
- [6 Recovery of the HA cluster](#6-recovery-of-the-ha-cluster)
  - [6.1 Configuration database (and possible replicas) is lost](#61-configuration-database-and-possible-replicas-is-lost)
  - [6.2 One or more cental server nodes lost, backup available](#62-one-or-more-cental-server-nodes-lost-backup-available)
  - [6.3 Some Central Server nodes lost, backup not available](#63-some-central-server-nodes-lost-backup-not-available)
- [Appendix A. Setting up a replicated PostgreSQL database](#appendix-a-setting-up-a-replicated-postgresql-database)
  - [Prerequisites](#prerequisites)
  - [PostgreSQL configuration (all database servers)](#postgresql-configuration-all-database-servers)
  - [Preparing the standby](#preparing-the-standby)
  - [Verifying replication](#verifying-replication)
  - [Configuring Central Servers](#configuring-central-servers)
  - [Fail-over](#fail-over)
    - [Automatic fail-over](#automatic-fail-over)
- [Appendix B. Central Server HA Migration](#appendix-b-central-server-ha-migration)
  - [Migrating from Standalone to HA Cluster](#migrating-from-standalone-to-ha-cluster)

<!-- vim-markdown-toc -->
<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/

## 1 Introduction


### 1.1 High Availability for X-Road Central Server

The High Availability (HA) solution for the X-Road Central Server relies on a shared, optionally highly available database. This enables every node to work as a standalone Central Server which receives configuration updates from all other nodes. Security servers can download identical configuration from any of the public addresses published in a configuration anchor file.

Every Central Server node has its own:
- node specific keys to sign configuration;
- node specific public address which is distributed to the configuration clients in configuration anchor files;
- optionally, a management Security Server.

In addition, a highly-available PostgreSQL compatible database is required for storing the shared configuration. Setting up the database is out of the scope of this document.

The minimum X-Road Central Server version is 6.23.

### 1.2 Target Audience

The intended audience of this installation guide are X-Road Central Server administrators responsible for installing and configuring the X-Road Central Server software.

The document is intended for readers with a good knowledge of Linux server management, computer networks, and the X-Road functioning principles.

### 1.3 Terms and abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\]

### 1.4 References

1.  <a id="Ref_IG-CS" class="anchor"></a>\[IG-CS\] X-Road 7. Central Server Installation Guide. Document ID: [IG-CS](ig-cs_x-road_6_central_server_installation_guide.md).
2.  <a id="Ref_UG-CS" class="anchor"></a>\[UG-CS\] X-Road 7. Central Server User Guide. Document ID: [UG-CS](ug-cs_x-road_6_central_server_user_guide.md).
3.  <a id="Ref_TERMS" class="anchor"></a>\[TA-TERMS\] X-Road Terms and Abbreviations. Document ID: [TA-TERMS](../terms_x-road_docs.md).


## 2 Key Points and Known Limitations for X-Road Central Server HA Deployment

1.  Correct timekeeping is important.

    It is assumed that the clocks of the cluster nodes are synchronized using e.g. NTP. The administrator must configure a time synchronization service (e.g. ntpd, chrony, systemd-timesyncd) and take care to keep the time synced.

2.  Network security and speed.

    Even though all the database connections are secured and authenticated with TLS, network security (especially confidentiality, availability) must be reviewed on infrastructure level. The network speed and reliability between Central Server nodes and the shared database is important for the functionality of the system. A Central Server node can not function if it can not write to and read from the database.

3.  All the nodes must run the same patch-level X-Road software and database software.

4.  Time window for node failure repairing.

    A node can work (i.e. provide valid global configuration to the X-Road instance) as long as it can read from and write to the shared configuration database. If one node loses database access, other nodes continue providing valid global configuration, and Security Servers will switch downloading the configuration from a healthly node. In the case all nodes or the shared database fails, Security Servers can function until global configuration exprires, but new Security Servers can not be added to the X-Road instance.

5.  Configuration files (located in `/etc/xroad/`) are not synchronized between nodes. It is the responsibility of the system administrator to change them in all nodes if required or stated by the user manual.

## 3 Requirements and Workflows for HA Configuration

### 3.1 Requirements

The nodes must meet all the requirements listed in the X-Road Central Server Installation Guide (see \[[IG-CS](#Ref_IG-CS)\]). 
Additionally, creating an HA setup requires that all nodes can access the shared configuration database. A PostgreSQL (version 9.4 or newer) compatible database is required.

### 3.2 Workflow for a New X-Road Instance Setup

1.  Install HA support according to steps listed in section [4](#4-general-installation-of-ha-support).
2.  Install the X-Road Central Server software according to the X-Road Central Server Installation Guide \[[IG-CS](#Ref_IG-CS)\] on each node.

### 3.3 Workflow for Upgrading an Existing X-Road Central Server to an HA Configuration

1.  Upgrade the existing X-Road Central Server software to the latest release available, verify system health.
2.  Create a backup of the system configuration and store it in a safe place.
3.  Continue with HA support installation steps as described in section [4](#4-general-installation-of-ha-support).
4.  Install the X-Road Central Server software as described in the X-Road Central Server Installation Guide \[[IG-CS](#Ref_IG-CS)\] to each of the new nodes.
5.  After installing and configuring all the X-Road Central Server nodes, retrieve new internal and external configuration anchor files from one of the nodes and distribute the files to all Security Servers and configuration proxies.

### 3.4 Workflow for Adding New Nodes to an Existing HA Configuration

Referencing steps in section [4](#4-general-installation-of-ha-support).

1.  Upgrade the software of the existing nodes to the latest version available, verify system health on all nodes.
2.  Create a backup system configuration of the existing nodes and store it in a safe place.
3.  Prepare the node for HA configuration as described in section [4](#4-general-installation-of-ha-support).
4.  Install the X-Road Central Server software as described in the X-Road Central Server Installation Guide \[[IG-CS](#Ref_IG-CS)\] to each of the new nodes.

After installing and configuring all the X-Road Central Server nodes, retrieve new internal end external configuration anchor files from one of the nodes and distribute the files to all Security Servers and configuration proxies.

### 3.5 Post-Configuration Steps

If the remote database supports fail over to a secondary host (e.g. when using PostgreSQL streaming replication with hot-standby), it is possible to define the secondary database hosts in `/etc/xroad/db.properties`. The system will automatically try the secondary hosts in case the primary fails.

```properties
secondary_hosts=<comma separated list of hosts>

# Example: 
# secondary_hosts=standby.example.org,standby2.example.org
```

The secondary hosts are assumed to use the same port (default 5432) as the primary host.

## 4 General Installation of HA Support

The HA support requires that an external database is initialized and available (see [X-Road Central Server Installation Guide](#Ref_IG-CS) about using an external database). 
The database can also be installed on the Central Server node(s), but that is not recommended unless a multi-master setup (e.g. BDR) is used.

In addition, it is necessary to configure a unique node name for each node participating in the cluster preferably before installing the X-Road software.

1.  On each node, edit file `/etc/xroad/conf.d/local.ini`, creating it if necessary
2.  Add the following lines
    ```ini
    [center]
    ha-node-name=<node_name>
    ```
    Where `<node_name>` is the unique name for the HA node. It is suggested to follow a naming scheme where the first node is `node_0` and subsequent ones `node_1`, `node_2`, etc..
    
    When upgrading an existing Central Server to HA configuration, the name of the existing server *must be* `node_0`.

    If upgrading an existing cluster with BDR, the name of the node **must be** the same as the local BDR node name.

3.  Install the X-Road Central Server software according to the X-Road Central Server Installation Guide \[[IG-CS](#Ref_IG-CS)\] on the first node.
    
    When the installation asks for a database host, provide the details of the external database.

4. On the subsequent nodes, first copy the database configuration file /etc/xroad/db.properties from the first node. Then install the Central Server according to the X-Road Central Server Installation Guide \[[IG-CS](#Ref_IG-CS)\].

## 5 Monitoring HA State on a Node

It is possible to get HA status via a web interface, for example using curl:
```bash
curl --header "Authorization: X-Road-ApiKey token=<api key>" -k https://cs1.example.org:4000/api/v1/system/high-availability-cluster/status
```

See [Central Server User Guide](ug-cs_x-road_6_central_server_user_guide.md#32-checking-the-status-of-the-nodes-of-the-cluster) for more information about the API KEY Authorization

Response:
```json
{
  "ha_configured": true,
  "node_name": "node_0",
  "nodes": [
    {
      "node_name": "node_0",
      "node_address": "cs1.example.org",
      "configuration_generated": "2023-06-06T09:48:02.000Z",
      "status": "OK"
    },
    {
      "node_name": "node_1",
      "node_address": "cs2.example.org",
      "configuration_generated": "2023-06-06T09:46:02.000Z",
      "status": "WARN"
    }
  ],
  "all_nodes_ok": false
}
```
The status information is based on the data in the configuration database and other nodes are not directly accessed.
A node can be:
  * "OK" if the configuration is recently generated.
  * "WARN" if the timestamp is more than a global configuration generation interval in the past.
  * "ERROR" if the timestamp is older than the global configuration expriry time.
  * "UNKNOWN" if the node has not been seen at all.

The combined status "all_nodes_ok" is true if the status of all nodes is "OK" and false otherwise.

For a global view of the cluster status, the check should be executed on each node and compared to verify that nodes have a consistent view of the status.

In addition, one should monitor the database status, and if a replication solution is used, the database replication status using the tools provided by the database and/or replication solution. 

## 6 Recovery of the HA cluster

Recovery procedure depends on the type of the failure and database used. If configuration keys or central system addresses are modified during recovery, a new configuration anchor file must be distributed to members. See the Central Server User Guide \[[UG-CS](#Ref_UG-CS)\].

### 6.1 Configuration database (and possible replicas) is lost

* Stop Central Servers.
* Set up a new database.
* Update the db.properties on each Central Server node to point to the new database.
* On one node, restore the database from a backup (see \[[UG-CS](#Ref_UG-CS)\]).
* Start Central Servers.

### 6.2 One or more cental server nodes lost, backup available

* Setup the missing nodes like described in [3.4](#34-workflow-for-adding-new-nodes-to-an-existing-ha-configuration)
* Restore configuration from a backup, skipping database restoration (see \[[UG-CS](#Ref_UG-CS)\]).

### 6.3 Some Central Server nodes lost, backup not available

See [3.4 Workflow for Adding New Nodes to an Existing HA Configuration](#34-workflow-for-adding-new-nodes-to-an-existing-ha-configuration)

## Appendix A. Setting up a replicated PostgreSQL database

In this configuration, the Central Server nodes share an external PostgreSQL database that uses a master - standby configuration with streaming replication. In case of master failure, the Central Server nodes can automatically start using the standby once it has been promoted to master. The promoting is manual by default, but can be automated by custom scripts or using third-party tools. Because the database standby is read-only and the Central Server nodes require write access, this setup does not provide horizontal scalability.

It is assumed that a PostgreSQL database is installed on two or more similar hosts. It is recommended to use a recent PostgeSQL version (10 or later). One of these hosts will be configured as a master, and others will replicate the master database (standby). The master node can have an existing database, but the standby databases will be initialized during the installation.

A thorough discussion about PostgreSQL replication and high-availability is out of the scope of this guide. For more details, see e.g. [PostgreSQL documentation about high-availability](https://www.postgresql.org/docs/current/high-availability.html).

### Prerequisites

* Same version (10 or later) of PostgreSQL installed on at least two similar hosts.
* Network connections on PostgreSQL port (tcp/5432) are allowed between database servers (both directions).
* Network connections to PostgreSQL port (tcp/5432) are allowed from the Central Servers to the database servers.

### PostgreSQL configuration (all database servers)

Edit `postgresql.conf` and verify the following settings:
* Ubuntu: `/etc/postgresql/<version>/<cluster name>/postgresql.conf`
* RHEL: In data directory, usually `/usr/lib/pgsql/data`

```properties
# more secure password encryption (optional but recommended)
password_encryption = scram-sha-256

# ssl should be enabled (optional but recommended)
ssl on

# network interfaces to listen on (default is localhost)
listen_addresses = '*'

# WAL replication settings
# write ahead log level
wal_level = replica

# Enable replication connections
# See: https://www.postgresql.org/docs/current/runtime-config-replication.html#GUC-MAX-WAL-SENDERS
max_wal_senders = 10

# See: https://www.postgresql.org/docs/current/runtime-config-replication.html#GUC-MAX-REPLICATION-SLOTS
max_replication_slots = 10

# Enables read-only queries on the standby, ignored by master
hot_standby = on
```

Edit pg_hba.conf (on Ubuntu by default in `/etc/postgresql/<version>/<cluster name>/`) and add configuration for the replication user. For example, the following allows the user "standby" from a host in the same network to connect to the master. The connection requires TLS and uses challenge-response password authentication. See https://www.postgresql.org/docs/current/auth-pg-hba-conf.html for more information.

```
# TYPE    DATABASE        USER            ADDRESS       METHOD
hostssl   replication     standby         samenet       scram-sha-256
```

Restart the master PostgreSQL instance before continuing.

On the master server, create the replication user:
```bash
sudo -iu postgres psql -c "CREATE USER standby REPLICATION PASSWORD '<password>'"
```

Also on master, create a replication slot for the standby. Replication slots provide an automated way to ensure that the master does not remove write-ahead-log segments until they have been received by a standby, even when standby is disconnected (see https://www.postgresql.org/docs/current/warm-standby.html#STREAMING-REPLICATION-SLOTS).

```bash
sudo -iu postgres psql -c "SELECT pg_create_physical_replication_slot('standby_node1');"
```

### Preparing the standby

* Verify that the standby PostgreSQL instance is not running.
* Clear the data directory (on Ubuntu, default location is `/var/lib/postgresql/<version>/<cluster name>`)

  ```bash
   rm -rf /path/to/data/directory/*
  ```

* Do a base backup with `pg_basebackup`:
  
  ```bash
  sudo -u postgres pg_basebackup -h <master> -U standby --slot=standby_node1 -R -D /path/to/data/directory/
  ```


* Additional settings:
  * Postgresql version < 12: Edit `recovery.conf` (in the data directory) and verify the settings:
  
  ```properties
  standby_mode = 'on'
  primary_conninfo = 'host=<master> user=<standby> password=<password>'
  primary_slot_name = 'standby_node1'
  recovery_target_timeline = 'latest'
  ```
   * Postgresql version >=12: Previous settings were moved to `postgresql.conf` instead and standby_mode is not used anymore. A standby.signal file in the data directory is used instead. (https://www.postgresql.org/docs/current/recovery-config.html):
  ```properties
  primary_conninfo = 'host=<master> user=<standby> password=<password>'
  primary_slot_name = 'standby_node1'
  recovery_target_timeline = 'latest'
  ```
  
  Where `<master>` is the DNS or IP address of the master node and `<standby>` and `<password>` are the credentials of the replication user (see https://www.postgresql.org/docs/current/runtime-config-replication.html#RUNTIME-CONFIG-REPLICATION-STANDBY).

* Start the standby server.

### Verifying replication

On master, check pg_stat_replication view:
```bash
sudo -iu postgres psql -txc "SELECT * FROM pg_stat_replication"
...
username         | standby
...
state            | streaming
sent_lsn         | 0/2A03F000
write_lsn        | 0/2A03F000
flush_lsn        | 0/2A03F000
replay_lsn       | 0/2A03F000
```

On stanbys, check pg_stat_wal_receiver view:
```bash
sudo -iu postgres psql -txc "SELECT * FROM pg_stat_wal_receiver"
...
status                | streaming
receive_start_lsn     | 0/29000000
received_lsn          | 0/2A03F000
last_msg_send_time    | 2019-12-30 07:32:03.902888+00
last_msg_receipt_time | 2019-12-30 07:32:03.903118+00
...
slot_name             | standby_node1
```

The `status` should be _streaming_ and `sent_lsn` on master should be close to `received_lsn` on the stanbys. If replication slots are in use, one can also compare the `sent_lsn` and `replay_lsn` values on the master.

### Configuring Central Servers

See [Central Server User Guide](ug-cs_x-road_6_central_server_user_guide.md#17-migrating-to-remote-database-host) for instructions about migrating an existing Central Server database to an external database.

Edit `/etc/xroad/db.properties` and change the connection properties:
```properties
spring.datasource.username=<database_user>
spring.datasource.password=<password>
spring.datasource.url=jdbc:postgresql://<master_host>:<master_port>/<database>
secondary_hosts=<standby_host>
```

Restart Central Servers and verify that the cluster is working (see [5 Monitoring HA State on a Node](#5-monitoring-ha-state-on-a-node)).

### Fail-over

In case the master server fails, one can manually promote the standby to a master by executing the following command on the standby server:
```bash
sudo pg_ctl promote
```

On Ubuntu pg_ctl is typically not in the path, use pg_ctlcluster instead:
```bash
sudo pg_ctlcluster <major version> <cluster name> promote
# e.g. sudo pg_ctlcluster 10 main promote
```

If the standby is configured as a secondary database host on the Central Servers, the servers will automatically reconnect to it.
To avoid a "split-brain" situation, the old master must not be started until it has been reconfigured as a standby.

See also: https://www.postgresql.org/docs/current/warm-standby-failover.html

#### Automatic fail-over

Achieving and maintaining a system with automated fail-over is a complex task and out of the scope of this guide. Some actively maintained open-source solutions for automating PostgreSQL failover include:

* Microsoft (Citus Data) pg_auto_failover: https://pg-auto-failover.readthedocs.io/en/latest/index.html
  * A straightforward solution for a two-node (primary-standby) setup. In addition to the data nodes, it requires a monitoring server (also a PostgreSQL instance).
* 2ndQuadrant Repmgr: https://repmgr.org/
  * An established solution for managing PostgreSQL replication and fail-over. Can handle more complex setups (e.g. several replicas) than pg_auto_failover.
* Patroni by Zalando: https://patroni.readthedocs.io/en/latest/
  * "Patroni is a template for you to create your own customized, high-availability solution using Python and a distributed configuration store like ZooKeeper, etcd, Consul or Kubernetes"
* ClusterLabs PostgreSQL Automatic Failover (PAF): http://clusterlabs.github.io/PAF/documentation.html
  * PAF is a resource agent for Pacemaker cluster resource manager; probably the most versatile but also most difficult to configure and operate.

## Appendix B. Central Server HA Migration

### Migrating from Standalone to HA Cluster

Since version 6.23.0 it is possible to use an external database for the Central Server. This is the basis of the currently recommended Central Server HA solution. It is possible to migrate a standalone Central Server (version 6.23.0 or later) to the cluster based HA solution with the following steps.

1. Take a backup of the Central Server.

2. Follow the instructions in [Central Server User Guide](ug-cs_x-road_6_central_server_user_guide.md#17-migrating-to-remote-database-host) to migrate the Central Server database from local to remote.

3. Follow the instructions in [General Installation of HA Support](#4-general-installation-of-ha-support)

4. Setup the database cluster as instructed in [Appendix A. Setting up a replicated PostgreSQL database](#appendix-a-setting-up-a-replicated-postgresql-database).
