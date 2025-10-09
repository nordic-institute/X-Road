# X-Road: Central Server Installation Guide <!-- omit in toc -->

Version: 1.0
Doc. ID: IG-CS-8

---

## Version history <!-- omit in toc -->
| Date       | Version | Description     | Author          |
|------------|---------|-----------------|-----------------|
| 07.10.2025 | 1.0     | Initial version | Justas Samuolis |


## Table of Contents <!-- omit in toc -->

<!-- toc -->
<!-- vim-markdown-toc GFM -->

- [License](#license)
- [1. Introduction](#1-introduction)
  - [1.1 Target Audience](#11-target-audience)
  - [1.2 Terms and abbreviations](#12-terms-and-abbreviations)
  - [1.3 References](#13-references)
- [2. Installation](#2-installation)
  - [2.1 Prerequisites to Installation](#21-prerequisites-to-installation)
  - [2.2 Reference Data](#22-reference-data)
    - [2.2.1 Network Diagram](#221-network-diagram)
  - [2.3 Requirements to the Central Server](#23-requirements-to-the-central-server)
  - [2.4 Preparing OS](#24-preparing-os)
  - [2.5 Setup Package Repository](#25-setup-package-repository)
  - [2.6 Package Installation](#26-package-installation)
    - [2.6.1 Configuring TLS Certificates](#261-configuring-tls-certificates-for-global-configuration-distribution)
    - [2.6.2 Provisioning TLS Certificates for management services and user interface](#262-provisioning-tls-certificates-for-management-services-and-user-interface)
    - [2.6.3 Provisioning TLS Certificates for gRPC services](#263-provisioning-tls-certificates-for-grpc-services)
  - [2.7 Pre-configuration for Registration Web Service](#27-pre-configuration-for-registration-web-service)
  - [2.8 Pre-configuration for Management Web Service](#28-pre-configuration-for-management-web-service)
  - [2.9 Post-Installation Checks](#29-post-installation-checks)
- [3 Initial Configuration](#3-initial-configuration)
  - [3.1 Reference Data](#31-reference-data)
  - [3.2 Initializing the Central Server](#32-initializing-the-central-server)
  - [3.3 Configuring the Central Server and the Management Services' Security Server](#33-configuring-the-central-server-and-the-management-services-security-server)
- [4 Installation Error Handling](#4-installation-error-handling)
- [Annex A Central Server Default Database Properties](#annex-a-central-server-default-database-properties)
- [Annex B Database Users](#annex-b-database-users)
- [Annex C `xroad-secret-store-local` default configuration](#annex-c-xroad-secret-store-local-default-configuration)

<!-- vim-markdown-toc -->
<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/.

## 1. Introduction

### 1.1 Target Audience

The intended audience of this installation guide are the X-Road Central Server administrators responsible for installing and configuring the X-Road Central Server software.
The document is intended for readers with a good knowledge of Linux server management, computer networks, and the X-Road functioning principles.

### 1.2 Terms and abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\].

### 1.3 References

1. <a id="Ref_UG-CS" class="anchor"></a>\[UG-CS\] X-Road 7. Central Server User Guide. Document ID: [UG-CS](ug-cs_x-road_6_central_server_user_guide.md) 
2. <a id="Ref_IG-SS" class="anchor"></a>\[IG-SS\] X-Road 7. Security Server Installation Guide. Document ID: [IG-SS](ig-ss_x-road_v6_security_server_installation_guide.md)
3. <a id="Ref_UG-SS" class="anchor"></a>\[UG-SS\] X-Road 7. Security Server User Guide. Document ID: [UG-SS](ug-ss_x-road_6_security_server_user_guide.md)
4. <a id="Ref_TERMS" class="anchor"></a>\[TA-TERMS\] X-Road Terms and Abbreviations. Document ID: [TA-TERMS](../terms_x-road_docs.md).
5. <a id="Ref_UG-SYSPAR" class="anchor"></a>\[UG-SYSPAR\] X-Road: System Parameters User Guide. Document ID: [UG-SYSPAR]('ug-syspar_x-road_v6_system_parameters.md').
6. <a id="Ref_IG-CS" class="anchor"></a>\[IG-CS\] X-Road 7. Central Server Installation Guide. Document ID: [IG-CS](ig-cs_x-road_6_central_server_installation_guide.md)

## 2. Installation

### 2.1 Prerequisites to Installation

The Central Server software assumes an existing installation of the Ubuntu 24.04 LTS operating system, on an x86-64bit platform. To provide management services, a Security Server is installed alongside the Central Server.

The Central Server’s software can be installed both on physical and virtualized hardware.

### 2.2 Reference Data

Note: The information in empty cells will be determined at the latest during the server’s installation, by the person performing the installation.

Caution: Data necessary for the functioning of the operating system is not included.

| **Ref** |                                                                                                                                                                                                                                                                                                  | **Explanation**                                                                                                                                                                                                                                                                            |
|---------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1.0     | Ubuntu 24.04 (x86-64), 2 GB RAM, 3 GB free disk space                                                                                                                                                                                                                                            | Minimum requirements                                                                                                                                                                                                                                                                       |
| 1.1     | TODO: FIXME: update this!!! https://artifactory.niis.org/xroad-release-deb                                                                                                                                                                                                                       | X-Road package repository                                                                                                                                                                                                                                                                  |
| 1.1.1   | https://artifactory.niis.org/artifactory/xroad-dependencies-deb                                                                                                                                                                                                                                  | X-Road external dependencies repository                                                                                                                                                                                                                                                    |
| 1.2     | https://artifactory.niis.org/api/gpg/key/public                                                                                                                                                                                                                                                  | The repository key.<br /><br />Hash: `935CC5E7FA5397B171749F80D6E3973B`<br  />Fingerprint: `A01B FE41 B9D8 EAF4 872F  A3F1 FB0D 532C 10F6 EC5B`<br  />3rd party key server: [Ubuntu key server](https://keyserver.ubuntu.com/pks/lookup?search=0xfb0d532c10f6ec5b&fingerprint=on&op=index) |
| 1.3     |                                                                                                                                                                                                                                                                                                  | Account name in the user interface                                                                                                                                                                                                                                                         |
| 1.4     | TCP 4001 service for authentication certificate registration<br>TCP 80 distribution of the global configuration<br>TCP 443 distribution of the global configuration                                                                                                                              | Ports for inbound connections (from the external network to the Central Server)                                                                                                                                                                                                            |
| 1.4.1   | TCP 4002 management services                                                                                                                                                                                                                                                                     | Port for inbound connections from the management Security Server                                                                                                                                                                                                                           |
| 1.5     | TCP 80, 443 software updates                                                                                                                                                                                                                                                                     | Ports for outbound connections (from the Central Server to the external network)                                                                                                                                                                                                           |
| 1.6     | TCP 80 HTTP between the Central Server and the management services' Security Server<br>TCP 443 HTTP between the Central Server and the management services' Security Server<br>TCP 4000 user interface<br>TCP 4002 HTTPS between the Central Server and the management services' Security Server | Internal network ports, the user interface port, and management service ports for the management services' Security Server                                                                                                                                                                 |
| 1.7     |                                                                                                                                                                                                                                                                                                  | Central Server internal IP address(es) and hostname(s)                                                                                                                                                                                                                                     |
| 1.8     |                                                                                                                                                                                                                                                                                                  | Central Server public IP address, NAT address                                                                                                                                                                                                                                              |
| 1.9     | <by default, the server’s IP addresses and names are added to the certificate’s Distinguished Name (DN) field>                                                                                                                                                                                   | Information about the user interface TLS certificate                                                                                                                                                                                                                                       |
| 1.10    | <by default, the server’s IP addresses and names are added to the certificate’s Distinguished Name (DN) field>                                                                                                                                                                                   | Information about the services TLS certificate                                                                                                                                                                                                                                             |
| 1.11    | <by default, the server’s IP addresses and names are added to the certificate’s Distinguished Name (DN) field>                                                                                                                                                                                   | Information about the global configuration TLS certificate                                                                                                                                                                                                                                 |

It is strongly recommended to protect the Central Server from unwanted access using a firewall (hardware or software based). The firewall can be applied to both incoming and outgoing connections depending on the security requirements of the environment where the Central Server is deployed. It is recommended to allow incoming traffic to specific ports only from explicitly defined sources using IP filtering. **Special attention should be paid with the firewall configuration since incorrect configuration may leave the Central Server vulnerable to exploits and attacks.**

#### 2.2.1 Network Diagram

The network diagram below provides an example of a basic Central Server setup.

![network diagram](img/ig-cs_network_diagram.svg)

The table below lists the required connections between different components. Please note that required connections between Security Servers and trust services (OCSP service, time-stamping service) have been omitted from the diagram and the table below. Their configuration is described in [IG-SS](#Ref_IG-SS).

| **Connection Type** | **Source**                        | **Target**                    | **Target Ports**    | **Protocol** | **Note**                                                                                                                                                                         |
|---------------------|-----------------------------------|-------------------------------|---------------------|--------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Out                 | Monitoring Security Server        | X-Road Member Security Server | 5500, 5577          | tcp          | Operational and environmental monitoring data collection                                                                                                                         |
| In                  | X-Road Member Security Server     | Central Server                | 80, 443             | tcp          | Global configuration distribution                                                                                                                                                |
| In                  | X-Road Member Security Server     | Central Server                | 4001                | tcp          | Authentication certificate registration requests from X-Road Members' Security Servers                                                                                           |
| In                  | Management Security Server        | Central Server                | 80, 443, 4001, 4002 | tcp          | Source in the internal network. Management service requests from Management Security Server. Global configuration distribution. Authentication certificate registration requests |
| In                  | Monitoring Security Server        | Central Server                | 80, 443, 4001       | tcp          | Source in the internal network. Global configuration distribution. Authentication certificate registration requests                                                              |
| In                  | X-Road Member Security Server     | Management Security Server    | 5500, 5577          | tcp          | Management service requests from X-Road Members' Security Servers                                                                                                                |
| In                  | Central Monitoring Client         | Monitoring Security Server    | 8080, 8443          | tcp          | Source in the internal network                                                                                                                                                   |
| In                  | Admin, management REST API client | Central Server                | 4000                | tcp          | Source in the internal network                                                                                                                                                   |
| In                  | Admin                             | Management Security Server    | 4000                | tcp          | Source in the internal network                                                                                                                                                   |
| In                  | Admin                             | Monitoring Security Server    | 4000                | tcp          | Source in the internal network                                                                                                                                                   |

The table below lists the open ports for Central Server components utilizing the _loopback_ interface. A loopback interface is a virtual network interface on a computer, facilitating self-communication for processes and applications. This enables local communication and the ports must be accessible locally.

| **Component**        | **Ports** | **Protocol** | **Note**                                      |
|----------------------|-----------|--------------|-----------------------------------------------|
| Registration Service | 8084      | tcp          | For incoming requests to Registration Service |
| Management Service   | 8085      | tcp          | For incoming requests to Management Service   |
| PostgreSQL database  | 5432      | tcp          | Default PostgreSQL database port              |
| Signer               | 5560      | tcp          | Signer gRPC port                              |
| Audit log            | 514       | udp          |                                               |
| Secret Store         | 8200      | tcp          | Local Secret Store (OpenBao) port             |

### 2.3 Requirements to the Central Server

Minimum recommended hardware parameters:
- the server hardware (motherboard, CPU, network interface cards, storage system) must be supported by Ubuntu in general;
- a 64-bit dual-core Intel, AMD or compatible CPU;
- 2 GB RAM;
- 100 Mbps network interface card.

Requirements for software and settings:
- an installed and configured Ubuntu 24.04 LTS x86-64 operating system;
- the necessary connections are allowed in the firewall (reference data: 1.4; 1.4.1; 1.5; 1.6),
- if the Central Server has a private IP address, a corresponding NAT record must be created in the firewall (reference data: 1.8).

### 2.4 Preparing OS

- Add an X-Road system administrator user (reference data: 1.3) whom all roles in the user interface are granted to. 

  Add the new user with the command: `sudo adduser <username>`.

  User roles are discussed in detail in the X-Road Security Server User Guide [UG-SS](#Ref_UG-SS). Do not use the user name `xroad`, it is reserved for the X-Road system user.

- Ensure that the packages `locales` and `lsb-release` are present
  
  `sudo apt install locales lsb-release`

- Set the operating system locale.

  Add the following line to the file `/etc/environment`: `LC_ALL=en_US.UTF-8`  
  Ensure that the locale is generated: `sudo locale-gen en_US.UTF-8`

### 2.5 Setup Package Repository

Add the X-Road repository’s signing key to the list of trusted keys (**reference data: 1.2**):
```bash
curl -fsSL https://x-road.eu/gpg/key/public/niis-artifactory-public.gpg | sudo tee /usr/share/keyrings/niis-artifactory-keyring.gpg > /dev/null
```

Add X-Road package and external dependencies repositories (**reference data: 1.1, 1.1.1**)
```bash
TODO: FIXME: update release repo 
echo "deb [signed-by=/usr/share/keyrings/niis-artifactory-keyring.gpg] https://artifactory.niis.org/xroad-release-deb $(lsb_release -sc)-current main" | sudo tee /etc/apt/sources.list.d/xroad.list > /dev/null
echo "deb [signed-by=/usr/share/keyrings/niis-artifactory-keyring.gpg] https://artifactory.niis.org/xroad-dependencies-deb xroad external" | sudo tee /etc/apt/sources.list.d/xroad.list > /dev/null
```

### 2.6 Package Installation

Update package repository metadata:
```bash
sudo apt update
```

Issue the following command to install the Central Server packages:
```bash
sudo apt install xroad-centralserver
```

Upon the first installation of the Central Server software, the system asks for the following information.

- Account name for the user who will be granted the rights to perform all activities in the user interface (reference data: 1.3).

- Database server URL. Locally installed database is suggested as default but remote databases can be used as well. In case remote database is used, one should verify that the version of the local PostgreSQL client matches the version of the remote PostgreSQL server.

- Whether the database migrations should be skipped and handled manually instead. Usually automatic migrations should be used, but for legacy database support (like BDR1) it's possible to rely on manual operations instead. How to execute the database migrations manually is described in [Annex E Run Database Migrations Manually](#annex-e-run-database-migrations-manually).

- The Common Name of the owner of the user interface self-signed TLS certificate (subjectDN attribute) and its alternative names (subjectAltName). The certificate is used for securing connections to the user interface (reference data: 1.7; 1.9). The name and IP addresses detected from the operating system are suggested as default values. 

  The certificate owner’s Common Name must be entered in the format: `server.domain.tld`. 
  All IP addresses and domain names in use must be entered as alternative names in the format: `IP:1.2.3.4,IP:4.3.2.1,DNS:servername,DNS:servername2.domain.tld`

- Common name for the TLS certificate that is used for securing the HTTPS access point used for providing management services (reference data: 1.7; 1.10). The name and IP addresses detected from the operating system are suggested as default values.

  ATTENTION: The Central Server IP address or DNS name that Security Servers will use to connect to the server must be added to the certificate owner’s Distinguished Name (subjectDN) or alternative name forms (subjectAltName) list (reference data: 1.8).

  The certificate owner’s Common Name must be entered in the format: `server.domain.tld`
  All IP addresses and domain names in use must be entered as alternative names in the format: `IP:1.2.3.4,IP:4.3.2.1,DNS:servername,DNS:servername2.domain.tld`

- Identification of the TLS certificate that is used for securing the HTTPS access point used for global configuration distribution (reference data: 1.7; 1.11). The name and IP addresses detected from the operating system are suggested as default values.

  The certificate owner’s Distinguished Name must be entered in the format: `/CN=server.domain.tld`.
  All IP addresses and domain names in use must be entered as alternative names in the format: `IP:1.2.3.4,IP:4.3.2.1,DNS:servername,DNS:servername2.domain.tld`

#### 2.6.1 Configuring TLS Certificates for global configuration distribution

The installation process creates a self-signed TLS certificate for global configuration distribution. However, self-signed certificates are not recommended for production use, and should be substituted with certificate issued by a trusted Certificate Authority (CA).

To configure the Central Server to use a certificate issued by a trusted CA for serving global configurations over HTTPS, replace the existing certificate files (`global-conf.crt`) and its associated private key (`global-conf.key`), located in the `/etc/xroad/ssl/` directory.

Reload the nginx service for the certificate change to take effect.

```bash
systemctl reload nginx
```

#### 2.6.2 Provisioning TLS Certificates for management services and user interface

TLS certificates for the user interface and management services are automatically provisioned at the applications startup, using OpenBao PKI secrets engine (package `xroad-secret-store-local`).

#### 2.6.3 Provisioning TLS Certificates for gRPC services

TLS certificates for gRPC services are provisioned and rotated automatically, using OpenBao PKI secrets engine (package `xroad-secret-store-local`).

### 2.7 Pre-configuration for Registration Web Service

The registration web service is installed by package `xroad-center-registration-service`. The package is included in the Central Server installation by default.

Configuration parameters for registration web service are specified in the [UG-SYSPAR](#Ref_UG-SYSPAR) section "Registration service parameters".

**Note:** With new registration service, a maximum size limit (MAX_REQUEST_SIZE = 100 KB) is set for the authentication certificate SOAP message.

### 2.8 Pre-configuration for Management Web Service
The management web service is installed by package `xroad-center-management-service`. The package is included in the Central Server installation by default.

Configuration parameters for management web service are specified in the [UG-SYSPAR](#Ref_UG-SYSPAR) section "Management service parameters".

### 2.9 Post-Installation Checks

The installation is successful if the system services are started and the user interface is responding.

-   Ensure from the command line that relevant X-Road services are in the `running` state (example output follows). Notice that it is normal for the `xroad-confclient` to be in `stopped` state on the Central Server since it operates in one-shot mode. `xroad-secret-store-local` is run once to initialize OpenBao and then stops.
    ```bash
    sudo systemctl list-units "xroad*"

    UNIT                                      LOAD   ACTIVE SUB     DESCRIPTION
    xroad-base.service                        loaded active exited  X-Road initialization
    xroad-center-management-service.service   loaded active running X-Road Central Server Management Service
    xroad-center-registration-service.service loaded active running X-Road Central Server Registration Service
    xroad-center.service                      loaded active running X-Road Central Server
    xroad-secret-store-local.service          loaded active exited  X-Road OpenBao Auto Init Service
    xroad-signer.service                      loaded active running X-Road signer
    ```

-   Ensure that the Central Server user interface at https://SECURITYSERVER:4000/ (**reference data: 1.8; 1.6**) can be opened in a Web browser. To log in, use the account name chosen during the installation (**reference data: 1.3**). While the user interface is still starting up, the Web browser may display the "502 Bad Gateway" error.

## 3 Initial Configuration

### 3.1 Reference Data

Note: The information in empty cells will be entered at the latest during the installation, by the person performing the installation.

Attention: Data necessary for the functioning of the operating system is not included.

| **Ref** |        | **Explanation**                                                          |
|---------|--------|--------------------------------------------------------------------------|
| 2.1     |        | The X-Road instance identifier                                           |
| 2.2     |        | The external DNS name or IP address of the Central Server                |
| 2.3     |        | The softtoken PIN                                                        |
| 2.4     |        | Codes and descriptions of the member classes used in the X-Road instance |

### 3.2 Initializing the Central Server

The Central Server user interface can be accessed at https://CENTRALSERVER:4000/ (reference data: 1.7; 1.6)

1. Set the X-Road instance identifier (reference data: 2.1).
2. Set the Central Server public DNS hostname or public IP address (reference data: 2.2).
3. Set the PIN of the software token (reference data: 2.3). The PIN will be used to protect the keys stored in the software token. The PIN must be stored in a secure place, because it will be no longer possible to use or recover the private keys in the token once the PIN is lost.

### 3.3 Configuring the Central Server and the Management Services' Security Server

Upon the first configuration of the Central Server and the management services' Security Server, the following actions must be carried out.

Actions 7 and 8 must be performed in the management services' Security Server.

1. Generate the internal and external configuration signing keys. Refer to [UG-CS](#Ref_UG-CS) section "Generating a Configuration Signing Key".
2. Configure the member classes. Refer to [UG-CS](#Ref_UG-CS) section "Managing the Member Classes". (reference data: 2.4).
3. Configure the management service provider:
add the X-Road member who will be responsible for management services - [UG-CS](#Ref_UG-CS) section "Adding a Member";
add the subsystem that will provide the management services to the X-Road member - [UG-CS](#Ref_UG-CS) section "Adding a Subsystem to an X-Road Member";
appoint the subsystem as the management service provider - [UG-CS](#Ref_UG-CS) section "Appointing the Management Service Provider".
4. Configure the certification services. Refer to [UG-CS](#Ref_UG-CS) section "Managing the Approved Certification Services".
5. Configure the timestamping services. Refer to [UG-CS](#Ref_UG-CS) section "Managing the Approved Timestamping Services".
6. Verify that the global configuration generation succeeds (no global error messages should be displayed in the user interface at this point) and download the internal configuration anchor - [UG-CS](#Ref_UG-CS) section "Downloading the Configuration Anchor". The anchor is needed to set up the management services' Security Server.
7. Install and configure the management services' Security Server as described in [IG-SS](#Ref_IG-SS).
8. Register the management services' Security Server in the Central Server. Refer to [UG-SS](#Ref_UG-SS) section "Security Server Registration".
9. Complete the registration of the management services' Security Server - [UG-CS](#Ref_UG-CS) section "Registering a Member's Security Server".
10. Register the management service provider as a client of the management services' Security Server - [UG-CS](#Ref_UG-CS) section "Registering the Management Service Provider as a Security Server Client".
11. Add the management service provider as a client to the management services' Security Server. Refer to [UG-SS](#Ref_UG-SS) section "Adding a Security Server Client". (The client should appear in "Registered" state, as the association between the client and the Security Server was already registered in the Central Server in the previous step). If necessary, configure the signing keys and certificates for the client - [UG-SS](#Ref_UG-SS) section "Configuring a Signing Key and Certificate for a Security Server Client"
12. Configure the management services. Refer to [UG-CS](#Ref_UG-CS) section "Configuring the Management Services in The Management Services’ Security Server".

## 4 Installation Error Handling

Refer to X-Road 7. Central Server Installation Guide [IG-CS](#Ref_IG-CS), section "Installation Error Handling".

## Annex A Central Server Default Database Properties

`/etc/xroad/db.properties`

```properties
spring.datasource.username=centerui
spring.datasource.password=<randomly generated password stored is stored here>
spring.datasource.hikari.data-source-properties.currentSchema=centerui
spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/centerui_production
skip_migrations=false
```
## Annex B Database Users

| User     | Database            | Privileges               | Description                                                                                         |
|----------|---------------------|--------------------------|-----------------------------------------------------------------------------------------------------|
| centerui | centerui_production | CREATE,TEMPORARY,CONNECT | The database user used to create the schema and read/write the database during application runtime. |
| openbao  | openbao             | CREATE,TEMPORARY,CONNECT | The database user for local OpenBao if `xroad-secret-store-local` is used.                          | 
| postgres | ALL                 | ALL                      | PostgreSQL database default superuser.                                                              |

## Annex C `xroad-secret-store-local` default configuration

Local Secret Store uses OpenBao for secrets storage. The default configuration files are located in `/etc/openbao/`.<br/>
Files:<br/>
`openbao.hcl` - OpenBao main configuration file. Contains DB credentials and other options.<br/>
`root-token` - root OpenBao token.<br/>
`unseal-keys` - OpenBao unseal keys.