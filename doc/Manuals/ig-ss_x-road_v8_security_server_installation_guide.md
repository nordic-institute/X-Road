# Security Server Installation Guide for Ubuntu <!-- omit in toc -->

**X-ROAD 8**

Version: 1.5
Doc. ID: IG-SS-8

---

## Version history <!-- omit in toc -->

| Date       | Version | Description                                                          | Author            |
|------------|---------|----------------------------------------------------------------------|-------------------|
| 20.10.2025 | 1.0     | Initial version                                                      | Justas Samuolis   |
| 15.09.2026 | 1.1     | Updated component name and port for auxiliary service (7665 -> 5666) | Eneli Reimets     |
| 19.10.2026 | 1.2     | Updated port for operational monitoring daemon (2080 -> 2081)        | Eneli Reimets     |
| 17.11.2026 | 1.3     | Documented country profile selection and precedence                  | Ričardas Bučiūnas |
| 18.11.2026 | 1.4     | Added the DID and DSP port choice at installation                    | Justas Samuolis   |
| 23.11.2026 | 1.5     | Added section 2.9 about installing an external operational monitoring | Eneli Reimets     |

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
    - [2.6.2 Country profile](#262-country-profile)
    - [2.6.3 DID and DSP ports](#263-did-and-dsp-ports)
  - [2.7 Post-Installation Checks](#27-post-installation-checks)
  - [2.8 Installing the Support for Environmental Monitoring](#28-installing-the-support-for-environmental-monitoring)
  - [2.9 Installing an External Operational Monitoring Daemon](#29-installing-an-external-operational-monitoring-daemon)
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
3. <a id="Ref_IG-SS" class="anchor"></a>\[IG-SS] Security Server Installation Guide for Ubuntu. Document ID: [IG-SS](ig-ss_x-road_v6_security_server_installation_guide.md)

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
| 1.2     | https://artifactory.niis.org/api/gpg/key/public                                                                      | The repository key.<br /><br />Hash: `935CC5E7FA5397B171749F80D6E3973B`<br  />Fingerprint: `A01B FE41 B9D8 EAF4 872F  A3F1 FB0D 532C 10F6 EC5B`<br  />3rd party key server: [Ubuntu key server](https://keyserver.ubuntu.com/pks/lookup?search=0xfb0d532c10f6ec5b&fingerprint=on&op=index) |
| 1.3   | https://pkgs.openbao.org/deb                                                                                         | OpenBao package repository                                                                                                                                                                                                                                                                 |
| 1.4     |                                                                                                                      | Account name in the user interface                                                                                                                                                                                                                                                         |
| 1.5     | **Inbound ports from external network**                                                                              | Ports for inbound connections from the external network to the Security Server                                                                                                                                                                                                             |
| &nbsp;  | TCP 80                                                                                                               | Incoming ACME challenge requests from ACME servers                                                                                                                                                                                                                                         |
| &nbsp;  | TCP 5500                                                                                                             | Message exchange between Security Servers                                                                                                                                                                                                                                                  |
| &nbsp;  | TCP 5577                                                                                                             | Querying of OCSP responses between Security Servers                                                                                                                                                                                                                                        |
| &nbsp;  | TCP 7183                                                                                                             | DID documents, fetched by the Central Server and by other Security Servers. Default; chosen at installation, see [2.6.3](#263-did-and-dsp-ports)                                                                                                                                           |
| &nbsp;  | TCP 8183                                                                                                             | Dataspace Protocol messages from other Security Servers. Default; chosen at installation, see [2.6.3](#263-did-and-dsp-ports)                                                                                                                                                              |
| 1.6     | **Outbound ports to external network**                                                                               | Ports for outbound connections from the Security Server to the external network                                                                                                                                                                                                            |
| &nbsp;  | TCP 5500                                                                                                             | Message exchange between Security Servers                                                                                                                                                                                                                                                  |
| &nbsp;  | TCP 5577                                                                                                             | Querying of OCSP responses between Security Servers                                                                                                                                                                                                                                        |
| &nbsp;  | TCP 4001                                                                                                             | Communication with the Central Server                                                                                                                                                                                                                                                      |
| &nbsp;  | TCP 80,443                                                                                                           | Downloading global configuration from the Central Server                                                                                                                                                                                                                                   |
| &nbsp;  | TCP 80,443                                                                                                           | Most common OCSP and time-stamping services                                                                                                                                                                                                                                                |
| &nbsp;  | TCP 80,443                                                                                                           | Communication with ACME servers                                                                                                                                                                                                                                                            |
| &nbsp;  | TCP 587                                                                                                              | Communication with mail servers. The mail server may be located in internal or external network                                                                                                                                                                                            |
| 1.7     | **Inbound ports from internal network**                                                                              | Ports for inbound connections from the internal network to the Security Server                                                                                                                                                                                                             |
| &nbsp;  | TCP 4000                                                                                                             | User interface and management REST API (local network). **Must not be accessible from the internet!**                                                                                                                                                                                      |
| &nbsp;  | TCP 8080, 8443                                                                                                       | Information system access points (in the local network). **Must not be accessible from the external network without strong authentication. If open to the external network, IP filtering is strongly recommended.**                                                                        |
| 1.8     | **Outbound ports to internal network**                                                                               | Ports for inbound connections from the internal network to the Security Server                                                                                                                                                                                                             |
| &nbsp;  | TCP 80, 443, *other*                                                                                                 | Producer information system endpoints                                                                                                                                                                                                                                                      |
| &nbsp;  | TCP 2081                                                                                                             | gRPC connection between Security Server and operational data monitoring daemon (by default on localhost)                                                                                                                                                                                  |
| 1.9     |                                                                                                                      | Security Server internal IP address(es) and hostname(s)                                                                                                                                                                                                                                    |
| 1.10     |                                                                                                                      | Security Server public IP address, NAT address                                                                                                                                                                                                                                             |
| 1.11    | &lt;by default, the server’s IP addresses and names are added to the certificate’s Distinguished Name (DN) field&gt; | Information about the user interface TLS certificate                                                                                                                                                                                                                                       |
| 1.12    | &lt;by default, the server’s IP addresses and names are added to the certificate’s Distinguished Name (DN) field&gt; | Information about the services TLS certificate                                                                                                                                                                                                                                             |

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
| In                  | Central Server                                           | Security Server                                          | 7183             | tcp          | DID port, chosen at installation (default shown), see [2.6.3](#263-did-and-dsp-ports)                                                                                                 |
| In                  | Data Exchange Partner Security Server                    | Security Server                                          | 7183, 8183       | tcp          | DID port and DSP port, chosen at installation (defaults shown), see [2.6.3](#263-did-and-dsp-ports)                                                                                   |
| In                  | ACME Server                                              | Security Server                                          | 80               | tcp          |                                                                                                                                                                                       | 
| In                  | Consumer Information System                              | Security Server                                          | 8080, 8443       | tcp          | Source in the internal network                                                                                                                                                        |
| In                  | Admin                                                    | Security Server                                          | 4000             | tcp          | Source in the internal network                                                                                                                                                        |
| In                  | Monitoring system                                        | Security Server                                          | other            | tcp          | Source in the internal network<br />The health check interface is disabled by default and the target port is defined by the Security Server administrator when the feature is enabled |

The table below lists the open ports for Security Server components utilizing the _loopback_ interface. A loopback interface is a virtual network interface on a computer, facilitating self-communication for processes and applications. This enables local communication and the ports must be accessible locally.

| **Component**            | **Ports** | **Protocol** | **Note**                                  |
|--------------------------|-----------|--------------|-------------------------------------------|
| PostgreSQL database      | 5432      | tcp          | Default PostgreSQL port                   |
| OP Monitoring daemon     | 2081      | tcp          | Operational monitoring gRPC server port   |
| Environmental monitoring | 2552      | tcp          | Environmental monitoring gRPC server port |
| Signer                   | 5560      | tcp          | Signer gRPC port                          |
| Proxy                    | 5566      | tcp          | Proxy admin port                          |
| Proxy                    | 5567      | tcp          | Proxy gRPC server port                    |
| Configuration Client     | 5665      | tcp          | Configuration Client gRPC server port     |
| Auxiliary service        | 5666      | tcp          | Auxiliary service gRPC server port        |
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
* if the Security Server is separated from other networks by a firewall and/or NAT, the necessary connections to and from the Security Server are allowed (**reference data: 1.5; 1.6; 1.7; 1.8**). The enabling of auxiliary services which are necessary for the functioning and management of the operating system (such as DNS, NTP, and SSH) stay outside the scope of this guide;
* the DID port (default 7183) and the DSP port (default 8183) are allowed for inbound connections (**reference data: 1.5**). The ports are chosen at installation, see [2.6.3 DID and DSP ports](#263-did-and-dsp-ports);
* if the Security Server has a private IP address, a corresponding NAT record must be created in the firewall (**reference data: 1.10**).

### 2.4 Preparing OS

* Add an X-Road system administrator user (**reference data: 1.4**) whom all roles in the user interface are granted to. Add a new user with the command

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

Add X-Road package repository (**reference data: 1.1**):
```bash
echo "deb [signed-by=/usr/share/keyrings/niis-artifactory-keyring.gpg] https://artifactory.niis.org/xroad8-snapshot-deb $(lsb_release -sc)-current main" | sudo tee /etc/apt/sources.list.d/xroad.list > /dev/null
```

Add OpenBao package repository (**reference data: 1.3**):
```bash
curl -fsSL https://openbao.org/assets/openbao-gpg-pub-20240618.asc | sudo tee /usr/share/keyrings/openbao-keyring.asc > /dev/null
echo "deb [signed-by=/usr/share/keyrings/openbao-keyring.asc] https://pkgs.openbao.org/deb/ stable main" | sudo tee /etc/apt/sources.list.d/openbao.list > /dev/null
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

To use a country profile, install a country package instead. See [2.6.2 Country profile](#262-country-profile).

Upon the first installation of the packages, the system asks for the following information.

* Account name for the user who will be granted the rights to perform all activities in the user interface (**reference data: 1.4**).
* Database server URL. Locally installed database is suggested as default.
* The memory allocation configuration for the Java Virtual Machine (JVM) used by the proxy service.
  Allowed values are:
    * *d* - default, adds `-Xms100m -Xmx512m` config to the JVM options in `XROAD_PROXY_PARAMS` in `/etc/xroad/services/local.properties` file;
  
    * *r* - recommended, adds recommended Xms and Xmx values based on total memory in current server to the JVM options in `XROAD_PROXY_PARAMS` in `/etc/xroad/services/local.properties` file;
  
    * `<initialSize>[k|m|g] <maxSize>[k|m|g]` - custom values, which will be transformed to `-Xms<initialSize>[k|m|g] -Xmx<maxSize>[k|m|g]`.
  
  Note that in all cases `/etc/xroad/services/local.properties` file is updated so that `XROAD_PROXY_PARAMS` property contains new memory configs in the end of any other already present options there.

* The Common Name of the owner of the **user interface's and management REST API's** self-signed TLS certificate (*Subject Common Name*) and its alternative names (*subjectAltName*) (**reference data: 1.9; 1.11**). The certificate is used for securing connections to the user interface and to the management REST API.
  The name and IP addresses detected from the operating system are suggested as default values.

    * The *Subject Common Name* must be entered in the format:

              server.domain.tld

    * All IP addresses and domain names in use must be entered as alternative names in the format:

              IP:1.2.3.4,IP:4.3.2.1,DNS:servername,DNS:servername2.domain.tld

* The Common Name of the owner of the TLS certificate that is used for securing the HTTPS access point of information systems (**reference data: 1.9; 1.12**).
  The name and IP addresses detected from the system are suggested as default values.

    * The *Subject Common Name* must be entered in the format:

            server.domain.tld

    * All IP addresses and domain names in use must be entered as alternative names in the format:

            IP:1.2.3.4,IP:4.3.2.1,DNS:servername,DNS:servername2.domain.tld

* The decentralized identifier (DID) port and the Dataspace Protocol (DSP) port, on the first installation only. The defaults are 7183 and 8183. See [2.6.3 DID and DSP ports](#263-did-and-dsp-ports).

#### 2.6.1 Provisioning TLS certificates

TLS certificates for the user interface, the information system access point and gRPC services are automatically provisioned at the application startup, using OpenBao PKI secrets engine (package `xroad-secret-store-local`).  

#### 2.6.2 Country profile

A country profile is a set of default values for system parameters that fit the rules of one country. The profiles are part of the product. A server without a country package uses only the default values of X-Road.

The profile files are in the X-Road source code, in the directory `src/lib/properties-core/src/main/resources/xroad-config/country-profile/`. There is one YAML file for each profile, named `<CC>.yaml`, where `<CC>` is the upper case country code. The files define the values of the profiles.

To use a country profile, install the country package `xroad-securityserver-<cc>` in place of the base package `xroad-securityserver`. Here `<cc>` is the lower case country code. To list the available country packages, run:

```bash
apt-cache search xroad-securityserver-
```

Then install the package of your country. For example:

```bash
sudo apt install xroad-securityserver-<cc>
```

A country package installs `xroad-securityserver` and `xroad-opmonitor`, so no other package is needed. The country profile switches operational monitoring on. The same command adds a country profile to a server that has only the base package. Only one country package can be installed on a server. On the first installation of a country package, the X-Road services are restarted so that they load the profile.

If you remove a country package, the X-Road services keep the profile until their next restart.

The country profile is not part of the configuration backup. To restore a backup on a server that must use a country profile, install the same country package on that server before the restore.

The value of a system parameter comes from the first of these sources that has a value:

1. The value that you saved for the system parameter in the admin user interface or in the configuration database. It always wins over the country profile.
2. The value of the country profile.
3. The default value of X-Road.

To see where a value comes from, open **Settings > System Parameters** in the admin user interface. The **Current Value** column shows the value that the server uses after the restart that the user interface asks for when you save a change. The **Source** column shows its origin:

| Source                 | Meaning                                                    |
|------------------------|------------------------------------------------------------|
| `Database`             | A value that an operator saved for the system parameter.   |
| `Country profile <CC>` | The value of the profile of the installed country package. |
| `Default`              | The default value of X-Road.                               |

#### 2.6.3 DID and DSP ports

The Security Server serves the documents of its decentralized identifiers (DIDs) on the DID port and the Dataspace Protocol (DSP) on the DSP port. The defaults are 7183 for the DID port and 8183 for the DSP port.

The ports are chosen once, on the first installation, before the services start. A DID cannot change its port after it is bound, so the installation never asks for the ports again and never changes them later.

Each port must be a number from 1024 to 65535, and the DID port must differ from the DSP port. Leading zeros are ignored (`08183` is `8183`). A port that equals its default is not stored, because a missing row means the default. A port that differs from its default is stored in the `serverconf` database as the key `xroad.dsp.did-port` or `xroad.dsp.dsp-port`, before the identity hub, the control plane and the admin service (`xroad-ds-identity-hub`, `xroad-ds-control-plane`, `xroad-proxy-ui-api`) start. The installation stops with an error when a port cannot be stored, or when an answer is not valid. A key that already has a row keeps its value.

**Ubuntu packages**

`apt` asks for the DID port and the DSP port on the first installation of the package `xroad-proxy`, with the defaults filled in. An invalid answer is asked again. To answer them in advance, preseed the answers before the installation. The owner is `xroad-proxy` and the keys are `xroad-common/did-port` and `xroad-common/dsp-port`:

```bash
echo "xroad-proxy xroad-common/did-port string 7190" | sudo debconf-set-selections
echo "xroad-proxy xroad-common/dsp-port string 8190" | sudo debconf-set-selections
```

A non-interactive installation without preseeded answers uses the defaults. An invalid preseeded value stops the installation.

**Reinstall, upgrade and reconfigure**

A reinstall, a package upgrade and `sudo dpkg-reconfigure xroad-proxy` neither ask for the ports nor change them. An upgrade from X-Road 7.8 to X-Road 8 runs the upgrade wizard, which asks for the ports once, see the [Security Server Upgrade Guide](ig-ss_x-road_v8_security_server_upgrade_guide.md#operator-choices). Changing them after the upgrade is not supported: the upgraded server is already registered, so its DIDs are bound soon after X-Road 8 starts.

To use other ports on a server that was installed with the defaults, before the server is registered and its DIDs are bound, write the rows by hand and restart the services that read them:

```bash
sudo /usr/share/xroad/scripts/db_property.sh set xroad.dsp.did-port 7190 --yes
sudo /usr/share/xroad/scripts/db_property.sh set xroad.dsp.dsp-port 8190 --yes
sudo systemctl restart xroad-ds-identity-hub xroad-ds-control-plane xroad-proxy-ui-api
```

The checks of the installation do not run for rows written by hand. Use values that follow the rules above. Changing a port after the DIDs are bound is not supported.

**Secondary nodes**

A secondary node of a cluster neither asks for the ports nor stores them, because the primary node owns the rows. The packages treat the host as a secondary node when one of these holds:

* `/etc/xroad/conf.d/node.ini` has the value `secondary` (the legacy value `slave` also counts) for `type` in the `[node]` section before the packages are installed. The directory does not exist before the installation, so create it by hand:

  ```bash
  sudo mkdir -p /etc/xroad/conf.d
  printf '[node]\ntype=secondary\n' | sudo tee /etc/xroad/conf.d/node.ini
  ```

* The environment variable `XROAD_IGNORE_DATABASE_SETUP` is set, with any value. This is the supported switch for installing a secondary node. It also makes the packages skip their database setup, so use it only for a node that uses the database of the primary node.

A node that was installed first and made a secondary later, as described in the [External Load Balancer Installation Guide](LoadBalancing/ig-xlb_x-road_external_load_balancer_installation_guide.md), has port rows in its own local database when ports other than the defaults were chosen at its installation. The rows are unused once the node uses the database replicated from the primary node.

**Firewall**

The DID port must accept inbound connections from the Central Server and from other Security Servers, which fetch DID documents from it. Other Security Servers send Dataspace Protocol messages to the DSP port. When a port differs from its default, change the firewall rules to match.

### 2.7 Post-Installation Checks

The installation is successful if system services are started and the user interface is responding.

* Ensure from the command line that X-Road services are in the `running` state (example output follows):
  ```bash
  sudo systemctl list-units "xroad-*"

  UNIT                           LOAD   ACTIVE SUB     DESCRIPTION
  xroad-auxiliary-service.service   loaded active running X-Road auxiliary service
  xroad-base.service               loaded active exited  X-Road initialization
  xroad-confclient.service         loaded active running X-Road confclient
  xroad-monitor.service            loaded active running X-Road Monitor
  xroad-proxy-ui-api.service       loaded active running X-Road Proxy UI REST API
  xroad-proxy.service              loaded active running X-Road Proxy
  xroad-secret-store-local.service loaded active exited  X-Road OpenBao Auto Init Service
  xroad-signer.service             loaded active running X-Road signer
  ```
* Ensure that the Security Server user interface at https://SECURITYSERVER:4000/ (**reference data: 1.9; 1.7**) can be opened in a Web browser. To log in, use the account name chosen during the installation (**reference data: 1.4**). While the user interface is still starting up, the Web browser may display a connection refused -error.

### 2.8 Installing the Support for Environmental Monitoring

The support for environmental monitoring functionality on a Security Server is provided by package xroad-monitor that is installed by default. The package installs and starts the `xroad-monitor` process that will gather and make available the monitoring information.

### 2.9 Installing an External Operational Monitoring Daemon

A standalone operational monitoring daemon runs on a host that is not the Security Server. This section describes how to install it on Ubuntu. For RHEL, see section 2.12 of the [Security Server Installation Guide for RHEL](ig-ss_x-road_v6_security_server_installation_guide_for_rhel.md#212-installing-an-external-operational-monitoring-daemon). The configuration of the daemon is in section [15.3.1](ug-ss_x-road_6_security_server_user_guide.md#1531-installing-and-configuring-an-external-operational-monitoring-daemon) of the Security Server User Guide \[[UG-SS](#Ref_UG-SS)\]. This section does not repeat it.

**Prerequisites**

* The host runs Ubuntu Server 24.04 LTS on a x86-64 platform (see [2.1](#21-prerequisites-to-installation)).
* The host has the locale settings of section [2.4](#24-preparing-os) and the X-Road package repository of section [2.5](#25-setup-package-repositories). The host needs no X-Road administrator user. Do not add the OpenBao package repository: the host needs no OpenBao and no secret store.
* The database of the daemon is a local PostgreSQL 15 or later. The package `xroad-database-local` installs it.
* The host has the same X-Road version as the Security Server that it serves.

**Installation**

Run the following command to install the packages:

```bash
sudo apt install xroad-opmonitor xroad-database-local
```

The installation creates the database `op-monitor` and its database roles in the local PostgreSQL server on 127.0.0.1 and writes the connection properties to `/etc/xroad/db.properties` (see [Annex A](#annex-a-security-server-default-database-properties)). A remote database is not covered by this guide.

The daemon cannot run before the host is configured, so its first start after the installation fails. Continue with section [15.3.1](ug-ss_x-road_6_security_server_user_guide.md#1531-installing-and-configuring-an-external-operational-monitoring-daemon) of the Security Server User Guide.

**Verification**

* Ensure that the package is installed and that the database properties of the daemon exist. The command prints the property names only:

  ```bash
  dpkg -s xroad-opmonitor | grep '^Status'
  sudo grep -E '^xroad\.db\.op-monitor\.hibernate\.' /etc/xroad/db.properties | cut -d= -f1
  ```

  The first command must print `Status: install ok installed`. The second command must list six property names, as in [Annex A](#annex-a-security-server-default-database-properties).

## 3 Security Server Initial Configuration

Refer to X-Road 7. Security Server Installation Guide for Ubuntu [IG-SS](#Ref_IG-SS), section "Security Server Initial Configuration".

## 4 Installation Error handling

Refer to X-Road 7. Security Server Installation Guide for Ubuntu [IG-SS](#Ref_IG-SS), section "Installation Error Handling".

## Annex A Security Server Default Database Properties

`/etc/xroad/db.properties`

```properties
# connection.url format: jdbc:postgresql://<hostname>:<port>/<database name>

xroad.db.serverconf.hibernate.connection.url = jdbc:postgresql://127.0.0.1:5432/serverconf
xroad.db.serverconf.hibernate.connection.username = serverconf
xroad.db.serverconf.hibernate.connection.password = <randomly generated password>
xroad.db.serverconf.hibernate.connection.driver_class = org.postgresql.Driver
xroad.db.serverconf.hibernate.dialect = ee.ria.xroad.common.db.CustomPostgreSQLDialect
xroad.db.serverconf.hibernate.hikari.dataSource.currentSchema = serverconf,public
xroad.db.serverconf.hibernate.jdbc.use_streams_for_binary = true

xroad.db.messagelog.hibernate.connection.url = jdbc:postgresql://127.0.0.1:5432/messagelog
xroad.db.messagelog.hibernate.connection.username = messagelog
xroad.db.messagelog.hibernate.connection.password = <randomly generated password>
xroad.db.messagelog.hibernate.connection.driver_class = org.postgresql.Driver
xroad.db.messagelog.hibernate.dialect = ee.ria.xroad.common.db.CustomPostgreSQLDialect
xroad.db.messagelog.hibernate.hikari.dataSource.currentSchema = messagelog,public
xroad.db.messagelog.hibernate.jdbc.use_streams_for_binary = true

xroad.db.op-monitor.hibernate.connection.url = jdbc:postgresql://127.0.0.1:5432/op-monitor
xroad.db.op-monitor.hibernate.connection.username = opmonitor
xroad.db.op-monitor.hibernate.connection.password = <randomly generated password>
xroad.db.op-monitor.hibernate.connection.driver_class = org.postgresql.Driver
xroad.db.op-monitor.hibernate.hikari.dataSource.currentSchema = opmonitor,public
xroad.db.op-monitor.hibernate.jdbc.use_streams_for_binary = true

```

The installation of the package `xroad-opmonitor` writes the operational monitoring block.

## Annex B Default Database Users

| User             | Database   | Privileges               | Description                                                                              |
|------------------|------------|--------------------------|------------------------------------------------------------------------------------------|
| serverconf       | serverconf | TEMPORARY,CONNECT        | The database user used to read/write the serverconf database during application runtime. |
| serverconf_admin | serverconf | CREATE,TEMPORARY,CONNECT | The database user used to create/update the serverconf schema.                           |
| messagelog       | messagelog | TEMPORARY,CONNECT        | The database user used to read/write the messagelog database during application runtime. |
| messagelog_admin | messagelog | CREATE,TEMPORARY,CONNECT | The database user used to create/update the messagelog schema.                           |
| opmonitor        | op-monitor | TEMPORARY,CONNECT        | The database user used to read/write the op-monitor database during application runtime. |
| opmonitor_admin  | op-monitor | CREATE,TEMPORARY,CONNECT | The database user used to create/update the op-monitor schema.                           |
| openbao          | openbao    | CREATE,TEMPORARY,CONNECT | The database user for local OpenBao (package `xroad-secret-store`)                       |
| postgres         | ALL        | ALL                      | PostgreSQL database default superuser.                                                   |

If a database role already exists and its password differs from the one stored in `db.properties`, the installation stops with the message "Logging in as database role '<role>' failed". Set the stored password in `db.properties` to match the role and rerun the installation.

The installer creates the database roles itself. If they already exist, on PostgreSQL 16 and later the master user needs ADMIN OPTION on them.

## Annex C `xroad-secret-store-local` default configuration

Local Secret Store uses OpenBao for secrets storage. The default configuration files are located in `/etc/openbao/`.<br/>
Files:<br/>
`openbao.hcl` - OpenBao main configuration file. Contains DB credentials and other options.<br/>
`root-token` - root OpenBao token.<br/>
`unseal-keys` - OpenBao unseal keys.


