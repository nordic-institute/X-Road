|                                                     |
|-----------------------------------------------------|
| X-ROAD 6                                            
                                                      
 Central Server High Availability Installation Guide  
                                                      
 1.3                                                  |
|                                                     |

Version HISTORY

|            |         |                            |
|------------|---------|----------------------------|
| Date       | Version | Description                |
| 31.08.2015 | 0.1     | Initial version created.   |
| 15.09.2015 | 1.0     | Minor fixes done.          |
| 20.09.2015 | 1.1     | Editorial changes made     |
| 16.12.2015 | 1.2     | Added recovery information |
| 17.12.2015 | 1.3     | Editorial changes made     |

Table of Contents

[1 Introduction 4](#introduction)

[1.1 High Availability for X-Road Central Server 4](#high-availability-for-x-road-central-server)

[1.2 Target Audience 4](#target-audience)

[1.3 References 4](#references)

[2 Key Points and Known Limitations for X-Road Ce ntral Server HA Deployment 5](#key-points-and-known-limitations-for-x-road-ce-ntral-server-ha-deployment)

[3 Requirements and Workflows for HA Configuration 6](#requirements-and-workflows-for-ha-configuration)

[3.1 Requirements 6](#requirements)

[3.2 Workflow for a New X-Road Instance Setup 6](#workflow-for-a-new-x-road-instance-setup)

[3.3 Workflow for Upgrading an Existing X-Road Central Server to an HA Configuration 6](#workflow-for-upgrading-an-existing-x-road-central-server-to-an-ha-configuration)

[3.4 Workflow for Adding New Nodes to an Existing HA Configuration 6](#workflow-for-adding-new-nodes-to-an-existing-ha-configuration)

[3.5 Post-Configuration Steps 7](#post-configuration-steps)

[4 General Installation of HA Support 8](#general-installation-of-ha-support)

[5 Monitoring HA State on a Node 9](#monitoring-ha-state-on-a-node)

[6 Recovery of the HA cluster 10](#recovery-of-the-ha-cluster)

Introduction
============

High Availability for X-Road Central Server
-------------------------------------------

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

Target Audience
---------------

The intended audience of this installation guide are X-Road central server administrators responsible for installing and configuring the X-Road central server software.

The document is intended for readers with a good knowledge of Linux server management, computer networks, and the X-Road functioning principles.

References
----------

1.  <span id="__RefHeading__1000_2054282326" class="anchor"><span id="Ref_IG-CS" class="anchor"></span></span>\[IG-CS\] Cybernetica AS. X-Road 6. Central Server Installation Guide. Document ID: IG-CS.

2.  \[<span id="Ref_UG-CS" class="anchor"></span>UG-CS\] Cybernetica AS. X-Road 6. Central Server User Guide. Document ID: UG-CS.

Key Points and Known Limitations for X-Road Ce ntral Server HA Deployment
=========================================================================

1.  Correct timekeeping is crucial.

NTP is installed during cluster setup to all nodes. It is assumed that setting the system time using NTP is enabled. The administrator must take care to keep the time synced at all times. Conflict resolution between database nodes is based on timestamps – the newest change will win and older ones will be rejected. Monitoring of the time drift on servers is generally suggested.

1.  Network security and speed.

Even though all the clustered database connections are secured and authenticated with TLS, network security (especially confidentiality, availability) must be reviewed on infrastructure level.

Network speed is not highly critical for the application itself as the cluster works in asynchronous mode but it can affect cluster initialization time and the time it takes for the changes to be distributed between nodes.

1.  X-Road upgrades which involve database changes – all the configured nodes must be available and running at the time the update is applied. Details will be stated in the change log if applicable.

2.  All the nodes must run the same patch-level X-Road software and database software.

3.  HA configuration with a single node is not supported.

4.  The removal of a configured node is not supported. This limitation will be removed in future releases.

5.  Time window for node failure repairing.

A node can work (i.e. provide valid global configuration to the X-Road instance) without contacting other configured nodes for unlimited time as long as the number of active nodes is (number of total nodes)/2 +1. If fewer nodes are active, up to 1000 records can be inserted to each database table. See [1] for details.

1.  Configuration files (located in /etc/xroad/) are not synchronized between nodes. It is the responsibility of the system administrator to change them in all nodes if required or stated by the user manual.<span id="__RefHeading__56609_945751461" class="anchor"></span>

Requirements and Workflows for HA Configuration
===============================================

Requirements
------------

The nodes must meet all the requirements listed in the X-Road Central Server Installation Guide (see \[IG-CS\]). Additionally, creating an HA setup requires the following.

-   Root (sudo) level access to all the nodes to install authorized SSH public key for the root user.

-   Key-based SSH access to each node for the root user. This is the default SSH server setting in Ubuntu 14.04. If the servers have a different setting, back up the configuration of the SSH server before starting to configure the cluster.

-   Open ports between nodes:

    -   TCP 5432 (database connections)

    -   TCP 22 (SSH for configuring the cluster)

Workflow for a New X-Road Instance Setup
----------------------------------------

1.  Install HA support according to steps listed in section 4 : .

2.  Install the X-Road central server software according to the X-Road Central Server Installation Guide \[IG-CS\] on each node.

Workflow for Upgrading an Existing X-Road Central Server to an HA Configuration
-------------------------------------------------------------------------------

1.  Upgrade the existing X-Road central server software to the latest release available, verify system health.

2.  Create a backup of the system configuration and store it in a safe place.

3.  Continue with HA support installation steps as described in section 4 : .

4.  Install the X-Road central server software as described in the X-Road Central Server Installation Guide \[IG-CS\] to each of the new nodes.

5.  After installing and configuring all the X-Road central server nodes, retrieve new internal and external configuration anchor files from one of the nodes and distribute the files to all security servers and configuration proxies.

Workflow for Adding New Nodes to an Existing HA Configuration
-------------------------------------------------------------

Referencing steps in section 4 :

1.  Upgrade the software of the existing nodes to the latest version available, verify system health on all nodes.

2.  Create a backup system configuration of the existing nodes and store it in a safe place.

3.  On the node where the cluster was initialized add the IP addresses of the new nodes to the end of the /etc/xroad/cluster/nodes file. Do not remove or alter any existing lines.

4.  Re-run the cluster initialization script:

sudo -i -u xroad /usr/share/xroad/scripts/xroad\_create\_cluster.sh

1.  Install the X-Road central server software as described in the X-Road Central Server Installation Guide \[IG-CS\] to each of the new nodes.

After installing and configuring all the X-Road central server nodes, retrieve new internal end external configuration anchor files from one of the nodes and distribute the files to all security servers and configuration proxies.

Post-Configuration Steps
------------------------

After the database cluster has been configured and the X-Road Central Server packages have been installed, it is advisable to delete the keys generated during cluster setup.

If key-based SSH access to the nodes by the root user was disabled before enabling it for cluster setup, the respective configuration of the SSH server should be restored.

General Installation of HA Support
==================================

1.  Install the cluster management package on one node (from the X-Road repository which is configured as described in the X-Road Central Server Installation Guide \[IG-CS\]):

sudo apt-get install xroad-center-clusterhelper

1.  Create/modify a list of HA node IP addresses in /etc/xroad/cluster/nodes

-   one IP per line, no spaces, no comments;

-   the node to be upgraded from non-HA-&gt;HA (if any) must be at the first position in the nodes file;

-   do not remove or alter any existing lines in the nodes file.

Sample contents of the nodes file:

$ cat /etc/xroad/cluster/nodes

192.168.56.201

192.168.56.202

1.  Execute the cluster setup script and follow the output:

sudo -i -u xroad /usr/share/xroad/scripts/xroad\_create\_cluster.sh

What is done during the setup:

1.  An SSH key is configured and a command for distributing the SSH key to all the servers is displayed. **The public key must be distributed to all the servers manually, before allowing the script to continue.** SSH access to all nodes is checked next.

2.  NTP is installed and immediate NTP time sync is called to ensure time correctness on all nodes.

3.  A self-signed CA is created and TLS keys for secure database connections are generated.

4.  PostgreSQL 9.4 with BDR plugin is installed and configured to establish database connections between nodes.

5.  An X-Road specific database role with the needed features is created.
    If the first node contains an older database with the X-Road database schema then the old database schema will be migrated to the new database.

**NOTE 1**: The location of the log file with detailed information about initialization progress is displayed when you start the cluster initialization script. Logs are named as

/var/log/xroad/cluster\_&lt;datetime&gt;.log

**NOTE 2**: You can re-run the cluster initialization script after you have corrected the issues that caused script termination.

1.  Install the X-Road central server cluster helper package on all the added nodes:

sudo apt-get install xroad-clusterhelper

In addition to the cluster setup script, the package provides tools for monitoring the status of the cluster.

Monitoring HA State on a Node
=============================

A script for checking cluster health is available on every node with the xroad-center-clusterhelper package. To view cluster status run the following command:

/usr/share/xroad/scripts/check\_ha\_cluster\_status.py

Sample output is similar to the following (emphasizing the important values):

$ /usr/share/xroad/scripts/check\_ha\_cluster\_status.py

SUMMARY OF CLUSTER STATUS:

All nodes: **OK**

Configuration: **OK**

DETAILED CLUSTER STATUS INFORMATION:

{

"all\_nodes\_ok": **true**,

"configuration\_ok": **true**,

"ha\_configured": **true**,

"nodes": {

"node\_0": {

"external\_anchor\_update\_timestamp": "2015-08-28T14:08:31Z",

"internal\_anchor\_update\_timestamp": "2015-08-28T14:08:29Z",

"node\_status": "**ready**",

"private\_params\_update\_timestamp": "2015-08-31T13:34:01Z",

"shared\_params\_update\_timestamp": "2015-08-31T13:34:01Z"

},

"node\_1": {

"external\_anchor\_update\_timestamp": "2015-08-28T14:08:31Z",

"internal\_anchor\_update\_timestamp": "2015-08-28T14:08:29Z",

"node\_status": "**ready**",

"private\_params\_update\_timestamp": "2015-08-31T13:34:02Z",

"replication\_client\_address": "192.168.8.96",

"replication\_lag\_bytes": "**0**",

"replication\_state": "**streaming**",

"shared\_params\_update\_timestamp": "2015-08-31T13:34:02Z"

}

}

}

The timestamps of the generated private and shared parameter files on different nodes must be within a reasonable time window. The timestamps of the internal and external anchors must be equal.

Recovery of the HA cluster
==========================

This section describes the steps that are required to recover from system failure which has resulted in a loss of all cluster nodes.

1.  Initialize an HA cluster **at least on two nodes**. See chapter 4 : . If the original cluster consisted of more than two nodes,additional nodes can be added later.

2.  Install the X-Road central server software according to the X-Road Central Server Installation Guide \[IG-CS\] on the **first node**.

3.  Restore the system configuration **on the first node (node\_0) using the backup from node\_0**. From the command line specifying the correct **instance identifier** and **the location of the backup file**(as a single line):

sudo -i -u xroad /usr/share/xroad/scripts/restore\_xroad\_center\_configuration.sh -i **CC** -n node\_0 -f /root/conf\_backup\_20151216-125451.tar

For restoration details see the Central Server User Guide \[UG-CS\].

1.  Verify Central Server UI for correct data. Specifically check configuration signing keys – their availability and correctness. If needed – change the central server's address.

2.  Install the X-Road central server software according to the X-Road Central Server Installation Guide \[IG-CS\] on the **other nodes**.

3.  Restore the system configuration **on the second node (node\_1) using the backup from node\_1**. From command line specifying the correct **instance code** and **the location of the backup file** (as a single line):

sudo -i -u xroad /usr/share/xroad/scripts/restore\_xroad\_center\_configuration.sh **-S** -i **CC** -n node\_1 -f /root/conf\_backup\_20151216-125524.tar

**Please note the extra parameter -S** – this prevents database restoration as it has been already restored in step 3.

For restoration details see the Central Server User Guide \[UG-CS\].

1.  Verify Central Server UI for correct data. Specifically check configuration signing keys – their availability and correctness. If needed – change the central server's address.

2.  For other nodes repeat steps 6. and 7. changing the cluster node identifier.

3.  If configuration keys or central system addresses were modified during recovery – a new configuration anchor file must be distributed to members. See the Central Server User Guide \[UG-CS\].

[1] http://bdr-project.org/docs/next/global-sequence-voting.html
