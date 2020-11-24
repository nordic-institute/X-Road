
| ![European Union / European Regional Development Fund / Investing in your future](img/eu_rdf_75_en.png "Documents that are tagged with EU/SF logos must keep the logos until 1.1.2022, if it has not stated otherwise in the documentation. If new documentation is created  using EU/SF resources the logos must be tagged appropriately so that the deadline for logos could be found.") |
| -------------------------: |

# Security Server Installation Guide for Red Hat Enterprise Linux <!-- omit in toc -->

**X-ROAD 6**

Version: 1.13  
Doc. ID: IG-SS-RHEL

---


## Version history <!-- omit in toc -->

 Date       | Version | Description                                                     | Author
 ---------- | ------- | --------------------------------------------------------------- | --------------------
 25.10.2018 | 1.0     | Initial version                                                 | Petteri Kivimäki
 16.11.2018 | 1.1     | Update link to Ubuntu installation guide                        | Jarkko Hyöty
 28.01.2018 | 1.2     | Update port 2080 documentation                                  | Petteri Kivimäki
 11.09.2019 | 1.3     | Remove Ubuntu 14.04 from supported platforms                    | Jarkko Hyöty
 12.09.2019 | 1.4     | Add instruction for remote database usage                       | Ilkka Seppälä
 10.10.2019 | 1.5     | Add instructions for binding xroad-proxy to ports 80,443        | Jarkko Hyöty
 30.04.2020 | 1.6     | Add instructions how to use remote database located in Microsoft Azure        | Ilkka Seppälä
 12.06.2020 | 1.7     | Update reference data regarding JMX listening ports | Petteri Kivimäki
 24.06.2020 | 1.8     | Add repository sign key details in section [2.2 Reference data](#22-reference-data) | Petteri Kivimäki
 24.06.2020 | 1.9     | Remove environmental and operational monitoring daemon JMX listening ports from section [2.2 Reference data](#22-reference-data) | Petteri Kivimäki
 09.08.2020 | 1.10    | Update ports information in section [2.2 Reference data](#22-reference-data), add section [2.2.1 Network Diagram](#221-network-diagram) | Petteri Kivimäki
 17.08.2020 | 1.11    | Update for RHEL 8. Document id and name changed.                | Jarkko Hyöty
 16.09.2020 | 1.12    | Describe deployment options and database customization options. | Ilkka Seppälä
 29.09.2020 | 1.13    | Add instructions for creating database structure and roles manually. | Ilkka Seppälä


## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/


## Table of Contents <!-- omit in toc -->

<!-- toc -->
<!-- vim-markdown-toc GFM -->

- [License](#license)
- [1 Introduction](#1-introduction)
  - [1.1 Target Audience](#11-target-audience)
  - [1.2 Terms and abbreviations](#12-terms-and-abbreviations)
  - [1.3 References](#13-references)
- [2 Installation](#2-installation)
  - [2.1 Prerequisites to Installation](#21-prerequisites-to-installation)
  - [2.2 Reference Data](#22-reference-data)
    - [2.2.1 Network Diagram](#221-network-diagram)
  - [2.3 Requirements for the Security Server](#23-requirements-for-the-security-server)
  - [2.4 Preparing OS](#24-preparing-os)
  - [2.5 Prepare for Installation](#25-prepare-for-installation)
    - [2.5.1 Customize the Database Properties](#251-customize-the-database-properties)
  - [2.6 Remote Database Installation](#26-remote-database-installation)
  - [2.7 Setup Package Repository](#27-setup-package-repository)
  - [2.8 Package Installation](#28-package-installation)
    - [2.8.1 Configure Proxy Ports](#281-configure-proxy-ports)
    - [2.8.2 Start Security Server](#282-start-security-server)
  - [2.9 Post-Installation Checks](#29-post-installation-checks)
  - [2.10 Installing the Support for Hardware Tokens](#210-installing-the-support-for-hardware-tokens)
  - [2.11 Installing the Support for Environmental Monitoring](#211-installing-the-support-for-environmental-monitoring)
  - [2.12 Remote Database Post-Installation Tasks](#212-remote-database-post-installation-tasks)
- [3 Security Server Initial Configuration](#3-security-server-initial-configuration)
  - [3.1 Prerequisites](#31-prerequisites)
  - [3.2 Reference Data](#32-reference-data)
  - [3.3 Configuration](#33-configuration)
- [Annex A Security Server Default Database Properties](#annex-a-security-server-default-database-properties)
- [Annex B Database Users](#annex-b-database-users)
- [Annex C Deployment Options](#annex-c-deployment-options)
  - [C.1 General](#c1-general)
  - [C.2 Local Database](#c2-local-database)
  - [C.3 Remote Database](#c3-remote-database)
  - [C.4 High Availability Setup](#c4-high-availability-setup)
  - [C.5 Load Balancing Setup](#c5-load-balancing-setup)
  - [C.6 Summary](#c6-summary)
- [Annex D Create Database Structure Manually](#annex-d-create-database-structure-manually)

<!-- vim-markdown-toc -->
<!-- tocstop -->


## 1 Introduction


### 1.1 Target Audience

The intended audience of this Installation Guide are X-Road Security server system administrators responsible for installing and using X-Road software. The daily operation and maintenance of the security server is covered by its User Guide \[[UG-SS](#Ref_UG-SS)\].

The document is intended for readers with a moderate knowledge of Linux server management, computer networks, and the X-Road working principles.


### 1.2 Terms and abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\].

### 1.3 References

1.  <a id="Ref_UG-SS" class="anchor"></a>\[UG-SS\] X-Road 6. Security Server User Guide. Document ID: [UG-SS](ug-ss_x-road_6_security_server_user_guide.md)

2.  <a id="Ref_TERMS" class="anchor"></a>\[TA-TERMS\] X-Road Terms and Abbreviations. Document ID: [TA-TERMS](../terms_x-road_docs.md).

3.  <a name="Ref_IG-XLB" class="anchor"></a>\[IG-XLB\] X-Road: External Load Balancer Installation Guide. Document ID: [IG-XLB](LoadBalancing/ig-xlb_x-road_external_load_balancer_installation_guide.md).


## 2 Installation


### 2.1 Prerequisites to Installation

There are multiple alternatives how the security server can be deployed. The options are described in [Annex C Deployment Options](#annex-c-deployment-options).

The security server runs on the following platforms:

* Red Hat Enterprise Linux (RHEL) versions 7 and 8 on a x86-64 platform.
* Ubuntu Server 18.04 on a x86-64 platform. See [IG-SS](ig-ss_x-road_v6_security_server_installation_guide.md) for more information.

The software can be installed both on physical and virtualized hardware (of the latter, Xen and Oracle VirtualBox have been tested).


### 2.2 Reference Data

*Note*: The information in empty cells should be determined before the server’s installation, by the person performing the installation.

**Caution**: Data necessary for the functioning of the operating system is not included.


| Ref     |                           | Explanation |
| ------- | --------------------------| ---------------------------------------------------------- |
| 1.0     | RHEL (7.3 or newer; 8.0 or newer), x86-64 CPU, 4 GB RAM, 10 GB free disk space | Minimum requirements
| 1.1     | https://artifactory.niis.org/xroad-release-rpm  | X-Road package repository
| 1.2     | https://artifactory.niis.org/api/gpg/key/public | The repository key.<br /><br />Hash: `935CC5E7FA5397B171749F80D6E3973B`<br  />Fingerprint: `A01B FE41 B9D8 EAF4 872F  A3F1 FB0D 532C 10F6 EC5B`<br  />3rd party key server: [SKS key servers](http://pool.sks-keyservers.net/pks/lookup?op=vindex&hash=on&fingerprint=on&search=0xFB0D532C10F6EC5B)
| 1.3     |                                         | Account name in the user interface
| 1.4     | **Inbound ports from external network** | Ports for inbound connections from the external network to the security server
|         | TCP 5500                                | Message exchange between security servers
|         | TCP 5577                                | Querying of OCSP responses between security servers
| 1.5     | **Outbound ports to external network**  | Ports for outbound connections from the security server to the external network
|         | TCP 5500                                | Message exchange between security servers
|         | TCP 5577                                | Querying of OCSP responses between security servers
|         | TCP 4001                                | Communication with the central server
|         | TCP 80                                  | Downloading global configuration from the central server
|         | TCP 80,443                              | Most common OCSP and time-stamping services
| 1.6     | **Inbound ports from internal network** | Ports for inbound connections from the internal network to the security server
|         | TCP 4000                                | User interface and management REST API (local network). **Must not be accessible from the internet!**
|         | TCP 8080 (or TCP 80), 8443 (or TCP 443) | Information system access points (in the local network). **Must not be accessible from the external network without strong authentication. If open to the external network, IP filtering is strongly recommended.**
| 1.7     | **Outbound ports to internal network**  | Ports for inbound connections from the internal network to the security server
|         | TCP 80, 443, *other*                    | Producer information system endpoints
|         | TCP 2080                                | Message exchange between security server and operational data monitoring daemon (by default on localhost)
| 1.8     |                                         | Security server internal IP address(es) and hostname(s)
| 1.9     |                                         | Security server public IP address, NAT address

It is strongly recommended to protect the security server from unwanted access using a firewall (hardware or software based). The firewall can be applied to both incoming and outgoing connections depending on the security requirements of the environment where the security server is deployed. It is recommended to allow incoming traffic to specific ports only from explicitly defined sources using IP filtering. **Special attention should be paid with the firewall configuration since incorrect configuration may leave the security server vulnerable to exploits and attacks.**


#### 2.2.1 Network Diagram

The network diagram below provides an example of a basic Security Server setup. Allowing incoming connections from the Monitoring Security Server on ports 5500/tcp and 5577/tcp is necessary for the X-Road Operator to be able to monitor the ecosystem and provide statistics and support for Members.

![network diagram](img/ig-ss_network_diagram_RHEL.png)

The table below lists the required connections between different components.

| Connection Type | Source | Target | Target Ports | Protocol | Note |
|-----------|------------|-----------|-----------|-----------|-----------|
| Out | Security Server | Central Server | 80, 4001 | tcp | |
| Out | Security Server | Management Security Server | 5500, 5577 | tcp | |
| Out | Security Server | OCSP Service | 80 / 443 | tcp | |
| Out | Security Server | Timestamping Service | 80 / 443 | tcp | |
| Out | Security Server | Data Exchange Partner Security Server (Service Producer) | 5500, 5577 | tcp | |
| Out | Security Server | Producer Information System | 80, 443, other | tcp | Target in the internal network |
| In  | Monitoring Security Server | Security Server | 5500, 5577 | tcp | |
| In  | Data Exchange Partner Security Server (Service Consumer) | Security Server | 5500, 5577 | tcp | |
| In | Consumer Information System | Security Server | 8080, 8443 | tcp | Source in the internal network |
| In | Admin | Security Server | 4000 | tcp | Source in the internal network |


### 2.3 Requirements for the Security Server

Minimum recommended hardware parameters:

* the server’s hardware (motherboard, CPU, network interface cards, storage system) must be supported by RHEL in general;
* a x86-64 dual-core Intel, AMD or compatible CPU; AES instruction set support is highly recommended;
* 2 CPU;
* 4 GB RAM;
* 10 GB free disk space (OS partition), 20-40 GB free disk space (`/var` partition);
* a 100 Mbps network interface card.

Requirements to software and settings:

* an installed and configured RHEL (v7.3 or newer) x86-64 operating system;
* if the security server is separated from other networks by a firewall and/or NAT, the necessary connections to and from the security server are allowed (**reference data: 1.4; 1.5; 1.6; 1.7**). The enabling of auxiliary services which are necessary for the functioning and management of the operating system (such as DNS, NTP, and SSH) stay outside the scope of this guide;
* if the security server has a private IP address, a corresponding NAT record must be created in the firewall (**reference data: 1.9**).


### 2.4 Preparing OS

* Set the operating system locale. Add following line to the `/etc/environment` file.

        LC_ALL=en_US.UTF-8

* Install `yum-utils`, a collection of utilities that integrate with yum to extend its native features.

        sudo yum install yum-utils


### 2.5 Prepare for Installation

The database users required by security server are listed in [Annex B Database Users](#annex-b-database-users). The database properties created by the default installation can be found at [Annex A Security Server Default Database Properties](#annex-a-security-server-default-database-properties). If necessary, it's possible to customize the database names, users, passwords etc. by following the steps in [2.5.1 Customize the Database Properties](#251-customize-the-database-properties).


#### 2.5.1 Customize the Database Properties

**This is an optional step.** Security server uses `/etc/xroad/db.properties` file to store the database properties. It's possible to customize the installation by precreating this file before running the installer. First create the `xroad` user, directory and the file as follows:

  ```
  sudo useradd --system --home /var/lib/xroad --no-create-home --shell /bin/bash --user-group --comment "X-Road system user" xroad
  sudo mkdir /etc/xroad
  sudo chown xroad:xroad /etc/xroad
  sudo chmod 751 /etc/xroad
  sudo touch /etc/xroad/db.properties
  sudo chown xroad:xroad /etc/xroad/db.properties
  sudo chmod 640 /etc/xroad/db.properties
  ```

Then edit `/etc/xroad/db.properties` contents. See the template below. Replace the parameter values with your own. The default values can be found in [Annex A Security Server Default Database Properties](#annex-a-security-server-default-database-properties). Note that you only need to define the properties that need to be customized, elsewhere the defaults apply. The database names can be changed by modifying the `<database>.hibernate.connection.url` property e.g. `serverconf.hibernate.connection.url = jdbc:postgresql://<host:port>/custom`.

  ```
  serverconf.hibernate.connection.url = jdbc:postgresql://<host:port>/serverconf
  serverconf.hibernate.hikari.dataSource.currentSchema = serverconf,public
  serverconf.hibernate.connection.username = <serverconf username>
  serverconf.hibernate.connection.password = <serverconf password>
  messagelog.hibernate.connection.url = jdbc:postgresql://<host:port>/messagelog
  messagelog.hibernate.hikari.dataSource.currentSchema = messagelog,public
  messagelog.hibernate.connection.username = <messagelog username>
  messagelog.hibernate.connection.password = <messagelog password>
  op-monitor.hibernate.connection.url = jdbc:postgresql://<host:port>/op-monitor
  op-monitor.hibernate.hikari.dataSource.currentSchema = opmonitor,public
  op-monitor.hibernate.connection.username = <opmonitor username>
  op-monitor.hibernate.connection.password = <opmonitor password>
  ```


### 2.6 Remote Database Installation

**This is an optional step.** If you want to use remote database server instead of the default locally installed one, you need to pre-create a configuration file containing at least the database administrator master password. If storing the database administrator password on the security server is not possible due to security risk or other problem, the alternative is to create the database structure manually as described in [Annex D Create Database Structure Manually](#annex-d-create-database-structure-manually). Otherwise, creating the configuration file can be done by performing the following steps:

  ```
  sudo touch /etc/xroad.properties
  sudo chown root:root /etc/xroad.properties
  sudo chmod 600 /etc/xroad.properties
  ```

  Edit `/etc/xroad.properties` contents. See the example below. Replace parameter values with your own.

  ```
  postgres.connection.password = <database superuser password>
  postgres.connection.user = <database superuser name, postgres by default>
  ```

* If Microsoft Azure database for PostgreSQL is used, the connection user needs to be in format `username@servername`.
* One should verify that the version of the local PostgreSQL client matches the version of the remote PostgreSQL server.

**This is an optional step.** If you want to customize the names and/or passwords of the database admin users created by the installer, follow these steps. By default the installer generates these users with format `<database-name>_admin` and autogenerates the password. Edit `/etc/xroad.properties` contents. See the example below. Replace parameter values with your own.

  ```
  serverconf.database.admin_user = <serverconf-admin-username>
  serverconf.database.admin_password = <serverconf-admin-password>
  op-monitor.database.admin_user = <op-monitor-admin-username>
  op-monitor.database.admin_password = <op-monitor-admin-password>
  messagelog.database.admin_user = <messagelog-admin-username>
  messagelog.database.admin_password = <messagelog-admin-password>
  ```


### 2.7 Setup Package Repository

Add X-Road package repository (**reference data: 1.1**) and Extra Packages for Enterprise Linux (EPEL) repository:

  ```
  RHEL_MAJOR_VERSION=$(source /etc/os-release;echo ${VERSION_ID%.*})
  sudo yum install https://dl.fedoraproject.org/pub/epel/epel-release-latest-${RHEL_MAJOR_VERSION}.noarch.rpm
  sudo yum-config-manager --add-repo https://artifactory.niis.org/xroad-release-rpm/rhel/${RHEL_MAJOR_VERSION}/current
  ```

The following packages are fetched from EPEL: `crudini`, and `rlwrap`.

Add the X-Road repository’s signing key to the list of trusted keys (**reference data: 1.2**):

  ```
  sudo rpm --import https://artifactory.niis.org/api/gpg/key/public
  ```


### 2.8 Package Installation

Issue the following command to install the security server packages (use package `xroad-securityserver-ee` to include configuration specific to Estonia; use package `xroad-securityserver-fi` to include configuration specific to Finland):

  ```
  sudo yum install xroad-securityserver
  ```

Add system user (**reference data: 1.3**) whom all roles in the user interface are granted to. Add a new user with the command

  ```
  sudo xroad-add-admin-user <username>
  ```

User roles are discussed in detail in X-Road Security Server User Guide \[[UG-SS](#Ref_UG-SS)\].


#### 2.8.1 Configure Proxy Ports

**This is an optional step.** Change `xroad-proxy` to use ports 80 and 443.

By default, `xroad-proxy` listens for consumer information system connections on ports 8080 (HTTP) and 8443 (HTTPS). To use standard HTTP(S) ports 80 and 443, verify that the ports are free, and make the following modifications:

Edit `/etc/xroad/conf.d/local.ini` and add the following properties in the `[proxy]` section:

  ```
  [proxy]
  client-http-port=80
  client-https-port=443
  ```


#### 2.8.2 Start Security Server

Once the installation is completed, start the security server

  ```
  sudo systemctl start xroad-proxy
  ```


### 2.9 Post-Installation Checks

The installation is successful if system services are started and the user interface is responding.

* Ensure from the command line that X-Road services are in the `running` state (example output follows):
  
  ```
  sudo systemctl list-units "xroad-*"

  UNIT                       LOAD   ACTIVE SUB     DESCRIPTION
  xroad-confclient.service   loaded active running X-Road confclient
  xroad-monitor.service      loaded active running X-Road Monitor
  xroad-opmonitor.service    loaded active running X-Road opmonitor daemon
  xroad-proxy-ui-api.service loaded active running X-Road Proxy UI REST API
  xroad-proxy.service        loaded active running X-Road Proxy
  xroad-signer.service       loaded active running X-Road signer
  ```

* Ensure that the security server user interface at https://SECURITYSERVER:4000/ (**reference data: 1.8; 1.6**) can be opened in a Web browser. To log in, use the account name chosen during the installation (**reference data: 1.3**). While the user interface is still starting up, the Web browser may display a connection refused -error.


### 2.10 Installing the Support for Hardware Tokens

Hardware security tokens (smartcard, USB token, Hardware Security Module) have not been tested on RHEL. Therefore, support is not provided.


### 2.11 Installing the Support for Environmental Monitoring

The support for environmental monitoring functionality on a security server is provided by package xroad-monitor that is installed by default. The package installs and starts the `xroad-monitor` process that will gather and make available the monitoring information.


### 2.12 Remote Database Post-Installation Tasks

Local PostgreSQL is always installed with Security Server. When remote database host is used, the local PostgreSQL can be stopped and disabled after the installation.

To stop the local PostgreSQL server

`systemctl stop postgresql`

To disable the local PostgreSQL server so that it does not start automatically when the server is rebooted.

`systemctl mask postgresql`


## 3 Security Server Initial Configuration

During the security server initial configuration, the server’s X-Road membership information and the software token’s PIN are set.


### 3.1 Prerequisites

Configuring the security server assumes that the security server owner is a member of the X-Road.


### 3.2 Reference Data

ATTENTION: Reference items 2.1 - 2.3 in the reference data are provided to the security server owner by the X-Road central’s administrator.

The security server code and the software token’s PIN will be determined during the installation at the latest, by the person performing the installation.

 Ref  |                                                   | Explanation
 ---- | ------------------------------------------------- | --------------------------------------------------
 2.1  | &lt;global configuration anchor file&gt; or &lt;URL&gt; | Global configuration anchor file
 2.2  | E.g.<br>GOV - government<br> COM - commercial     | Member class of the security server's owner
 2.3  | &lt;security server owner register code&gt;       | Member code of the security server's owner
 2.4  | &lt;choose security server identificator name&gt; | Security server's code
 2.5  | &lt;choose PIN for software token&gt;             | Software token’s PIN


### 3.3 Configuration

To perform the initial configuration, open the address

    https://SECURITYSERVER:4000/

in a Web browser (**reference data: 1.8; 1.6**). To log in, use the account name chosen during the installation (**reference data: 1.3).**

Upon first log-in, the system asks for the following information.

* The global configuration anchor file (**reference data: 2.1**).

    **Please verify anchor hash value with the published value.**

If the configuration is successfully downloaded, the system asks for the following information.

* The security server owner’s member class (**reference data: 2.2**).
* The security server owner’s member code (**reference data: 2.3**).

  If the member class and member code are correctly entered, the system displays the security server owner’s name as registered in the X-Road center.

* Security server code (**reference data: 2.4**), which is chosen by the security server administrator and which has to be unique across all the security servers belonging to the same X-Road member.
* Software token’s PIN (**reference data: 2.5**). The PIN will be used to protect the keys stored in the software token. The PIN must be stored in a secure place, because it will be no longer possible to use or recover the private keys in the token once the PIN has been lost.


## Annex A Security Server Default Database Properties

`/etc/xroad/db.properties`

```
serverconf.hibernate.jdbc.use_streams_for_binary = true
serverconf.hibernate.dialect = ee.ria.xroad.common.db.CustomPostgreSQLDialect
serverconf.hibernate.connection.driver_class = org.postgresql.Driver
serverconf.hibernate.connection.url = jdbc:postgresql://127.0.0.1:5432/serverconf
serverconf.hibernate.hikari.dataSource.currentSchema = serverconf,public
serverconf.hibernate.connection.username = serverconf
serverconf.hibernate.connection.password = <randomly generated password>
messagelog.hibernate.jdbc.use_streams_for_binary = true
messagelog.hibernate.connection.driver_class = org.postgresql.Driver
messagelog.hibernate.connection.url = jdbc:postgresql://127.0.0.1:5432/messagelog
messagelog.hibernate.hikari.dataSource.currentSchema = messagelog,public
messagelog.hibernate.connection.username = messagelog
messagelog.hibernate.connection.password = <randomly generated password>
op-monitor.hibernate.jdbc.use_streams_for_binary = true
op-monitor.hibernate.connection.driver_class = org.postgresql.Driver
op-monitor.hibernate.connection.url = jdbc:postgresql://127.0.0.1:5432/op-monitor
op-monitor.hibernate.hikari.dataSource.currentSchema = opmonitor,public
op-monitor.hibernate.connection.username = opmonitor
op-monitor.hibernate.connection.password = <randomly generated password>
```


## Annex B Database Users

| User             | Database   | Privileges               | Description                                                                              |
| ---------------- | ---------- | ------------------------ | ---------------------------------------------------------------------------------------- |
| serverconf       | serverconf | TEMPORARY,CONNECT        | The database user used to read/write the serverconf database during application runtime. |
| serverconf_admin | serverconf | CREATE,TEMPORARY,CONNECT | The database user used to create/update the serverconf schema.                           |
| messagelog       | messagelog | TEMPORARY,CONNECT        | The database user used to read/write the messagelog database during application runtime. |
| messagelog_admin | messagelog | CREATE,TEMPORARY,CONNECT | The database user used to create/update the messagelog schema.                           |
| opmonitor        | op-monitor | TEMPORARY,CONNECT        | The database user used to read/write the op-monitor database during application runtime. |
| opmonitor_admin  | op-monitor | CREATE,TEMPORARY,CONNECT | The database user used to create/update the op-monitor schema.                           |
| postgres         | ALL        | ALL                      | PostgreSQL database default superuser.                                                   |


## Annex C Deployment Options


### C.1 General

X-Road security server has multiple deployment options. The simplest choice is to have a single security server with local database. This is usually fine for majority of the cases, but there are multiple reasons to tailor the deployment.


### C.2 Local Database

The simplest deployment option is to use a single security server with local database. For development and testing purposes there is rarely need for anything else, but for production the requirements may be stricter.

![Security server with local database](img/ig-ss_local_db.svg)


### C.3 Remote Database

It is possible to use a remote database with security server. This option is sometimes used in development and testing when there's need to externalize the database state.

Security server supports a variety of cloud databases including AWS RDS and Azure Database for PostgreSQL. This deployment option is useful when doing development in cloud environment, where use of cloud native database is the first choice.

![Security server with remote database](img/ig-ss_remote_db.svg)


### C.4 High Availability Setup

In production systems it's rarely acceptable to have a single point of failure. Security server supports provider side high availability setup via so called internal load balancing mechanism. The setup works so that the same member / member class / member code / subsystem / service code is configured on multiple security servers and X-Road will then route the request to the server that responds the fastest. Note that this deployment option does not provide performance benefits, just redundancy.

![Security server high-availability setup](img/ig-ss_high_availability.svg)


### C.5 Load Balancing Setup

Busy production systems may need scalable performance in addition to high availability. X-Road supports external load balancing mechanism to address both of these problems simultaneously. A load balancer is added in front of a security server cluster to route the requests based on selected algorithm. This deployment option is extensively documented in \[[IG-XLB](#Ref_IG-XLB)\].

![Security server load balancing setup](img/ig-ss_load_balancing.svg)


### C.6 Summary

The following table lists a summary of the security server deployment options and indicates whether they are aimed for development or production use.

| Deployment               | Dev  | Prod  |
|--------------------------|------|-------|
| Local database           | x    |       |
| Remote database          | x    |       |
| High-availability Setup  |      | x     |
| Load Balancing Setup     |      | x     |


## Annex D Create Database Structure Manually

First create the configuration file `/etc/xroad.properties`.

  ```
  sudo touch /etc/xroad.properties
  sudo chown root:root /etc/xroad.properties
  sudo chmod 600 /etc/xroad.properties
  ```

Edit `/etc/xroad.properties` contents.

  ```
  serverconf.database.admin_user = <serverconf admin username>
  serverconf.database.admin_password = <serverconf admin password>
  op-monitor.database.admin_user = <op-monitor admin username>
  op-monitor.database.admin_password = <op-monitor admin password>
  messagelog.database.admin_user = <messagelog admin username>
  messagelog.database.admin_password = <messagelog admin password>
  ```

Next install PostgreSQL client.

  ```
  sudo apt install postgresql-client-10
  ```

Login to the database server as the superuser (`postgres` by default).

  ```
  psql -h <database host> -U <superuser> -d postgres
  ```

Run the following commands to create the necessary database structures and roles for `serverconf` and `messagelog` databases.

  ```
  CREATE DATABASE <serverconf database> ENCODING 'UTF8';
  REVOKE ALL ON DATABASE <serverconf database> FROM PUBLIC;
  CREATE ROLE <serverconf admin> LOGIN PASSWORD '<serverconf admin password>';
  GRANT <serverconf admin> to <superuser>;
  GRANT CREATE,TEMPORARY,CONNECT ON DATABASE <serverconf database> TO <serverconf admin>;
  \c <serverconf database>
  CREATE EXTENSION hstore;
  CREATE SCHEMA <serverconf database schema> AUTHORIZATION <serverconf admin>;
  REVOKE ALL ON SCHEMA public FROM PUBLIC;
  GRANT USAGE ON SCHEMA public to <serverconf admin>;
  CREATE ROLE <serverconf user> LOGIN PASSWORD '<serverconf user password>';
  GRANT <serverconf user> to <superuser>;
  GRANT TEMPORARY,CONNECT ON DATABASE <serverconf database> TO <serverconf user>;
  GRANT USAGE ON SCHEMA public to <serverconf user>;
  ```

  ```
  CREATE DATABASE <messagelog database> ENCODING 'UTF8';
  REVOKE ALL ON DATABASE <messagelog database> FROM PUBLIC;
  CREATE ROLE <messagelog admin> LOGIN PASSWORD '<messagelog admin password>';
  GRANT <messagelog admin> to <superuser>;
  GRANT CREATE,TEMPORARY,CONNECT ON DATABASE <messagelog database> TO <messagelog admin>;
  \c <messagelog database>
  CREATE SCHEMA <messagelog database schema> AUTHORIZATION <messagelog admin>;
  REVOKE ALL ON SCHEMA public FROM PUBLIC;
  GRANT USAGE ON SCHEMA public to <messagelog admin>;
  CREATE ROLE <messagelog user> LOGIN PASSWORD '<messagelog user password>';
  GRANT <messagelog user> to <superuser>;
  GRANT TEMPORARY,CONNECT ON DATABASE <messagelog database> TO <messagelog user>;
  GRANT USAGE ON SCHEMA public to <messagelog user>;
  ```

If operational monitoring is going to be installed, run additionally the following commands.

  ```
  CREATE DATABASE <op-monitor database> ENCODING 'UTF8';
  REVOKE ALL ON DATABASE <op-monitor database> FROM PUBLIC;
  CREATE ROLE <op-monitor admin> LOGIN PASSWORD '<op-monitor admin password>';
  GRANT <op-monitor admin> to <superuser>;
  GRANT CREATE,TEMPORARY,CONNECT ON DATABASE <op-monitor database> TO <op-monitor admin>;
  \c <op-monitor database>
  CREATE SCHEMA <op-monitor database schema> AUTHORIZATION <op-monitor admin>;
  REVOKE ALL ON SCHEMA public FROM PUBLIC;
  GRANT USAGE ON SCHEMA public to <op-monitor admin>;
  CREATE ROLE <op-monitor user> LOGIN PASSWORD '<op-monitor user password>';
  GRANT <op-monitor user> to <superuser>;
  GRANT TEMPORARY,CONNECT ON DATABASE <op-monitor database> TO <op-monitor user>;
  GRANT USAGE ON SCHEMA public to <op-monitor user>;
  ```
