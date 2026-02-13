# Security Server Installation Guide for Ubuntu <!-- omit in toc -->

**X-ROAD 8**

Version: 1.0
Doc. ID: IG-SS-8

---

## Version history <!-- omit in toc -->

| Date       | Version | Description     | Author          |
|------------|---------|-----------------|-----------------|
| 01.12.2014 | 1.0     | Initial version | Justas Samuolis |

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
  - [2.5 Setup Package Repositories](#25-setup-package-repositories)
  - [2.6 Security Server Installation](#26-security-server-installation)
    - [2.6.1 Provisioning TLS certificates](#261-provisioning-tls-certificates) 
  - [2.7 Post-Installation Checks](#27-post-installation-checks)
  - [2.8 Installing the Support for Environmental Monitoring](#28-installing-the-support-for-environmental-monitoring)
- [3 Security Server Initial Configuration](#3-security-server-initial-configuration)
- [4 Installation Error handling](#4-installation-error-handling)
- [Annex A Security Server Default Database Properties](#annex-a-security-server-default-database-properties)
- [Annex B Default Database Users](#annex-b-default-database-users)
- [Annex C `xroad-secret-store-local` default configuration](#annex-c-xroad-secret-store-local-default-configuration)

<!-- vim-markdown-toc -->
<!-- tocstop -->

## 1 Introduction


### 1.1 Target Audience

The intended audience of this Installation Guide are X-Road Security Server system administrators responsible for installing and using X-Road software. The daily operation and maintenance of the Security Server is covered by its User Guide \[[UG-SS](#Ref_UG-SS)\].

The document is intended for readers with a moderate knowledge of Linux server management, computer networks, and the X-Road working principles.


### 1.2 Terms and abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\].


### 1.3 References

1. <a id="Ref_UG-SS" class="anchor"></a>\[UG-SS\] X-Road 7. Security Server User Guide. Document ID: [UG-SS](ug-ss_x-road_6_security_server_user_guide.md)
2. <a id="Ref_TERMS" class="anchor"></a>\[TA-TERMS\] X-Road Terms and Abbreviations. Document ID: [TA-TERMS](../terms_x-road_docs.md)
3. <a id="Ref_UG-SS" class="anchor"></a>\[IG-SS] Security Server Installation Guide for Ubuntu. Document ID: [IG-SS](ig-ss_x-road_v6_security_server_installation_guide.md)

## 2 Installation

### 2.1 Prerequisites to Installation

The Security Server is officially supported on the following platforms:

* Ubuntu Server 24.04 Long-Term Support (LTS) operating system on a x86-64 platform.

The software can be installed both on physical and virtualized hardware.


### 2.2 Reference Data

*Note*: The information in empty cells should be determined before the server’s installation, by the person performing the installation.

**Caution**: Data necessary for the functioning of the operating system is not included.


| **Ref** |                                                                                                                      | **Explanation**                                                                                                                                                                                                                                                                            |
|---------|----------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1.0     | Ubuntu 22.04 or 24.04 (x86-64)<br>3 GB RAM, 3 GB free disk space                                                     | Minimum requirements without the `monitoring` and `op-monitoring` add-ons. With the add-ons minimum of 4 GB of RAM is required.                                                                                                                                                            |
| 1.1     | https://artifactory.niis.org/xroad8-snapshot-deb                                                                     | X-Road package repository                                                                                                                                                                                                                                                                  |
| 1.1.1   | https://artifactory.niis.org/artifactory/xroad-dependencies-deb                                                      | X-Road external dependencies repository                                                                                                                                                                                                                                                    |
| 1.2     | https://artifactory.niis.org/api/gpg/key/public                                                                      | The repository key.<br /><br />Hash: `935CC5E7FA5397B171749F80D6E3973B`<br  />Fingerprint: `A01B FE41 B9D8 EAF4 872F  A3F1 FB0D 532C 10F6 EC5B`<br  />3rd party key server: [Ubuntu key server](https://keyserver.ubuntu.com/pks/lookup?search=0xfb0d532c10f6ec5b&fingerprint=on&op=index) |
| 1.3     |                                                                                                                      | Account name in the user interface                                                                                                                                                                                                                                                         |
| 1.4     | **Inbound ports from external network**                                                                              | Ports for inbound connections from the external network to the Security Server                                                                                                                                                                                                             |
| &nbsp;  | TCP 80                                                                                                               | Incoming ACME challenge requests from ACME servers                                                                                                                                                                                                                                         |
| &nbsp;  | TCP 5500                                                                                                             | Message exchange between Security Servers                                                                                                                                                                                                                                                  |
| &nbsp;  | TCP 5577                                                                                                             | Querying of OCSP responses between Security Servers                                                                                                                                                                                                                                        |
| 1.5     | **Outbound ports to external network**                                                                               | Ports for outbound connections from the Security Server to the external network                                                                                                                                                                                                            |
| &nbsp;  | TCP 5500                                                                                                             | Message exchange between Security Servers                                                                                                                                                                                                                                                  |
| &nbsp;  | TCP 5577                                                                                                             | Querying of OCSP responses between Security Servers                                                                                                                                                                                                                                        |
| &nbsp;  | TCP 4001                                                                                                             | Communication with the Central Server                                                                                                                                                                                                                                                      |
| &nbsp;  | TCP 80,443                                                                                                           | Downloading global configuration from the Central Server                                                                                                                                                                                                                                   |
| &nbsp;  | TCP 80,443                                                                                                           | Most common OCSP and time-stamping services                                                                                                                                                                                                                                                |
| &nbsp;  | TCP 80,443                                                                                                           | Communication with ACME servers                                                                                                                                                                                                                                                            |
| &nbsp;  | TCP 587                                                                                                              | Communication with mail servers. The mail server may be located in internal or external network                                                                                                                                                                                            |
| 1.6     | **Inbound ports from internal network**                                                                              | Ports for inbound connections from the internal network to the Security Server                                                                                                                                                                                                             |
| &nbsp;  | TCP 4000                                                                                                             | User interface and management REST API (local network). **Must not be accessible from the internet!**                                                                                                                                                                                      |
| &nbsp;  | TCP 8080, 8443                                                                                                       | Information system access points (in the local network). **Must not be accessible from the external network without strong authentication. If open to the external network, IP filtering is strongly recommended.**                                                                        |
| 1.7     | **Outbound ports to internal network**                                                                               | Ports for inbound connections from the internal network to the Security Server                                                                                                                                                                                                             |
| &nbsp;  | TCP 80, 443, *other*                                                                                                 | Producer information system endpoints                                                                                                                                                                                                                                                      |
| &nbsp;  | TCP 2080                                                                                                             | Message exchange between Security Server and operational data monitoring daemon (by default on localhost)                                                                                                                                                                                  |
| 1.8     |                                                                                                                      | Security Server internal IP address(es) and hostname(s)                                                                                                                                                                                                                                    |
| 1.9     |                                                                                                                      | Security Server public IP address, NAT address                                                                                                                                                                                                                                             |
| 1.10    | &lt;by default, the server’s IP addresses and names are added to the certificate’s Distinguished Name (DN) field&gt; | Information about the user interface TLS certificate                                                                                                                                                                                                                                       |
| 1.11    | &lt;by default, the server’s IP addresses and names are added to the certificate’s Distinguished Name (DN) field&gt; | Information about the services TLS certificate                                                                                                                                                                                                                                             |

#### 2.2.1 Network Diagram

The network diagram below provides an example of a basic Security Server setup. Allowing incoming connections from the Monitoring Security Server on ports 5500/tcp and 5577/tcp is necessary for the X-Road Operator to be able to monitor the ecosystem and provide statistics and support for Members.

![network diagram](img/ig-ss_network_diagram.svg)

The table below lists the required connections between different components.

| **Connection Type** | **Source**                                               | **Target**                                               | **Target Ports** | **Protocol** | **Note**                                                                                                                                                                              |
|---------------------|----------------------------------------------------------|----------------------------------------------------------|------------------|--------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Out                 | Security Server                                          | Central Server                                           | 80, 443, 4001    | tcp          |                                                                                                                                                                                       |
| Out                 | Security Server                                          | Management Security Server                               | 5500, 5577       | tcp          |                                                                                                                                                                                       |
| Out                 | Security Server                                          | OCSP Service                                             | 80 / 443         | tcp          |                                                                                                                                                                                       |
| Out                 | Security Server                                          | Timestamping Service                                     | 80 / 443         | tcp          |                                                                                                                                                                                       |
| Out                 | Security Server                                          | Data Exchange Partner Security Server (Service Producer) | 5500, 5577       | tcp          |                                                                                                                                                                                       |
| Out                 | Security Server                                          | Producer Information System                              | 80, 443, other   | tcp          | Target in the internal network                                                                                                                                                        |
| Out                 | Security Server                                          | ACME Server                                              | 80 / 443         | tcp          |                                                                                                                                                                                       |
| Out                 | Security Server                                          | Mail server                                              | 587              | tcp          |                                                                                                                                                                                       |
| In                  | Monitoring Security Server                               | Security Server                                          | 5500, 5577       | tcp          |                                                                                                                                                                                       |
| In                  | Data Exchange Partner Security Server (Service Consumer) | Security Server                                          | 5500, 5577       | tcp          |                                                                                                                                                                                       |
| In                  | ACME Server                                              | Security Server                                          | 80               | tcp          |                                                                                                                                                                                       | 
| In                  | Consumer Information System                              | Security Server                                          | 8080, 8443       | tcp          | Source in the internal network                                                                                                                                                        |
| In                  | Admin                                                    | Security Server                                          | 4000             | tcp          | Source in the internal network                                                                                                                                                        |
| In                  | Monitoring system                                        | Security Server                                          | other            | tcp          | Source in the internal network<br />The health check interface is disabled by default and the target port is defined by the Security Server administrator when the feature is enabled |

The table below lists the open ports for Security Server components utilizing the _loopback_ interface. A loopback interface is a virtual network interface on a computer, facilitating self-communication for processes and applications. This enables local communication and the ports must be accessible locally.

| **Component**            | **Ports** | **Protocol** | **Note**                                  |
|--------------------------|-----------|--------------|-------------------------------------------|
| PostgreSQL database      | 5432      | tcp          | Default PostgreSQL port                   |
| OP Monitoring daemon     | 2080      | tcp          |                                           |
| Environmental monitoring | 2552      | tcp          | Environmental monitoring gRPC server port |
| Signer                   | 5560      | tcp          | Signer gRPC port                          |
| Proxy                    | 5566      | tcp          | Proxy admin port                          |
| Proxy                    | 5567      | tcp          | Proxy gRPC server port                    |
| Configuration Client     | 5665      | tcp          | Configuration Client gRPC server port     |
| Backup Manager           | 7665      | tcp          | Backup Manager gRPC server port           |
| Secret Store             | 8200      | tcp          | Local Secret Store (OpenBao) port         |
| Audit Log                | 514       | udp          |                                           |

### 2.3 Requirements for the Security Server

Minimum recommended hardware parameters:

* the server’s hardware (motherboard, CPU, network interface cards, storage system) must be supported by Ubuntu in general;
* a 64-bit dual-core Intel, AMD or compatible CPU; AES instruction set support is highly recommended;
* 4 GB RAM;
* a 100 Mbps network interface card;
* if necessary, interfaces for the use of hardware tokens.

Requirements to software and settings:

* an installed and configured 24.04 LTS x86-64 operating system;
* if the Security Server is separated from other networks by a firewall and/or NAT, the necessary connections to and from the Security Server are allowed (**reference data: 1.4; 1.5; 1.6; 1.7**). The enabling of auxiliary services which are necessary for the functioning and management of the operating system (such as DNS, NTP, and SSH) stay outside the scope of this guide;
* if the Security Server has a private IP address, a corresponding NAT record must be created in the firewall (**reference data: 1.9**).

### 2.4 Preparing OS

* Add an X-Road system administrator user (**reference data: 1.3**) whom all roles in the user interface are granted to. Add a new user with the command

        sudo adduser <username>

    User roles are discussed in detail in X-Road Security Server User Guide \[[UG-SS](#Ref_UG-SS)\]. Do not use the user name `xroad`, it is reserved for the X-Road system user.

* Set the operating system locale. Add following line to the `/etc/environment` file.

        LC_ALL=en_US.UTF-8

* Ensure that the packages `locales` and `lsb-release` are present

        sudo apt-get install locales lsb-release

* Ensure that the locale is available

        sudo locale-gen en_US.UTF-8


### 2.5 Setup Package Repositories

Add the X-Road repository’s signing key to the list of trusted keys (**reference data: 1.2**):
```bash
curl -fsSL https://x-road.eu/gpg/key/public/niis-artifactory-public.gpg | sudo tee /usr/share/keyrings/niis-artifactory-keyring.gpg > /dev/null
```

Add X-Road package and external dependencies repositories (**reference data: 1.1, 1.1.1**)
```bash
echo "deb [signed-by=/usr/share/keyrings/niis-artifactory-keyring.gpg] https://artifactory.niis.org/xroad8-snapshot-deb $(lsb_release -sc)-current main" | sudo tee /etc/apt/sources.list.d/xroad.list > /dev/null
echo "deb [signed-by=/usr/share/keyrings/niis-artifactory-keyring.gpg] https://artifactory.niis.org/xroad-dependencies-deb xroad external" | sudo tee -a /etc/apt/sources.list.d/xroad.list > /dev/null
```

Update package repository metadata:
```bash
sudo apt update
```
### 2.6 Security Server Installation

Issue the following command to install the Security Server packages:

```bash
sudo apt install xroad-securityserver
```

Upon the first installation of the packages, the system asks for the following information.

* Account name for the user who will be granted the rights to perform all activities in the user interface (**reference data: 1.3**).
* Database server URL. Locally installed database is suggested as default.
* The memory allocation configuration for the Java Virtual Machine (JVM) used by the proxy service.
  Allowed values are:
    * *d* - default, adds `-Xms100m -Xmx512m` config to the JVM options in `XROAD_PROXY_PARAMS` in `/etc/xroad/services/local.properties` file;
  
    * *r* - recommended, adds recommended Xms and Xmx values based on total memory in current server to the JVM options in `XROAD_PROXY_PARAMS` in `/etc/xroad/services/local.properties` file;
  
    * `<initialSize>[k|m|g] <maxSize>[k|m|g]` - custom values, which will be transformed to `-Xms<initialSize>[k|m|g] -Xmx<maxSize>[k|m|g]`.
  
  Note that in all cases `/etc/xroad/services/local.properties` file is updated so that `XROAD_PROXY_PARAMS` property contains new memory configs in the end of any other already present options there.

* The Common Name of the owner of the **user interface's and management REST API's** self-signed TLS certificate (*Subject Common Name*) and its alternative names (*subjectAltName*) (**reference data: 1.8; 1.10**). The certificate is used for securing connections to the user interface and to the management REST API.
  The name and IP addresses detected from the operating system are suggested as default values.

    * The *Subject Common Name* must be entered in the format:

              server.domain.tld

    * All IP addresses and domain names in use must be entered as alternative names in the format:

              IP:1.2.3.4,IP:4.3.2.1,DNS:servername,DNS:servername2.domain.tld

* The Common Name of the owner of the TLS certificate that is used for securing the HTTPS access point of information systems (**reference data: 1.8; 1.11**).
  The name and IP addresses detected from the system are suggested as default values.

    * The *Subject Common Name* must be entered in the format:

            server.domain.tld

    * All IP addresses and domain names in use must be entered as alternative names in the format:

            IP:1.2.3.4,IP:4.3.2.1,DNS:servername,DNS:servername2.domain.tld

#### 2.6.1 Provisioning TLS certificates

TLS certificates for the user interface, the information system access point and gRPC services are automatically provisioned at the application startup, using OpenBao PKI secrets engine (package `xroad-secret-store-local`).  

### 2.7 Post-Installation Checks

The installation is successful if system services are started and the user interface is responding.

* Ensure from the command line that X-Road services are in the `running` state (example output follows):
  ```bash
  sudo systemctl list-units "xroad-*"

  UNIT                           LOAD   ACTIVE SUB     DESCRIPTION
  xroad-backup-manager.service     loaded active running X-Road backup manager
  xroad-base.service               loaded active exited  X-Road initialization
  xroad-confclient.service         loaded active running X-Road confclient
  xroad-monitor.service            loaded active running X-Road Monitor
  xroad-proxy-ui-api.service       loaded active running X-Road Proxy UI REST API
  xroad-proxy.service              loaded active running X-Road Proxy
  xroad-secret-store-local.service loaded active exited  X-Road OpenBao Auto Init Service
  xroad-signer.service             loaded active running X-Road signer
  ```
* Ensure that the Security Server user interface at https://SECURITYSERVER:4000/ (**reference data: 1.8; 1.6**) can be opened in a Web browser. To log in, use the account name chosen during the installation (**reference data: 1.3**). While the user interface is still starting up, the Web browser may display a connection refused -error.

### 2.8 Installing the Support for Environmental Monitoring

The support for environmental monitoring functionality on a Security Server is provided by package xroad-monitor that is installed by default. The package installs and starts the `xroad-monitor` process that will gather and make available the monitoring information.

## 3 Security Server Initial Configuration

Refer to X-Road 7. Security Server Installation Guide for Ubuntu [IG-SS](#Ref_IG-SS), section "Security Server Initial Configuration".

## 4 Installation Error handling

Refer to X-Road 7. Security Server Installation Guide for Ubuntu [IG-SS](#Ref_IG-SS), section "Installation Error Handling".

## Annex A Security Server Default Database Properties

`/etc/xroad/db.properties`

```properties
# connection.url format: jdbc:postgresql://<hostname>:<port>/<database name>

serverconf.hibernate.connection.url = jdbc:postgresql://127.0.0.1:5432/serverconf
serverconf.hibernate.connection.username = serverconf
serverconf.hibernate.connection.password = <randomly generated password>
serverconf.hibernate.connection.driver_class = org.postgresql.Driver
serverconf.hibernate.dialect = ee.ria.xroad.common.db.CustomPostgreSQLDialect
serverconf.hibernate.hikari.dataSource.currentSchema = serverconf,public
serverconf.hibernate.jdbc.use_streams_for_binary = true

messagelog.hibernate.connection.url = jdbc:postgresql://127.0.0.1:5432/messagelog
messagelog.hibernate.connection.username = messagelog
messagelog.hibernate.connection.password = <randomly generated password>
messagelog.hibernate.connection.driver_class = org.postgresql.Driver
messagelog.hibernate.dialect = ee.ria.xroad.common.db.CustomPostgreSQLDialect
messagelog.hibernate.hikari.dataSource.currentSchema = messagelog,public
messagelog.hibernate.jdbc.use_streams_for_binary = true

```

## Annex B Default Database Users

| User             | Database   | Privileges               | Description                                                                              |
|------------------|------------|--------------------------|------------------------------------------------------------------------------------------|
| serverconf       | serverconf | TEMPORARY,CONNECT        | The database user used to read/write the serverconf database during application runtime. |
| serverconf_admin | serverconf | CREATE,TEMPORARY,CONNECT | The database user used to create/update the serverconf schema.                           |
| messagelog       | messagelog | TEMPORARY,CONNECT        | The database user used to read/write the messagelog database during application runtime. |
| messagelog_admin | messagelog | CREATE,TEMPORARY,CONNECT | The database user used to create/update the messagelog schema.                           |
| openbao          | openbao    | CREATE,TEMPORARY,CONNECT | The database user for local OpenBao (package `xroad-secret-store`)                       |
| postgres         | ALL        | ALL                      | PostgreSQL database default superuser.                                                   |

## Annex C `xroad-secret-store-local` default configuration

Local Secret Store uses OpenBao for secrets storage. The default configuration files are located in `/etc/openbao/`.<br/>
Files:<br/>
`openbao.hcl` - OpenBao main configuration file. Contains DB credentials and other options.<br/>
`root-token` - root OpenBao token.<br/>
`unseal-keys` - OpenBao unseal keys.


