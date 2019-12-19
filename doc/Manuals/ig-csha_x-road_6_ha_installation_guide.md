![](img/eu_regional_development_fund_horizontal_div_15.png "European Union | European Regional Development Fund | Investing in your future")

---


# Central Server High Availability Installation Guide
**X-ROAD 6**

Version: 1.10  
Doc. ID: IG-CSHA

---


## Version history

 Date       | Version | Description                                                     | Author
 ---------- | ------- | --------------------------------------------------------------- | --------------------
 31.08.2015 | 0.1     | Initial version created.
 15.09.2015 | 1.0     | Minor fixes done.
 20.09.2015 | 1.1     | Editorial changes made
 16.12.2015 | 1.2     | Added recovery information
 17.12.2015 | 1.3     | Editorial changes made
 20.02.2017 | 1.4     | Converted to Github flavoured Markdown, added license text, adjusted tables for better output in PDF | Toomas Mölder
 05.03.2018 | 1.5     | Added terms and abbreviations references and document links  | Tatu Repo
 03.10.2018 | 1.6     | Added the chapter "Changing Nodes' IP Addresses in HA Cluster"
 19.12.2018 | 1.7     | Minor changes related to Ubuntu 18 support
 03.01.2019 | 1.8     | Removed forced NTP installation. | Jarkko Hyöty
 27.09.2019 | 1.9     | Minor fix | Petteri Kivimäki
 03.12.2019 | 1.10    | Removed dependency on BDR | Jarkko Hyöty
 
## Table of Contents

<!-- toc -->
<!-- vim-markdown-toc GFM -->

* [License](#license)
* [1 Introduction](#1-introduction)
  * [1.1 High Availability for X-Road Central Server](#11-high-availability-for-x-road-central-server)
  * [1.2 Target Audience](#12-target-audience)
  * [1.3 Terms and abbreviations](#13-terms-and-abbreviations)
  * [1.4 References](#14-references)
* [2 Key Points and Known Limitations for X-Road Central Server HA Deployment](#2-key-points-and-known-limitations-for-x-road-central-server-ha-deployment)
* [3 Requirements and Workflows for HA Configuration](#3-requirements-and-workflows-for-ha-configuration)
  * [3.1 Requirements](#31-requirements)
  * [3.2 Workflow for a New X-Road Instance Setup](#32-workflow-for-a-new-x-road-instance-setup)
  * [3.3 Workflow for Upgrading an Existing X-Road Central Server to an HA Configuration](#33-workflow-for-upgrading-an-existing-x-road-central-server-to-an-ha-configuration)
  * [3.4 Workflow for Adding New Nodes to an Existing HA Configuration](#34-workflow-for-adding-new-nodes-to-an-existing-ha-configuration)
  * [3.5 Upgrading from a previous version of the HA cluster](#35-upgrading-from-a-previous-version-of-the-ha-cluster)
  * [3.6 Post-Configuration Steps](#36-post-configuration-steps)
* [4 General Installation of HA Support](#4-general-installation-of-ha-support)
* [5 Monitoring HA State on a Node](#5-monitoring-ha-state-on-a-node)
* [6 Recovery of the HA cluster](#6-recovery-of-the-ha-cluster)
  * [6.1 Configuration database (and possible replicas) is lost](#61-configuration-database-and-possible-replicas-is-lost)
  * [6.2 One or more cental server nodes lost, backup available](#62-one-or-more-cental-server-nodes-lost-backup-available)
  * [6.3 Some central server nodes lost, backup not available](#63-some-central-server-nodes-lost-backup-not-available)

<!-- vim-markdown-toc -->
<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/

## 1 Introduction


### 1.1 High Availability for X-Road Central Server

The High Availability (HA) solution for the X-Road central server relies on a shared, optionally highly-available database. This enables every node to work as a standalone central server which receives configuration updates from all other nodes. Security servers can download identical configuration from any of the public addresses published in a configuration anchor file.

Every central server node has its own:
- node specific keys to sign configuration;
- node specific public address which is distributed to the configuration clients in configuration anchor files;
- optionally, a management security server.

In addition, a highly-available PostgreSQL compatible database is required for storing the shared configuration. Setting up the database is out of the scope of this document.

The minimum X-Road central server version is 6.23.

### 1.2 Target Audience

The intended audience of this installation guide are X-Road central server administrators responsible for installing and configuring the X-Road central server software.

The document is intended for readers with a good knowledge of Linux server management, computer networks, and the X-Road functioning principles.

### 1.3 Terms and abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\]

### 1.4 References

1.  <a id="Ref_IG-CS" class="anchor"></a>\[IG-CS\] X-Road 6. Central Server Installation Guide. Document ID: [IG-CS](ig-cs_x-road_6_central_server_installation_guide.md).
2.  <a id="Ref_UG-CS" class="anchor"></a>\[UG-CS\] X-Road 6. Central Server User Guide. Document ID: [UG-CS](ug-cs_x-road_6_central_server_user_guide.md).
3.  <a id="Ref_TERMS" class="anchor"></a>\[TA-TERMS\] X-Road Terms and Abbreviations. Document ID: [TA-TERMS](../terms_x-road_docs.md).


## 2 Key Points and Known Limitations for X-Road Central Server HA Deployment

1.  Correct timekeeping is important.

    It is assumed that the clocks of the cluster nodes are synchronized using e.g. NTP. The administrator must configure a time synchronization service (e.g. ntpd, chrony, systemd-timesyncd) and take care to keep the time synced.

2.  Network security and speed.

    Even though all the database connections are secured and authenticated with TLS, network security (especially confidentiality, availability) must be reviewed on infrastructure level. The network speed and reliability between central server nodes and the shared database is important for the functionality of the system. A central server node can not function if it can not write to and read from the database.

3.  All the nodes must run the same patch-level X-Road software and database software.

4.  Time window for node failure repairing.

    A node can work (i.e. provide valid global configuration to the X-Road instance) as long as it can read from and write to the shared configuration database. If one node loses database access, other nodes continue providing valid global configuration, and security servers will switch downloading the configuration from a healthly node. In the case all nodes or the shared database fails, security servers can function until global configuration exprires, but new security servers can not be added to the X-Road instance.

5.  Configuration files (located in `/etc/xroad/`) are not synchronized between nodes. It is the responsibility of the system administrator to change them in all nodes if required or stated by the user manual.

## 3 Requirements and Workflows for HA Configuration

### 3.1 Requirements

The nodes must meet all the requirements listed in the X-Road Central Server Installation Guide (see \[[IG-CS](#Ref_IG-CS)\]). 
Additionally, creating an HA setup requires that all nodes can access the shared configuration database. A PostgreSQL (version 9.4 or newer) compatible database is required.

### 3.2 Workflow for a New X-Road Instance Setup

1.  Install HA support according to steps listed in section [4](#4-general-installation-of-ha-support).
2.  Install the X-Road central server software according to the X-Road Central Server Installation Guide \[[IG-CS](#Ref_IG-CS)\] on each node.

### 3.3 Workflow for Upgrading an Existing X-Road Central Server to an HA Configuration

1.  Upgrade the existing X-Road central server software to the latest release available, verify system health.
2.  Create a backup of the system configuration and store it in a safe place.
3.  Continue with HA support installation steps as described in section [4](#4-general-installation-of-ha-support).
4.  Install the X-Road central server software as described in the X-Road Central Server Installation Guide \[[IG-CS](#Ref_IG-CS)\] to each of the new nodes.
5.  After installing and configuring all the X-Road central server nodes, retrieve new internal and external configuration anchor files from one of the nodes and distribute the files to all security servers and configuration proxies.

### 3.4 Workflow for Adding New Nodes to an Existing HA Configuration

Referencing steps in section [4](#4-general-installation-of-ha-support).

1.  Upgrade the software of the existing nodes to the latest version available, verify system health on all nodes.
2.  Create a backup system configuration of the existing nodes and store it in a safe place.
3.  Prepare the node for HA configuration as described in section [4](#4-general-installation-of-ha-support).
4.  Install the X-Road central server software as described in the X-Road Central Server Installation Guide \[[IG-CS](#Ref_IG-CS)\] to each of the new nodes.

After installing and configuring all the X-Road central server nodes, retrieve new internal end external configuration anchor files from one of the nodes and distribute the files to all security servers and configuration proxies.

### 3.5 Upgrading from a previous version of the HA cluster

It is possible to use a BDR database cluster from a pre-6.23.0 version. Before upgrading the software to version 6.23 (or newer), define the node name on each node like described in [4](#4-general-installation-of-ha-support). 
The HA node name is displayed in the central server admin UI. It is also possible to query the local database node from command line:
```
sudo -iu postgres psql -qtA -d centerui_production -c "select bdr.bdr_get_local_node_name()"
```
 
### 3.6 Post-Configuration Steps

If the remote database supports fail over to a secondary host (e.g. when using PostgreSQL streaming replication with hot-standby), it is possible to define the secondary database hosts in `/etc/xroad/db.properties`. The system will automatically try the secondary hosts in case the primary fails.

```
secondary_hosts=<comma separated list of hosts>

# Example: 
# secondary_hosts=standby.example.org,standby2.example.org
```

The secondary hosts are assumed to use the same port (default 5432) as the primary host.

## 4 General Installation of HA Support

The HA support requires that an external database is initialized and available (see [X-Road Central Server Installation Guide](#Ref_IG-CS) about using an external database). 
The database can also be installed on the central server node(s), but that is not recommended unless a multi-master setup (e.g. BDR) is used.

In addition, it is necessary to configure a unique node name for each node participating in the cluster before installing the X-Road software.

1.  On each node, edit file `/etc/xroad/local.ini`, creating it if necessary
2.  Add the following lines
    ```
    [center]
    ha-node-name=<node_name>
    ```
    Where `<node_name>` is the unique name for the HA node. It is suggested to follow a naming scheme where the first node is `node_0` and subsequent ones `node_1`, `node_2`, etc..
    
    When upgrading an existing central server to HA configuration, the name of the existing server *must be* `node_0`.

    If upgrading an existing cluster with BDR, the name of the node **must be** the same as the local BDR node name.

3.  Install the X-Road central server software according to the X-Road Central Server Installation Guide \[[IG-CS](#Ref_IG-CS)\] on each node.
    
    When the installation asks for a database host, provide the details of the external database.

## 5 Monitoring HA State on a Node

It is possible to get HA status via a web interface, for example using curl:
```
curl -k https://cs1.example.org:4000/public_system_status/check_ha_cluster_status
```
```
{
  "ha_node_status": {
    "ha_configured": true,
    "node_name": "node_0",
    "nodes": [
      {
        "node_name": "node_0",
        "node_address": "cs1.example.org",
        "configuration_generated": "2019-12-03 14:48:02.306199",
        "status": "OK"
      },
      {
        "node_name": "node_1",
        "node_address": "cs2.example.org",
        "configuration_generated": "2019-12-03 14:47:02.053865",
        "status": "WARN"
      }
    ]
  }
}
```
The status information is based on the data in the configuration database and other nodes are not directly accessed. If the database is not available at all, the status check will respond with an error (HTTP error 503 Service Unvailable).
A node status is:
  * "OK" if the configuration is recently generated.
  * "WARN" if the timestamp is more than a global configuration generation interval in the past.
  * "ERROR" if the timestamp is older than the global configuration expriry time.
  * "UNKNOWN" if the node has not been seen at all.

For a global view of the cluster status, the check should be executed on each node and compared to verify that nodes have a consistent view of the status.

In addition, one should monitor the database status, and if a replication solution is used, the database replication status using the tools provided by the database and/or replication solution. 

## 6 Recovery of the HA cluster

Recovery procedure depends on the type of the failure and database used. If configuration keys or central system addresses are modified during recovery, a new configuration anchor file must be distributed to members. See the Central Server User Guide \[[UG-CS](#Ref_UG-CS)\].

### 6.1 Configuration database (and possible replicas) is lost

* Stop central servers.
* Set up a new database.
* Update the db.properties on each central server node to point to the new database.
* On one node, restore the database from a backup (see \[[UG-CS](#Ref_UG-CS)\]).
* Start central servers.

### 6.2 One or more cental server nodes lost, backup available

* Setup the missing nodes like described in [3.4](#34-workflow-for-adding-new-nodes-to-an-existing-ha-configuration)
* Restore configuration from a backup, skipping database restoration (see \[[UG-CS](#Ref_UG-CS)\]).

### 6.3 Some central server nodes lost, backup not available

See [3.4 Workflow for Adding New Nodes to an Existing HA Configuration](#34-workflow-for-adding-new-nodes-to-an-existing-ha-configuration)

