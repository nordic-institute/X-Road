# Central Server High Availability Installation Guide <!-- omit in toc -->
**X-ROAD 7**

Version: 1.28
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
| 11.11.2026 | 1.21    | Document the node name check, the default node name, the shared rows and signer rows of a standalone server, the node name check of an existing name, the signer rows after a rename and the per-node TLS certificate names | Ričardas Bučiūnas  |
| 12.11.2026 | 1.22    | Document the root token file and the unseal keys that every node keeps, and upgrading all nodes in one window | Ričardas Bučiūnas  |
| 15.11.2026 | 1.23    | Document that the secret store retry covers key-value calls only                                              | Ričardas Bučiūnas  |
| 16.11.2026 | 1.24    | Document that the secret store retry covers the Issuer Service                                                | Ričardas Bučiūnas  |
| 17.11.2026 | 1.25    | Document joining the OpenBao cluster, the ports between the nodes and using an external OpenBao               | Ričardas Bučiūnas  |
| 18.11.2026 | 1.26    | Correct the steps for joining a node to the OpenBao cluster and state the limits of the Red Hat packages  | Ričardas Bučiūnas  |
| 20.11.2026 | 1.27    | Rename the bundle script to secret-store-cluster-bundle.sh                                                | Ričardas Bučiūnas  |
| 25.11.2026 | 1.28    | Document the database conditions of a cluster, the connection pools and the installer checks of the database address. Remove the text about secondary database hosts and the PostgreSQL versions below 15. State that the write address is set at the first installation, add the OpenBao and admin service connection pool defaults, preseed the database address and describe the upgrade with an existing host list | Ričardas Bučiūnas  |


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
    - [3.1.1 Database requirements](#311-database-requirements)
    - [3.1.2 Connection pools](#312-connection-pools)
    - [3.1.3 Installer checks of the database](#313-installer-checks-of-the-database)
  - [3.2 Workflow for a New X-Road Instance Setup](#32-workflow-for-a-new-x-road-instance-setup)
  - [3.3 Workflow for Upgrading an Existing X-Road Central Server to an HA Configuration](#33-workflow-for-upgrading-an-existing-x-road-central-server-to-an-ha-configuration)
  - [3.4 Workflow for Adding New Nodes to an Existing HA Configuration](#34-workflow-for-adding-new-nodes-to-an-existing-ha-configuration)
- [4 General Installation of HA Support](#4-general-installation-of-ha-support)
  - [4.1 Node name and renaming a node](#41-node-name-and-renaming-a-node)
  - [4.2 Root token file and unseal keys on every node](#42-root-token-file-and-unseal-keys-on-every-node)
  - [4.3 Joining the OpenBao cluster](#43-joining-the-openbao-cluster)
  - [4.4 Using an external OpenBao](#44-using-an-external-openbao)
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

In addition, a highly-available PostgreSQL database is required for storing the shared configuration. The conditions that the database must meet are in section [3.1.1](#311-database-requirements). Setting up the database is out of the scope of this document.

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

    It is assumed that the clocks of the cluster nodes are synchronized using e.g. NTP. The administrator must configure a time synchronization service (e.g. ntpd, chrony, systemd-timesyncd) and take care to keep the time synced. The difference between the clocks of the nodes must stay small, see section [3.1.1](#311-database-requirements).

2.  Network security and speed.

    Even though all the database connections are secured and authenticated with TLS, network security (especially confidentiality, availability) must be reviewed on infrastructure level. The network speed and reliability between Central Server nodes and the shared database is important for the functionality of the system. A Central Server node can not function if it can not write to and read from the database.

3.  All the nodes must run the same patch-level X-Road software and database software.

4.  Time window for node failure repairing.

    A node can work (i.e. provide valid global configuration to the X-Road instance) as long as it can read from and write to the shared configuration database. If one node loses database access, other nodes continue providing valid global configuration, and Security Servers will switch downloading the configuration from a healthly node. In the case all nodes or the shared database fails, Security Servers can function until global configuration exprires, but new Security Servers can not be added to the X-Road instance.

5.  Configuration files (located in `/etc/xroad/`) are not synchronized between nodes. It is the responsibility of the system administrator to change them in all nodes if required or stated by the user manual.

## 3 Requirements and Workflows for HA Configuration

### 3.1 Requirements

The nodes must meet all the requirements listed in the X-Road Central Server Installation Guide (see \[[IG-CS](#Ref_IG-CS)\]). 
Additionally, creating an HA setup requires that all nodes can access the shared configuration database. The database must meet the conditions in section [3.1.1](#311-database-requirements).

#### 3.1.1 Database requirements

You set up and operate the database. X-Road does not choose or provide a PostgreSQL high availability product. The database must meet these conditions:

- **PostgreSQL 15 or newer.** All nodes use the same version of the database software.
- **One write address.** All nodes and all components of a node use the same database address. The address always leads to the one database server that accepts writes. It can be the primary server, a virtual IP address, a connection pooler, a managed database endpoint or a Kubernetes read-write service. Do not use the address of a standby server or of a read-only replica. Do not use a list of hosts.
- **An address that follows a failover.** You set the write address at the first installation of the Central Server, and it must stay valid. Use a stable name or a virtual IP address that leads to the new primary server after a failover, without a change on the nodes. The installation stores the database address in more than one place: in `/etc/xroad/db.properties` (the Central Server and the Issuer Service) and in `/etc/openbao/xroad-openbao.env` (the secret store, OpenBao). The installation does not change the stored addresses later, and no procedure in this guide changes them.
- **One writer.** At any time, only one database server accepts writes. A database with more than one writer is not supported.
- **Isolation level `READ COMMITTED`.** This is the default of PostgreSQL, and no X-Road component sets another level. Do not set another default transaction isolation level for the database or for the roles of X-Road.
- **Tight clock synchronization.** Use a time synchronization service on every node. Keep the difference between the clocks of the nodes small.
- **A database master user that can create roles and databases.** The user named by `postgres.connection.user` in `/etc/xroad.properties` (`postgres` by default) needs the attributes `CREATEROLE` and `CREATEDB`. It does not need to be a superuser. See [Annex D of the X-Road 8 Central Server Installation Guide](ig-cs_x-road_8_central_server_installation_guide.md#annex-d-remote-and-shared-database).

#### 3.1.2 Connection pools

Each node keeps a pool of database connections for each of its components. The table lists the largest pool of each component in the current release:

| Component on a node                                | Connections per node            |
|----------------------------------------------------|---------------------------------|
| OpenBao (the `max_parallel` default)               | up to 128                       |
| Signer                                             | 20                              |
| Admin service (the Spring Boot Hikari default)     | 10                              |
| Issuer Service, EDC pool (the EDC default)         | 8                               |
| Issuer Service, reader of the bound identities     | 1                               |

OpenBao opens up to `max_parallel` connections to its PostgreSQL storage. The shipped OpenBao configuration does not set `max_parallel`, so OpenBao uses the default of its PostgreSQL storage backend. The admin service has no own pool size setting, so it uses the default of Spring Boot.

The connection limit of the database server (`max_connections`) and of the connection pooler, if you use one, must be larger than the total of all nodes. Count the pools of every node, including OpenBao and the admin service, and the connections that you need for administration, backup and monitoring. Check the limit again each time you add a node.

#### 3.1.3 Installer checks of the database

The installation of the Central Server packages checks the database address before it creates or changes anything in the database. The checks run when you install, upgrade or reconfigure the Central Server. If a check fails, the installation stops with a message. Fix the cause and run the installation or the reconfiguration again.

| Check        | What the installer does                                                                                                                      | If the check fails                                                                                                                                                                                                                                       |
|--------------|----------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Version      | Refuses a PostgreSQL older than 15. The message is `PostgreSQL version <n> is not supported. Minimum required version is 15.`               | Use a database of version 15 or newer. If the message is `Unable to determine PostgreSQL version`, the installer could not log in to the database. Check the address, the port, the firewall and the credentials of the step that failed (see below).    |
| Write access | Refuses a database that does not accept writes. The message names the address and tells you to use the write address.                        | Use the write address of the database. If the address already is a write address, check that the database, or the role that the installer uses, does not set `default_transaction_read_only` to `on`.                                                     |
| Host list    | On a first installation, refuses an address that holds a comma, such as `db1,db2`. The message asks for one write address.                   | Give one write address, as in section [3.1.1](#311-database-requirements), and run the installation again.                                                                                                                                              |

The write check asks the database for the value of `transaction_read_only`. It therefore refuses a standby server, and it also refuses a database or a role that is set read-only.

An existing configuration can already have a host list in `spring.datasource.url` in `/etc/xroad/db.properties`. An upgrade from an old format of `db.properties` that listed secondary database hosts gets such a list. The upgrade continues, and the installer writes a warning that OpenBao uses only the first host and so does not follow a failover. The checks of an existing host list work as follows:

- The version check still refuses an unsupported PostgreSQL version.
- The write check of the Central Server step checks only the first host. If the first host does not accept writes, the check writes a warning that names the host and does not stop the installation. The check of a single-host address still refuses a database that does not accept writes.
- The secret store step and the Issuer Service step write to the first host. They still refuse a first host that does not accept writes.

Use one write address instead of a host list. You set the address at the first installation of the Central Server. As section [3.1.1](#311-database-requirements) says, the installation does not change the stored addresses later, and this guide has no procedure to change them.

The checks run in the packages in this order: the secret store (`xroad-secret-store-local`), the Central Server (`xroad-center`) and the Issuer Service (`xroad-ds-issuer-service`). A read-only address therefore shows first in the secret store step. The steps log in with these credentials:

- The secret store step runs the three checks on the first installation of a Central Server, as the database master user (`postgres.connection.user` and `postgres.connection.password` in `/etc/xroad.properties`). It does not run on a node that joins the OpenBao cluster, because that node creates no OpenBao database.
- The Central Server step runs the version check and the write check as the Central Server database role from `/etc/xroad/db.properties` when that role can log in. This is the case on a node that you prepared as in step 4 of section [4](#4-general-installation-of-ha-support). Otherwise it uses the database master user. It runs the host list check before it uses any credentials.
- The Issuer Service step runs the version check and the write check as the Central Server database admin role (`centerui.database.admin_user` and `centerui.database.admin_password` in `/etc/xroad.properties`). With wrong admin credentials it ends with `Unable to determine PostgreSQL version` after a retry of about 30 seconds.

**Preconfiguration of the packages.** The secret store step knows that it runs on a Central Server only when the questions of the Central Server package are loaded before the secret store package is configured. If they are not loaded, the secret store step skips the version check, the write check and the host list check, and it does not tell you. Do one of these on node 1:

- Install the package `apt-utils`. It is installed by default on Ubuntu Server. Then apt preconfigures all packages first, so the questions of the Central Server package exist before the secret store package is configured.
- Preseed two questions of the Central Server package before the installation: the question that decides on the database migrations (use `false` for automatic migrations) and the database address (the write address, as `<host>:<port>`):
  ```bash
  sudo debconf-set-selections <<< 'xroad-center xroad-common/skip-cs-db-migrations boolean false'
  sudo debconf-set-selections <<< 'xroad-center xroad-common/database-host string <write_address_host>:<write_address_port>'
  ```
  Preseeding only the migrations question is not enough. The question for the database address does not exist before the Central Server package is preconfigured, so the secret store step reads an empty answer. It then uses `127.0.0.1:5432`: it runs the checks against the local database and creates the OpenBao database there, not on the write address.

The variable `XROAD_IGNORE_DATABASE_SETUP` skips the Issuer Service database setup, including its checks, and the Central Server database migrations. It does not skip the checks of the Central Server step or of the secret store step.

### 3.2 Workflow for a New X-Road Instance Setup

1.  Install HA support according to steps listed in section [4](#4-general-installation-of-ha-support).
2.  Install the X-Road Central Server software according to the X-Road Central Server Installation Guide \[[IG-CS](#Ref_IG-CS)\] on the first node.
3.  Make the OpenBao of the first node reachable for the other nodes, as described in section [4.3](#43-joining-the-openbao-cluster). Then join each further node to the OpenBao cluster of the first node and install the X-Road Central Server software on it, as described in the same section.

### 3.3 Workflow for Upgrading an Existing X-Road Central Server to an HA Configuration

1.  Upgrade the existing X-Road Central Server software to the latest release available, verify system health.
2.  Create a backup of the system configuration and store it in a safe place.
3.  Continue with HA support installation steps as described in section [4](#4-general-installation-of-ha-support).
4.  Join each new node to the OpenBao cluster of the existing server, as described in section [4.3](#43-joining-the-openbao-cluster). Then install the X-Road Central Server software on it, as described in the same section and in the X-Road Central Server Installation Guide \[[IG-CS](#Ref_IG-CS)\].
5.  After installing and configuring all the X-Road Central Server nodes, retrieve new internal and external configuration anchor files from one of the nodes and distribute the files to all Security Servers and configuration proxies.

### 3.4 Workflow for Adding New Nodes to an Existing HA Configuration

Referencing steps in section [4](#4-general-installation-of-ha-support).

1.  Upgrade the software of the existing nodes to the latest version available, all nodes in one maintenance window (see section [4.2](#42-root-token-file-and-unseal-keys-on-every-node)), and verify system health on all nodes.
2.  Create a backup system configuration of the existing nodes and store it in a safe place.
3.  Prepare the node for HA configuration as described in section [4](#4-general-installation-of-ha-support).
4.  Join each new node to the OpenBao cluster of the existing nodes, as described in section [4.3](#43-joining-the-openbao-cluster). Then install the X-Road Central Server software on it, as described in the same section and in the X-Road Central Server Installation Guide \[[IG-CS](#Ref_IG-CS)\].

After installing and configuring all the X-Road Central Server nodes, retrieve new internal end external configuration anchor files from one of the nodes and distribute the files to all Security Servers and configuration proxies.

## 4 General Installation of HA Support

The HA support requires that an external database is initialized and available (see [X-Road Central Server Installation Guide](#Ref_IG-CS) about using an external database). 
The database can also be installed on the Central Server node(s), but that is not recommended. The database must meet the conditions in section [3.1.1](#311-database-requirements).

In addition, it is necessary to configure a unique node name for each node participating in the cluster. There are two equal routes: create `node.ini` before installing the Central Server (steps 1 and 2), or answer the node name question that each node's first Central Server installation asks (steps 3 and 4; preseed key `xroad-common/node-name`; you can skip steps 1 and 2, but the notes after step 2 apply to both routes). The installation asks the question only when `node.ini` has no name yet. It checks an entered name before it writes anything, and stops with an error when the name is not valid. The package database setup checks a name that is already in `node.ini` on install, upgrade and reconfigure, and stops with an error when the name is not valid. The services also check every name when they start, and a node with an invalid name does not start.

1.  On each node, create the directory `/etc/xroad/conf.d` if it does not exist, and edit the file `/etc/xroad/conf.d/node.ini` in it, creating the file if necessary. The directory does not exist before the first installation. The package sets the ownership of the directory and its files when it is installed.
    ```bash
    sudo mkdir -p /etc/xroad/conf.d
    ```
2.  Add the following lines
    ```ini
    [node]
    name=<node_name>
    ```
    Where `<node_name>` is the unique name for the HA node. It is suggested to follow a naming scheme where the first node is `node_0` and subsequent ones `node_1`, `node_2`, etc.. The name must follow the rule in section [4.1](#41-node-name-and-renaming-a-node). The services check the name when they start; a node with an invalid name does not start.

    When upgrading an existing Central Server to HA configuration, the name of the existing server *must be* `node_0`. A Central Server that is not clustered already uses `node_0` as its default node name, so its keys in the secret store are stored under that name, and its signer rows (tokens, keys, certificates and requests) carry it too. The installation and the X-Road 7 migration store its configuration rows as shared rows. Setting `name=node_0` in `node.ini` turns the server into node 1 of the cluster and moves nothing: the keys and the signer rows are already under `node_0`, and the shared rows keep applying. From then on the server reports itself as clustered. See [IG-CS section 2.6.4](ig-cs_x-road_8_central_server_installation_guide.md#264-node-name).

3.  Install the X-Road Central Server software according to the X-Road Central Server Installation Guide \[[IG-CS](#Ref_IG-CS)\] on the first node.
    
    When the installation asks for a database host, provide the write address of the external database: one host and port, not a list (see section [3.1.1](#311-database-requirements)). Prepare the packages as in section [3.1.3](#313-installer-checks-of-the-database) so that all installer checks run.

4.  On the subsequent nodes, provide the database settings of the first node before you install the Central Server. The package `xroad-base` must be installed first, because it creates the `xroad` user.
    Copy the database configuration file `/etc/xroad/db.properties` from the first node. The copy must be owned by `xroad:xroad` and have the mode `0640`. The services cannot read a copy that `root` owns.
    The Central Server package installs the Issuer Service package (`xroad-ds-issuer-service`) in the same run. It keeps its schema `ds-issuer-service` in the shared Central Server database. The Central Server database admin role creates the schema and runs its migrations. The package creates its own runtime role `ds-issuer-service` once for the shared database. The runtime role credentials are in the `/etc/xroad/db.properties` file that you copied.
    Make sure that `/etc/xroad.properties` on the subsequent node holds the password of the database master user and the Central Server database admin credentials of the first node. Create the file with the owner `root` and the mode `600` if it does not exist:
    ```properties
    postgres.connection.password=<master_password>
    centerui.database.admin_user=<admin_user>
    centerui.database.admin_password=<admin_password>
    ```
    Without the admin credentials, the package configuration on the subsequent node cannot create the Issuer Service schema. Without the master password, the database setup of the package looks for a local PostgreSQL instead of the shared database. If the first node sets `postgres.connection.user` in `/etc/xroad.properties`, set the same value on the subsequent node. Every package upgrade runs the admin database migrations with these credentials, so they stay in the file on every node. The cluster bundle in section [4.3](#43-joining-the-openbao-cluster) does not hold them. Keep a copy in your own secret store for new nodes.

**TLS certificate names.** The installation asks for the common name and the alternative names of the admin and management service TLS certificates ([IG-CS section 2.6](ig-cs_x-road_8_central_server_installation_guide.md#26-package-installation); preseed keys `xroad-common/admin-subject`, `xroad-common/admin-altsubject`, `xroad-common/service-subject` and `xroad-common/service-altsubject`). The installation detects the host name and the addresses of the node and suggests them. The suggested value is the value that the node uses now: its own row, else the shared row, else the preseeded answer, else the detected names. The answers are stored in the shared configuration database. An unchanged answer writes nothing. A changed answer is stored as a row of the node when the node has a node name, and as a shared row otherwise. Each node's certificates therefore get the names entered on that node.

A node that has a node name and no rows yet gets a node row for each name, and a node row wins over a shared row. If a shared row must apply to all nodes, set the shared rows before the first installation of the nodes, or remove the node rows later with `db_property.sh remove <key> --node <node>`. See [IG-CS section 2.6.2](ig-cs_x-road_8_central_server_installation_guide.md#262-provisioning-tls-certificates-for-management-services-and-user-interface) for the rows, the resolution order and the commands.

To use other names later, for example a load balancer name, follow the same section. The names are rows in the shared configuration database, so the rows of node 2 and later can be set from the first node before the new node is installed. After a change, delete the key of each affected node and restart the services of that node.

In a cluster, decide for each name whether it is the same on all nodes or specific to a node:

- A name that is specific to a node, for example its own host name, is a node row (`--node <node>`).
- A name that is the same on all nodes, for example a load balancer name, is a shared row.
- A key without a row of its own uses the default: the value of `XROAD_HOST`, else `localhost`, with no DNS alternative names and the IP alternative name `127.0.0.1`. The defaults apply only when no installation has stored rows, for example in container images.

To check the rows of a node, run `db_property.sh list --node <node>`.

Each node has its own management service TLS certificate. The management services' Security Server must therefore trust the certificate of every node. Add each certificate as an internal TLS certificate on that Security Server, see [UG-SS](ug-ss_x-road_6_security_server_user_guide.md) section "Managing Information System TLS Certificates".

Authentication certificate registration requests from Security Servers use the address and the management service certificate published in the configuration of the node the Security Server downloaded it from. The Security Server accepts only that exact certificate, whatever names it carries. Keep the default node-specific registration address: a registration address shared by all nodes behind a load balancer is not supported, because each node serves a different certificate.

### 4.1 Node name and renaming a node

The node name must follow this rule:

- Use 1 to 63 characters: letters, digits, `_` and `-`.
- The first character must be a letter or a digit.
- The name is read as plain text, so values such as `01`, `007` and `true` are valid.

The installation checks a name that is entered at the prompt with this rule before it writes anything, and stops with an error when the name is not valid. The package database setup checks a name that is already in `node.ini` with this rule on install, upgrade and reconfigure, and stops with an error when the name is not valid. The services also check every name with this rule when they start, and a node with an invalid name does not start. In containers, set the name with the environment variable `XROAD_NODE_NAME`.

The name is fixed after the node has joined the cluster. The node keeps its secrets in the secret store under its name, so changing the name in `node.ini` of a running node leaves the node without its secrets. To rename a node, remove the node and join it again under the new name:

1.  Take a backup of the node and of the other nodes.
2.  Stop the Central Server services on the node. Do not uninstall the packages yet. The entries of the old name must be deleted while the secret store is reachable, and the local secret store may be gone after the packages are removed.
3.  Delete the secret store entries of the old name. The commands use the OpenBao command line tool `bao`. With the local secret store, the address is `https://127.0.0.1:8200` and the token is in `/etc/openbao/root-token`; every node keeps this file (see section [4.2](#42-root-token-file-and-unseal-keys-on-every-node)). If the node has lost it, restore it from another node first. With a remote secret store, use its address and a token that can delete secrets there. If `bao` does not trust the secret store certificate, set `BAO_CACERT` to the CA certificate file. Replace `<old_node_name>` with the old name:
    ```bash
    export BAO_ADDR=<secret store address>
    export BAO_TOKEN=<token>
    bao kv list xrd-secret/signer/token-pins/<old_node_name>
    bao kv delete xrd-secret/signer/token-pins/<old_node_name>/<token_id>
    bao kv delete xrd-secret/tls/admin-service/<old_node_name>
    bao kv delete xrd-secret/tls/management-service/<old_node_name>
    ```
    Run the second command for each token ID that the first command lists. Do not delete the entries of the other nodes.
4.  Move the configuration rows of the old name to the new name. Rows set with `db_property.sh --node <old_node_name>`, for example per-node TLS certificate names, no longer apply to the node under its new name. They stay in the configuration database until they are removed. Run the commands on any Central Server node. Replace `<old_node_name>` and `<new_node_name>` with the old and the new name. List the rows of the old name:
    ```bash
    sudo /usr/share/xroad/scripts/db_property.sh list --node <old_node_name>
    ```
    Each line shows a key, a tab and the value. For each row, set the same value for the new name and then remove the row of the old name:
    ```bash
    sudo /usr/share/xroad/scripts/db_property.sh set --yes --node <new_node_name> -- <key> '<value>' && \
      sudo /usr/share/xroad/scripts/db_property.sh remove --yes --node <old_node_name> <key>
    ```
    Keep the single quotes around the value: without them, the shell changes values that contain spaces, braces, commas or quotes, such as JSON values, and `set` fails or stores a different value. Write a single quote inside the value as `'\''`. The `&&` runs `remove` only when `set` succeeded. After each row, check the new row with `sudo /usr/share/xroad/scripts/db_property.sh get --node <new_node_name> <key>`; it must print the same value as the `list` output.
    Run `list --node <old_node_name>` again to check that no row is left. Shared rows are not affected.
5.  Remove the node from the cluster: uninstall the Central Server packages from it.
6.  On the node, set the new name in `/etc/xroad/conf.d/node.ini` and install the Central Server again, as in section [4](#4-general-installation-of-ha-support).

These steps do not change the other tables of the configuration database. Rows in the `ha_node_name` column of the tables for configuration sources, distributed files and system parameters keep the old node name. So do the signer rows: tokens, keys, certificates and certificate requests. After the rename, the signer of the node no longer sees its old rows. This guide does not describe how to move or remove them.

### 4.2 Root token file and unseal keys on every node

The local secret store has a root token, kept in the file `/etc/openbao/root-token`, and unseal keys, kept in `/etc/openbao/unseal-keys`. Every node of the cluster keeps the same root token file, owned by `root:root` with the mode `600`. The file is copied to a node together with the unseal keys when the node joins the cluster. Section [4.3](#43-joining-the-openbao-cluster) describes the join steps. The unseal keys file has the same owner and mode on every node.

The secret store setup of a node runs in the unit `xroad-secret-store-local.service` at every start and at every upgrade. It waits for OpenBao, initializes it if needed, unseals it, creates the storage areas if they are missing, writes the access policy and checks the client token of the node. A node issues its own client token when it has none or its token is not valid. Never copy a client token between nodes.

Keep a copy of the unseal keys outside the cluster, in a safe place. The root token can be regenerated from the unseal keys, but not the other way round, and node backups contain neither of them.

**Copying the files.** Copy a file between nodes so that the content does not appear in the command line or the shell history. The command writes the content to a temporary file on the target node, checks that the file is not empty, sets the owner `root:root` and the mode `600`, and only then replaces the target file. If reading the file or any step on the target node fails, the existing target file is left untouched. Run the command on a node that has the file. The account on the target node needs `sudo` without a password prompt, because the file is sent through the standard input.

Root token:

```bash
sudo cat /etc/openbao/root-token | ssh <admin>@<target-node> \
  'sudo sh -c "umask 077 \
    && cat > /etc/openbao/root-token.new \
    && test -s /etc/openbao/root-token.new \
    && chown root:root /etc/openbao/root-token.new \
    && chmod 600 /etc/openbao/root-token.new \
    && mv /etc/openbao/root-token.new /etc/openbao/root-token"'
```

Unseal keys:

```bash
sudo cat /etc/openbao/unseal-keys | ssh <admin>@<target-node> \
  'sudo sh -c "umask 077 \
    && cat > /etc/openbao/unseal-keys.new \
    && test -s /etc/openbao/unseal-keys.new \
    && chown root:root /etc/openbao/unseal-keys.new \
    && chmod 600 /etc/openbao/unseal-keys.new \
    && mv /etc/openbao/unseal-keys.new /etc/openbao/unseal-keys"'
```

**A lost or rebuilt node.** A node that has lost the root token file gets it from another node, with the command above. A node that is rebuilt from scratch joins the cluster again as described in section [4.3](#43-joining-the-openbao-cluster). When the setup of a node finds no root token and no valid client token, it stops with an error that names these steps. Then:

1.  Copy the root token file from another node of the cluster.
2.  Restart the secret store setup and check that it succeeds:
    ```bash
    sudo systemctl restart xroad-secret-store-local.service
    systemctl status xroad-secret-store-local.service
    ```
    After a successful run the status is `active (exited)` and the journal of the unit (`journalctl -u xroad-secret-store-local.service`) contains `OpenBao setup completed` for the latest run. After a failure the status is `failed` and the journal shows the error.
3.  Restart the X-Road services on the node, because they read the client token only when they start:
    ```bash
    sudo systemctl restart 'xroad-*'
    ```

**Upgrade.** An upgrade restarts the secret store setup, which writes the access policy of the new release. Upgrade all nodes of the cluster in one maintenance window. A node that is not upgraded yet and restarts writes the old access policy back.

**Secret store failover.** The services retry their key-value calls to the secret store, such as secrets, token PINs and TLS credentials, for a limited time. This includes the Issuer Service. The reload of the dataspace HTTPS keystore is not covered by this time limit: after the first load, each periodic reload makes up to 3 attempts, 2 s apart, and then waits for the next periodic reload. The certificate issuing (PKI) calls make one attempt and are not retried. A call that fails after the time limit returns the error code `secret_store_unavailable`. For the Issuer Service, a read fails with this code, and a write or delete returns a failure that can be retried. See [System Parameters User Guide](ug-syspar_x-road_v6_system_parameters.md#4112-secret-store-retry-xroadcommonvault-retry).

### 4.3 Joining the OpenBao cluster

The local secret store of each Central Server node is an OpenBao that stores its data in the shared PostgreSQL database. The OpenBao instances of all nodes form one cluster on that database. One instance is active and the others are standby: a standby forwards write requests to the active instance and, by default, answers read requests itself (see "Standby reads" below). Every node keeps its services pointed at its own OpenBao on `https://127.0.0.1:8200`. A single Central Server that is not part of a cluster needs none of the steps in this section.

The commands below run on Ubuntu with the packages of the Central Server. The section [4.4](#44-using-an-external-openbao) describes the case where you run OpenBao yourself.

**Before you start.**

- The Central Server nodes share one PostgreSQL database, as described in section [4](#4-general-installation-of-ha-support). The OpenBao cluster supports PostgreSQL storage only.
- Every node has its own IPv4 address and, preferably, a DNS name that the other nodes can resolve. Use a lowercase DNS name. The name is a node address for the other nodes, not the name of a load balancer.
- The nodes can reach each other on TCP ports 8200 and 8201, see the firewall rules below.
- Node 1 is a Central Server that is already installed and running. Section [4](#4-general-installation-of-ha-support) describes how a standalone server becomes node 1 by using the node name `node_0`.

**Firewall.** Allow these connections between the Central Server nodes, in both directions, and nowhere else:

| Port     | Use                                                                  |
|----------|----------------------------------------------------------------------|
| TCP 8200 | OpenBao API                                                          |
| TCP 8201 | OpenBao cluster traffic, including requests that a standby forwards to the active instance |

Do not open these ports to Security Servers, clients or other networks. Each node must also reach the shared PostgreSQL database. The services on a node keep using the loopback listener `127.0.0.1:8200`.

**1. Make OpenBao on node 1 reachable for the other nodes.** Do this step once. Run the command on node 1. Replace `<node_address>` with the IPv4 address of the node and `<node_host>` with its DNS name:

```bash
sudo /usr/share/xroad/scripts/secret-store-cluster-config.sh --node-address <node_address> --node-host <node_host>
```

The script writes the node configuration file `/etc/xroad-secret-store/node.hcl` (owner `root:root`, mode `644`, in a directory with owner `root:root` and mode `755`). The file holds the node listener and the announced addresses, and no secrets. The base configuration file `/usr/share/xroad/openbao/xroad-openbao.hcl` and the systemd drop-in `/usr/lib/systemd/system/openbao.service.d/xroad.conf` are part of the `xroad-secret-store-local` package. OpenBao starts with the base file and the node file. Do not edit these files by hand. OpenBao then listens on `127.0.0.1:8200` and on `<node_address>:8200`. It announces `https://<node_host>:8200` (API) and `https://<node_host>:8201` (cluster traffic) to the other nodes. Without `--node-host` it announces the address. The option `--node-host` needs `--node-address`. The script creates a new TLS certificate for OpenBao when the names in the certificate change, installs the certificate as trusted, and restarts OpenBao if it is running. If the installed certificate is not the one the script generated, the script stops with an error that names the file. Delete the file and run the script again. Running the script again with the same options and an unchanged certificate changes nothing.

The script prints `Generating OpenBao TLS certificates for X-Road...` when it creates a new certificate, and `Updating OpenBao certificate trust...` when it installs the certificate as trusted. The X-Road services read the trusted certificates only when they start. After either line, restart the X-Road services of node 1:

```bash
sudo systemctl restart xroad-base
```

All X-Road services depend on `xroad-base`, so the restart stops all of them and starts them again. Node 1 does not serve requests in the meantime, so choose a quiet time. If the script prints neither line, the certificate and its trust did not change and no restart is needed.

To turn off standby reads, add `--disable-standby-reads` to the command. See the paragraph "Standby reads" below. Use the same options every time you run the command: a run without `--disable-standby-reads` turns standby reads on again. A run without `--node-address` on a node that has the node file stops with an error and changes nothing, so a node does not leave the cluster by mistake. To make a node a single node again, run `sudo /usr/share/xroad/scripts/secret-store-cluster-config.sh --single-node`. The script removes the node file and restarts OpenBao if it is running. The option `--single-node` cannot be used with `--node-address`, `--node-host` or `--disable-standby-reads`.

Do steps 2 to 4 for each new node, one node after the other.

**2. Export the cluster bundle on node 1.** The cluster bundle is a file that holds what a new node needs to use the existing OpenBao: the OpenBao database connection (`xroad-openbao.env`), the unseal keys and the root token. It does not hold the database admin credentials and no client token. The target file must not exist, because the command refuses to overwrite it. Step 4 deletes the archive after the import. The directory `/run` is on a memory-backed file system, so the file is gone after a reboot:

```bash
sudo /usr/share/xroad/scripts/secret-store-cluster-bundle.sh export /run/xroad-openbao-join.tar
```

**3. Copy the bundle to the new node.** The archive holds secrets: treat it like the root token file. How you move the file is your choice, for example a secure file transfer tool or the `ssh` command below. The requirements are:

- Only `root` can read the file, on both nodes.
- The content does not appear in a command line or the shell history.
- The file is not stored in a backup, a shared folder or a ticket.

This command, run on node 1, writes the file with the mode `600` on the new node and checks that it is not empty. It sends the file on the standard input, so it needs `sudo` without a password prompt on the new node:

```bash
sudo cat /run/xroad-openbao-join.tar | ssh <admin>@<new-node> \
  'sudo sh -c "umask 077 \
    && cat > /run/xroad-openbao-join.tar \
    && test -s /run/xroad-openbao-join.tar"'
```

**4. Prepare the new node and install the Central Server.** Do the items in this order. Install the Central Server last, because its installation reads the bundle and the answers.

1.  Install the packages `xroad-base` and `xroad-database-remote`. The cluster bundle script is part of `xroad-base`. The package `xroad-database-remote` makes the node use the shared database: without it, the installation of the Central Server selects the local database package. The package repositories are those of section [2.5 of the X-Road 8 Central Server installation guide](ig-cs_x-road_8_central_server_installation_guide.md#25-setup-package-repositories):
    ```bash
    sudo apt install xroad-base xroad-database-remote
    ```
2.  Set a unique node name. Use `node.ini` as in items 1 and 2 of the list in section [4](#4-general-installation-of-ha-support), or preseed the answer:
    ```bash
    sudo debconf-set-selections <<< 'xroad-center xroad-common/node-name string <node_name>'
    ```
    Do not use `node_0`: node 1 uses it. A node without a name gets `node_0`, and then the node-specific secrets of the two nodes collide.
3.  Provide the database settings of the first node, as in item 4 of the list in section [4](#4-general-installation-of-ha-support). The cluster bundle does not hold them.
4.  Check whether the node already has the bundle files:
    ```bash
    sudo /usr/share/xroad/scripts/secret-store-cluster-bundle.sh check
    ```
    On a new node the command lists all three files as missing or empty and exits with a non-zero code. This is the expected result: continue with the import. If it lists only some of the files, the node holds the other files, and the import refuses to run. On a node without an installed Central Server, delete the files that exist from `/etc/openbao` and continue. If the command succeeds, the node already has all three files and the import would refuse to overwrite them.
5.  Import the bundle:
    ```bash
    sudo /usr/share/xroad/scripts/secret-store-cluster-bundle.sh import /run/xroad-openbao-join.tar
    ```
    The files `xroad-openbao.env`, `unseal-keys` and `root-token` go to `/etc/openbao`. The import refuses to overwrite any of these files; if one exists, it imports nothing.
6.  Delete the archive. Run the command on the new node and on node 1:
    ```bash
    sudo rm /run/xroad-openbao-join.tar
    ```
7.  Answer the join questions before the installation. Replace `<node_address>` with the IPv4 address of the new node:
    ```bash
    sudo debconf-set-selections <<< 'xroad-center xroad-common/secret-store-join boolean true'
    sudo debconf-set-selections <<< 'xroad-center xroad-common/secret-store-node-address string <node_address>'
    ```
    Both answers are required. If the node address is empty, the installation fails with the message `Joining the OpenBao cluster requires a node address`. The next two answers are optional, and the installation uses them only together with the node address. Replace `<node_host>` with the DNS name of the new node. Set the standby reads answer to turn off standby reads on this node:
    ```bash
    sudo debconf-set-selections <<< 'xroad-center xroad-common/secret-store-node-host string <node_host>'
    sudo debconf-set-selections <<< 'xroad-center xroad-common/secret-store-disable-standby-reads boolean true'
    ```
    The questions are asked at the first installation only.
8.  Install the Central Server:
    ```bash
    sudo apt install xroad-centralserver
    ```
    The installation asks for the database host: enter the write address of the shared database, the same as on node 1. On a node with a join answer, the installation uses the imported OpenBao settings and creates no OpenBao database. It writes the OpenBao configuration with the answers, starts OpenBao and joins the existing cluster. It never initializes a new OpenBao. The installation of the Central Server then continues as in the Central Server installation guide \[[IG-CS](#Ref_IG-CS)\].

**5. Check the result.** On each node:

```bash
sudo systemctl status openbao
curl -s -k https://127.0.0.1:8200/v1/sys/leader
curl -s -k https://127.0.0.1:8200/v1/sys/seal-status
journalctl -u xroad-secret-store-local.service
```

The response of `sys/leader` shows `"ha_enabled":true`, `"is_self"` (true on the active instance only) and the `leader_address`. The seal status shows `"sealed":false`. The journal contains `OpenBao setup completed` for the latest run. Each node has its own client token in `/etc/xroad/secret-store-client-token`.

**Limits.** The join answers and the install-time join guard exist in the Debian and Ubuntu packages only. The guard is the run of `secret-store-init.sh --join` in the installation: it fails when the bundle files are missing or the OpenBao is not initialized, and it never initializes a new OpenBao. The `%post` script of the Red Hat package `xroad-secret-store-local` has no join answers and does not run the guard. This guide does not describe a join on Red Hat.

**Files on every node.** The file `/etc/openbao/xroad-openbao.env` has the mode `640` and is owned by `openbao:xroad`. Section [4.2](#42-root-token-file-and-unseal-keys-on-every-node) gives the owner and mode of the root token file and the unseal keys file.

**Standby reads.** By default, a standby OpenBao instance answers read requests itself. If PostgreSQL uses logical replication, turn standby reads off, so that no node reads a stale secret. Do it on every node, with `--disable-standby-reads` (step 1) or with the answer `xroad-common/secret-store-disable-standby-reads` (item 7 of step 4), and use the same choice on all nodes.

**Restart and failover.** OpenBao unseals itself every time it starts, by running `/usr/share/xroad/scripts/secret-store-unseal.sh` after the start. If the database is not available, the script logs this and ends without failing the unit; the next start of OpenBao tries again. Look in the journal with `journalctl -u openbao`. systemd restarts OpenBao 5 seconds after a failure. When the active node fails, a standby takes over after about 15 seconds. A restarted node is sealed until the unseal script has run.

### 4.4 Using an external OpenBao

You can run OpenBao yourself instead of using the local secret store of the Central Server nodes. You set up and operate that OpenBao: X-Road provides no script that creates it, and does not initialize, unseal, back up or cluster it. X-Road uses the settings that the local secret store scripts create, so create the same ones. The reference is the script `/usr/share/xroad/scripts/_openbao.sh` of the package `xroad-secret-store-local` (functions `configure_kv`, `configure_pki`, `configure_policy` and `create_token`). The package `xroad-secret-store-remote` replaces `xroad-secret-store-local` on a node that uses a remote OpenBao. This section lists what X-Road expects of the OpenBao; it does not describe the installation steps of a Central Server with a remote OpenBao.

**Secrets engines.** Create these mounts:

| Mount           | Type   | Settings                           |
|-----------------|--------|------------------------------------|
| `xrd-secret`    | `kv`   | Key-value, version 1               |
| `xrd-ds-secret` | `kv-v2`| Key-value, version 2               |
| `xrd-pki`       | `pki`  | Maximum lease TTL `175200h`        |

On `xrd-pki`, the local setup does the following:

- generates an internal root CA with the common name `localhost` and the TTL `175200h`;
- sets `issuing_certificates` to `https://127.0.0.1:8200/v1/xrd-pki/ca` and `crl_distribution_points` to `https://127.0.0.1:8200/v1/xrd-pki/crl`; set them to the address of your OpenBao;
- turns on automatic tidying every `12h` (`tidy_cert_store` and `tidy_revoked_certs` true);
- creates the role `xrd-internal` with `allow_any_name`, `allow_subdomains`, `allow_localhost` and `allow_ip_sans` true and the maximum TTL `87600h`.

**Policy.** Create the policy `xroad-policy` with this content. The function `configure_policy` in the script is the reference when the two differ:

```hcl
path "xrd-pki/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}
path "xrd-secret/*" {
  capabilities = ["read", "list"]
}
path "xrd-secret/tls/*" {
  capabilities = ["read", "list", "create", "update"]
}
path "xrd-secret/message-log/archival/pgp/*" {
  capabilities = ["read", "list", "create", "update"]
}
path "xrd-secret/message-log/database-encryption/keys/*" {
  capabilities = ["read", "list", "create", "update"]
}
path "xrd-secret/signer/token-pins/*" {
  capabilities = ["read", "list", "create", "update", "delete"]
}
path "xrd-secret/acme/account-keys/*" {
  capabilities = ["read", "list", "create", "update"]
}
path "xrd-secret/agreement-token/signing-keys/*" {
  capabilities = ["read", "list", "create", "update", "delete"]
}
path "xrd-secret" {
  capabilities = ["list"]
}
path "xrd-ds-secret/data/ds/*" {
  capabilities = ["read", "list", "create", "update"]
}
path "xrd-ds-secret/metadata/ds/*" {
  capabilities = ["read", "list", "delete"]
}
path "xrd-ds-secret/delete/ds/*" {
  capabilities = ["update"]
}
path "xrd-ds-secret" {
  capabilities = ["list"]
}
path "sys/internal/ui/mounts/*" {
  capabilities = ["read", "list"]
}
```

**Paths of a node.** Each Central Server node stores its own entries under its node name (`<node_name>`, for example `node_0` and `node_1`; see section [4.1](#41-node-name-and-renaming-a-node)):

- `xrd-secret/tls/admin-service/<node_name>`
- `xrd-secret/tls/management-service/<node_name>`
- `xrd-secret/signer/token-pins/<node_name>/<token_id>`

The policy gives each token access to the whole subtree of these paths. The services use only the paths of their own node name; OpenBao does not enforce this.

**Token.** Create a client token for each node, with the policy `xroad-policy`. The local setup creates a periodic, renewable token with the period `768h` and the display name `xroad-client`. Do not share one token between nodes. The services renew their token at the interval `xroad.common.vault-token-renewal.interval` (24 hours by default), so the token period must be longer than the interval. A token that cannot be renewed produces a warning, see the [System Parameters User Guide](ug-syspar_x-road_v6_system_parameters.md#4112-secret-store-retry-xroadcommonvault-retry).

**Values that each node needs.** A node reaches the OpenBao with these values:

| Value                  | Variable                  | Note                                              |
|------------------------|---------------------------|---------------------------------------------------|
| Scheme                 | `XROAD_SECRET_STORE_SCHEME` | `https`, the default of the local setup          |
| Host                   | `XROAD_SECRET_STORE_HOST` | Address that all nodes can reach                  |
| Port                   | `XROAD_SECRET_STORE_PORT` | `8200` in the local setup                         |
| Token                  | `XROAD_SECRET_STORE_TOKEN`| The client token of this node                     |

The local setup sets these in `/etc/xroad/services/secret-store-local.conf` and reads the token from `/etc/xroad/secret-store-client-token` (owner `xroad:xroad`, mode `640`). The services must trust the CA that issued the certificate of the OpenBao: add it to the system trust store and the Java trust store of the node. The local setup does this with `/usr/share/xroad/scripts/secret-store-install-tls-trust.sh`. The Security Server user guide lists the same requirements for the signing keys \[[UG-SS](ug-ss_x-road_6_security_server_user_guide.md#265-secret-store-requirements)\].

How these values reach the services of a native package node that uses the package `xroad-secret-store-remote` is not described in this guide.

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
* Set up a new database that the same write address leads to. The write address is set at the first installation and is stored in more than one place (see section [3.1.1](#311-database-requirements)), so move the stable name or the virtual IP address to the new database. Do not edit only `db.properties` to point to another address.
* On one node, restore the database from a backup (see \[[UG-CS](#Ref_UG-CS)\]).
* Start Central Servers.

### 6.2 One or more cental server nodes lost, backup available

* Setup the missing nodes like described in [3.4](#34-workflow-for-adding-new-nodes-to-an-existing-ha-configuration).
* Restore configuration from a backup, skipping database restoration (see \[[UG-CS](#Ref_UG-CS)\]).

### 6.3 Some Central Server nodes lost, backup not available

See [3.4 Workflow for Adding New Nodes to an Existing HA Configuration](#34-workflow-for-adding-new-nodes-to-an-existing-ha-configuration).

## Appendix A. Setting up a replicated PostgreSQL database

In this configuration, the Central Server nodes share an external PostgreSQL database that uses a master - standby configuration with streaming replication. In case of master failure, the standby is promoted to master, and the write address (see section [3.1.1](#311-database-requirements)) must then lead to the new master. The promoting is manual by default, but can be automated by custom scripts or using third-party tools. Because the database standby is read-only and the Central Server nodes require write access, this setup does not provide horizontal scalability.

It is assumed that a PostgreSQL database is installed on two or more similar hosts. Use PostgreSQL 15 or newer. One of these hosts will be configured as a master, and others will replicate the master database (standby). The master node can have an existing database, but the standby databases will be initialized during the installation.

A thorough discussion about PostgreSQL replication and high-availability is out of the scope of this guide. For more details, see e.g. [PostgreSQL documentation about high-availability](https://www.postgresql.org/docs/current/high-availability.html).

### Prerequisites

* Same version (15 or newer) of PostgreSQL installed on at least two similar hosts.
* A write address that leads to the master (see section [3.1.1](#311-database-requirements)): for example a virtual IP address or a connection pooler that you move to the new master at a failover.
* Network connections on PostgreSQL port (tcp/5432) are allowed between database servers (both directions).
* Network connections to PostgreSQL port (tcp/5432) are allowed from the Central Servers to the database servers.

### PostgreSQL configuration (all database servers)

Edit `postgresql.conf` and verify the following settings:
* Ubuntu: `/etc/postgresql/<version>/<cluster name>/postgresql.conf`
* RHEL: In data directory, usually `/var/lib/pgsql/data`

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
  * Edit `postgresql.conf` and create the file `standby.signal` in the data directory (https://www.postgresql.org/docs/current/recovery-config.html). `pg_basebackup` with the option `-R` creates both. Verify the settings:
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

Set the write address of the database at the first installation of the Central Server: give it as the database server URL of the installation (see section [3.1.3](#313-installer-checks-of-the-database) for the preconfiguration). The write address must be a stable name or a virtual IP address that leads to the primary server and follows a failover (see section [3.1.1](#311-database-requirements)). Use one host. Do not use a list of hosts.

The installation stores the address in `/etc/xroad/db.properties` (the Central Server and the Issuer Service) and in `/etc/openbao/xroad-openbao.env` (OpenBao). To move a node to another database address, do not change only the Central Server key in `db.properties`: the Issuer Service keeps its own copy of the address in other keys of `db.properties`, and OpenBao keeps its copy in `xroad-openbao.env`. This guide has no procedure for a change of the address. After the installation, a failover changes only where the stable name or the virtual IP address leads.

Verify that the cluster is working (see [5 Monitoring HA State on a Node](#5-monitoring-ha-state-on-a-node)).

### Fail-over

In case the master server fails, one can manually promote the standby to a master by executing the following command on the standby server:
```bash
sudo pg_ctl promote
```

On Ubuntu pg_ctl is typically not in the path, use pg_ctlcluster instead:
```bash
sudo pg_ctlcluster <major version> <cluster name> promote
# e.g. sudo pg_ctlcluster 15 main promote
```

After the promotion, move the write address to the new master (for example the virtual IP address or the connection pooler). The Central Servers do not choose a new database host themselves. Restart the Central Server services on every node if they do not reconnect.
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
