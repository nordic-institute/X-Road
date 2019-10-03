![](img/eu_regional_development_fund_horizontal_div_15.png "European Union | European Regional Development Fund | Investing in your future")

---


# Central Server High Availability Installation Guide
**X-ROAD 6**

Version: 1.9  
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
 
## Table of Contents

<!-- toc -->

- [Central Server High Availability Installation Guide](#central-server-high-availability-installation-guide)
  - [Version history](#version-history)
  - [Table of Contents](#table-of-contents)
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
  - [5 Changing Nodes' IP Addresses in HA Cluster](#5-changing-nodes-ip-addresses-in-ha-cluster)
  - [6 Monitoring HA State on a Node](#6-monitoring-ha-state-on-a-node)
  - [7 Recovery of the HA cluster](#7-recovery-of-the-ha-cluster)

<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/

## 1 Introduction


### 1.1 High Availability for X-Road Central Server

The High Availability (HA) solution for the X-Road central server relies on database replication between nodes. Clustering works as an active-active asynchronous shared-nothing database. This enables every node to work as a standalone central server which receives data updates from all other nodes.

Security servers can download identical configuration from any of the public addresses published in a configuration anchor file. It is the responsibility of the central system manager to ensure that the central server nodes have not fallen out of sync due to network or other failure. For that purpose monitoring scripts are provided.

The solution supports up to 48 nodes with a minimum of 2 nodes.

Every central server node has its own:

-   node specific keys to sign configuration;

-   node specific public address which is distributed to the configuration clients in configuration anchor files;

-   optionally, management security server.

The technology used:

-   PostgreSQL 9.4;

-   BDR plugin for PostgreSQL.

The minimum X-Road central server version is 6.6.


### 1.2 Target Audience

The intended audience of this installation guide are X-Road central server administrators responsible for installing and configuring the X-Road central server software.

The document is intended for readers with a good knowledge of Linux server management, computer networks, and the X-Road functioning principles.

### 1.3 Terms and abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\]

### 1.4 References

1.  <a id="Ref_IG-CS" class="anchor"></a>\[IG-CS\] Cybernetica AS. X-Road 6. Central Server Installation Guide. Document ID: [IG-CS](ig-cs_x-road_6_central_server_installation_guide.md).

2.  <a id="Ref_UG-CS" class="anchor"></a>\[UG-CS\] Cybernetica AS. X-Road 6. Central Server User Guide. Document ID: [UG-CS](ug-cs_x-road_6_central_server_user_guide.md).

3.  <a id="Ref_TERMS" class="anchor"></a>\[TA-TERMS\] X-Road Terms and Abbreviations. Document ID: [TA-TERMS](../terms_x-road_docs.md).


## 2 Key Points and Known Limitations for X-Road Central Server HA Deployment

1.  Correct timekeeping is crucial.

    It is assumed that the clocks of the cluster nodes are synchronized using e.g. NTP. The administrator must configure a time synchronization service (e.g. ntpd, chrony, systemd-timesyncd) and take care to keep the time synced. Conflict resolution between database nodes is based on timestamps – the newest change will win and older ones will be rejected. Monitoring of the time drift on servers is generally suggested.

2.  Network security and speed.

    Even though all the clustered database connections are secured and authenticated with TLS, network security (especially confidentiality, availability) must be reviewed on infrastructure level.

    Network speed is not highly critical for the application itself as the cluster works in asynchronous mode but it can affect cluster initialization time and the time it takes for the changes to be distributed between nodes.

3.  X-Road upgrades which involve database changes – all the configured nodes must be available and running at the time the update is applied. Details will be stated in the change log if applicable.

4.  All the nodes must run the same patch-level X-Road software and database software.

5.  HA configuration with a single node is not supported.

6.  The removal of a configured node is not supported. This limitation will be removed in future releases.

7.  Time window for node failure repairing.

    A node can work (i.e. provide valid global configuration to the X-Road instance) without contacting other configured nodes for unlimited time as long as the number of active nodes is (number of total nodes)/2 +1. If fewer nodes are active, up to 1000 records can be inserted to each database table. See \[[1](#Ref_1)\] for details.

8.  Configuration files (located in `/etc/xroad/`) are not synchronized between nodes. It is the responsibility of the system administrator to change them in all nodes if required or stated by the user manual.


<a id="Ref_1"></a>
\[1\] http://bdr-project.org/docs/next/global-sequence-voting.html


## 3 Requirements and Workflows for HA Configuration


### 3.1 Requirements

The nodes must meet all the requirements listed in the X-Road Central Server Installation Guide (see \[[IG-CS](#Ref_IG-CS)\]). Additionally, creating an HA setup requires the following.

-   Root (sudo) level access to all the nodes to install authorized SSH public key for the root user.

-   Key-based SSH access to each node for the root user. This is the default SSH server setting in Ubuntu. If the servers have a different setting, back up the configuration of the SSH server before starting to configure the cluster.

-   Open ports between nodes:

    -   TCP 5432 (database connections)

    -   TCP 22 (SSH for configuring the cluster)


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

3.  On the node where the cluster was initialized add the IP addresses of the new nodes to the end of the `/etc/xroad/cluster/nodes`  file. Do not remove or alter any existing lines.

4.  Re-run the cluster initialization script:

        sudo -i -u xroad /usr/share/xroad/scripts/xroad_create_cluster.sh

5.  Install the X-Road central server software as described in the X-Road Central Server Installation Guide \[[IG-CS](#Ref_IG-CS)\] to each of the new nodes.

After installing and configuring all the X-Road central server nodes, retrieve new internal end external configuration anchor files from one of the nodes and distribute the files to all security servers and configuration proxies.


### 3.5 Post-Configuration Steps

After the database cluster has been configured and the X-Road Central Server packages have been installed, it is advisable to delete the keys generated during cluster setup.

If key-based SSH access to the nodes by the root user was disabled before enabling it for cluster setup, the respective configuration of the SSH server should be restored.


## 4 General Installation of HA Support

1.  Install the cluster management package on one node (from the X-Road repository which is configured as described in the X-Road Central Server Installation Guide \[[IG-CS](#Ref_IG-CS)\]):

        sudo apt-get install xroad-center-clusterhelper

2.  Create/modify a list of HA node IP addresses in `/etc/xroad/cluster/nodes`

    -   one IP per line, no spaces, no comments;

    -   the node to be upgraded from non-HA-&gt;HA (if any) must be at the first position in the nodes file;

    -   do not remove or alter any existing lines in the nodes file.

    Sample contents of the nodes file:

        $ cat /etc/xroad/cluster/nodes
        192.168.56.201
        192.168.56.202

3.  Execute the cluster setup script and follow the output:

        sudo -i -u xroad /usr/share/xroad/scripts/xroad_create_cluster.sh

    What is done during the setup:

    1.  An SSH key is configured and a command for distributing the SSH key to all the servers is displayed. **The public key must be distributed to all the servers manually, before allowing the script to continue.** SSH access to all nodes is checked next.

    2.  A self-signed CA is created and TLS keys for secure database connections are generated.

    3.  PostgreSQL 9.4 with BDR plugin is installed and configured to establish database connections between nodes.

    4.  An X-Road specific database role with the needed features is created.
        If the first node contains an older database with the X-Road database schema then the old database schema will be migrated to the new database.

    **NOTE 1**: The location of the log file with detailed information about initialization progress is displayed when you start the cluster initialization script. Logs are named as

        /var/log/xroad/cluster_<datetime>.log

    **NOTE 2**: You can re-run the cluster initialization script after you have corrected the issues that caused script termination.

4. Install the X-Road central server cluster helper package on all the added nodes:

        sudo apt-get install xroad-center-clusterhelper

    In addition to the cluster setup script, the package provides tools for monitoring the status of the cluster.

## 5 Changing Nodes' IP Addresses in HA Cluster

The script for changing the IP addresses of the HA cluster nodes behaves similarly to the HA support installation script.

To change the IP addresses of the nodes:
* Replace the system IP addresses in all cluster nodes.
* Make sure PostgreSQL is running with bdr-9.4 support.
* Create new nodes file `/etc/xroad/cluster/nodes.new`, containing `<old-IP> <new-IP>` per each line, e.g:


    192.168.56.40 192.168.57.45
    192.168.56.41 192.168.57.46

* Run the `modify_cluster.sh` script:


    sudo -i -u xroad /usr/share/xroad/scripts/modify_cluster.sh

The script will:
* replace the IP addresses in the `/etc/xroad/cluster/nodes` file;
* modify the PostgreSQL configuration file;
* alternate the IP-s in the Postgres-BDR tables.

## 6 Monitoring HA State on a Node

A script for checking cluster health is available on every node with the `xroad-center-clusterhelper` package. To view cluster status run the following command:

    /usr/share/xroad/scripts/check_ha_cluster_status.py

Sample output is similar to the following (emphasizing the important values):

    $ /usr/share/xroad/scripts/check_ha_cluster_status.py

    SUMMARY OF CLUSTER STATUS:
        All nodes: OK
        Configuration: OK

    DETAILED CLUSTER STATUS INFORMATION:
    {
        "all_nodes_ok": true,
        "configuration_ok": true,
        "ha_configured": true,
        "nodes": {
            "node_0": {
                "external_anchor_update_timestamp": "2015-08-28T14:08:31Z",
                "internal_anchor_update_timestamp": "2015-08-28T14:08:29Z",
                "node_status": "ready",
                "private_params_update_timestamp": "2015-08-31T13:34:01Z",
                "shared_params_update_timestamp": "2015-08-31T13:34:01Z"
            },
            "node_1": {
                "external_anchor_update_timestamp": "2015-08-28T14:08:31Z",
                "internal_anchor_update_timestamp": "2015-08-28T14:08:29Z",
                "node_status": "ready",
                "private_params_update_timestamp": "2015-08-31T13:34:02Z",
                "replication_client_address": "192.168.8.96",
                "replication_lag_bytes": "0",
                "replication_state": "streaming",
                "shared_params_update_timestamp": "2015-08-31T13:34:02Z"
            }
        }
    }

The timestamps of the generated private and shared parameter files on different nodes must be within a reasonable time window. The timestamps of the internal and external anchors must be equal.


## 7 Recovery of the HA cluster

This section describes the steps that are required to recover from system failure which has resulted in a loss of all cluster nodes.

1.  Initialize an HA cluster **at least on two nodes**. See chapter [4](#4-general-installation-of-ha-support). If the original cluster consisted of more than two nodes, additional nodes can be added later.

2.  Install the X-Road central server software according to the X-Road Central Server Installation Guide \[[IG-CS](#Ref_IG-CS)\] on the **first node**.

3.  Restore the system configuration **on the first node (node\_0) using the backup from node\_0**. From the command line specifying the correct **instance identifier** and **the location of the backup file** (as a single line):

    `sudo -i -u xroad /usr/share/xroad/scripts/restore_xroad_center_configuration.sh -i CC -n node_0 -f /root/conf_backup_20151216-125451.tar`

    For restoration details see the Central Server User Guide \[[UG-CS](#Ref_UG-CS)\].

4.  Verify Central Server UI for correct data. Specifically check configuration signing keys – their availability and correctness. If needed – change the central server's address.

5.  Install the X-Road central server software according to the X-Road Central Server Installation Guide \[[IG-CS](#Ref_IG-CS)\] on the **other nodes**.

6.  Restore the system configuration **on the second node (node\_1) using the backup from node\_1**. From command line specifying the correct **instance code** and **the location of the backup file** (as a single line):

    `sudo -i -u xroad /usr/share/xroad/scripts/restore_xroad_center_configuration.sh -S -i CC -n node_1 -f /root/conf_backup_20151216-125524.tar`

    **Please note the extra parameter -S** – this prevents database restoration as it has been already restored in step 3.

    For restoration details see the Central Server User Guide \[[UG-CS](#Ref_UG-CS)\].

7.  Verify Central Server UI for correct data. Specifically check configuration signing keys – their availability and correctness. If needed – change the central server's address.

8.  For other nodes repeat steps 6. and 7. changing the cluster node identifier.

9.  If configuration keys or central system addresses were modified during recovery – a new configuration anchor file must be distributed to members. See the Central Server User Guide \[[UG-CS](#Ref_UG-CS)\].
