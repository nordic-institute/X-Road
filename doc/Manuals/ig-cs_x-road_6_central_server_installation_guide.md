# X-Road: Central Server Installation Guide

Version: 2.9
Doc. ID: IG-CS

---

## Version history
| Date       | Version | Description                      | Author                |
|------------|---------|----------------------------------|-----------------------|
| 01.12.2014 | 1.0     | Initial version                                         ||
| 19.01.2015 | 1.1     | License information added                               ||
| 02.02.2015 | 1.2     | References fixed                                        ||
| 18.03.2015 | 1.3     | Meta-package for central server                         ||
| 02.04.2015 | 1.4     | „sdsb” changed to „xroad”                               ||
| 30.06.2015 | 1.5     | Minor corrections done                                  ||
| 06.07.2015 | 1.6     | New repository address                                  ||
| 17.09.2015 | 1.7     | Notes about high availability added, references updated ||
| 18.09.2015 | 1.8     | Minor corrections done                                  ||
| 18.09.2015 | 2.0     | Editorial changes made                                  ||
| 16.12.2015 | 2.1     | Added installation instructions for monitoring          ||
| 09.12.2016 | 2.2     | Converted to markdown format          | Ilkka Seppälä |
| 20.12.2016 | 2.3     | Add chapter about additional configuration to central server's user manual          | Ilkka Seppälä |
| 20.01.2017 | 2.4     | Added license text and version history | Sami Kallio |
| 25.08.2017 | 2.5     | Update installation instructions concerning the support for environmental monitoring  | Ilkka Seppälä |
| 05.03.2018 | 2.6     | Added terms and abbreviations reference, links to references and actual documents | Tatu Repo | 
| 10.04.2018 | 2.7     | Updated chapter "[Installing the Support for Hardware Tokens](#26-installing-the-support-for-hardware-tokens)" with configurable parameters described in the configuration file 'devices.ini' | Cybernetica AS |
| 14.10.2018 | 2.8     | Update package repository address | Petteri Kivimäki |
| 15.11.2018 | 2.9     | Add Ubuntu 18.04 installation instructions | Jarkko Hyöty |
 
## Table of Contents

<!-- toc -->
<!-- vim-markdown-toc GFM -->

- [X-Road: Central Server Installation Guide](#x-road-central-server-installation-guide)
  - [Version history](#version-history)
  - [Table of Contents](#table-of-contents)
  - [License](#license)
  - [1. Introduction](#1-introduction)
    - [1.1 Target Audience](#11-target-audience)
    - [1.2 Terms and abbreviations](#12-terms-and-abbreviations)
    - [1.3 References](#13-references)
  - [2. Installation](#2-installation)
    - [2.1 Prerequisites to Installation](#21-prerequisites-to-installation)
    - [2.2 Reference Data](#22-reference-data)
    - [2.3 Requirements to the Central Server](#23-requirements-to-the-central-server)
    - [2.4 Preparing OS](#24-preparing-os)
    - [2.5 Installation](#25-installation)
    - [2.6 Installing the Support for Hardware Tokens](#26-installing-the-support-for-hardware-tokens)
    - [2.7 Installing the Support for Monitoring](#27-installing-the-support-for-monitoring)
    - [2.8 Post-Installation Checks](#28-post-installation-checks)
  - [3 Initial Configuration](#3-initial-configuration)
    - [3.1 Reference Data](#31-reference-data)
    - [3.2 Initializing the Central Server](#32-initializing-the-central-server)
    - [3.3 Configuring the Central Server and the Management Services' Security Server](#33-configuring-the-central-server-and-the-management-services-security-server)
  - [4 Additional configuration](#4-additional-configuration)
    - [4.1 Global configuration V1 support](#41-global-configuration-v1-support)
  - [5 Installation Error Handling](#5-installation-error-handling)
    - [5.1 Cannot Set LC_ALL to Default Locale](#51-cannot-set-lcall-to-default-locale)
    - [5.2 PostgreSQL Is Not UTF8 Compatible](#52-postgresql-is-not-utf8-compatible)
    - [5.3 Could Not Create Default Cluster](#53-could-not-create-default-cluster)
    - [5.4 Is Postgres Running on Port 5432?](#54-is-postgres-running-on-port-5432)

<!-- vim-markdown-toc -->
<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/.


## 1. Introduction

### 1.1 Target Audience

The intended audience of this installation guide are the X-Road central server administrators responsible for installing and configuring the X-Road central server software.
The document is intended for readers with a good knowledge of Linux server management, computer networks, and the X-Road functioning principles.

### 1.2 Terms and abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\].

### 1.3 References

1. <a id="Ref_UG-CS" class="anchor"></a>\[UG-CS\] Cybernetica AS. X-Road 6. Central Server User Guide. Document ID: [UG-CS](ug-cs_x-road_6_central_server_user_guide.md) 
2. <a id="Ref_IG-SS" class="anchor"></a>\[IG-SS\] Cybernetica AS. X-Road 6. Security Server Installation Guide. Document ID: [IG-SS](ig-ss_x-road_v6_security_server_installation_guide.md)
3. <a id="Ref_UG-SS" class="anchor"></a>\[UG-SS\] Cybernetica AS. X-Road 6. Security Server User Guide. Document ID: [UG-SS](ug-ss_x-road_6_security_server_user_guide.md)
4. <a id="Ref_IG-CSHA" class="anchor"></a>\[IG-CSHA\] Cybernetica AS. X-Road 6. Central Server High Availability Installation Guide. Document ID: [IG-CSHA](ig-csha_x-road_6_ha_installation_guide.md)
5. <a id="Ref_TERMS" class="anchor"></a>\[TA-TERMS\] X-Road Terms and Abbreviations. Document ID: [TA-TERMS](../terms_x-road_docs.md).

## 2. Installation

### 2.1 Prerequisites to Installation

The central server software assumes an existing installation of the Ubuntu operating system, on an x86-64bit platform.
To provide management services, a security server is installed alongside the central server.
The central server’s software can be installed both on physical and virtualized hardware (of the latter, Xen and Oracle VirtualBox have been tested).
Note: If the central server is a part of a cluster for achieving high availability, the database cluster must be installed and configured before the central server itself can be installed. Please refer to the Central Server High Availability Installation Guide [IG-CSHA](#Ref_IG-CSHA) for details.

### 2.2 Reference Data

Note: The information in empty cells will be determined at the latest during the server’s installation, by the person performing the installation.

Caution: Data necessary for the functioning of the operating system is not included.

| **Ref**              |                                                  | **Explanation**                                    |
|----------------------|--------------------------------------------------|----------------------------------------------------|
| 1.0 | Ubuntu 14.04 or 18.04, 64-bit, 2 GB RAM, 3 GB free disk space | Minimum requirements |
| 1.1 | https://artifactory.niis.org/xroad-release-deb | X-Road package repository |
| 1.2 | https://artifactory.niis.org/api/gpg/key/public | The repository key |
| 1.3 |  | Account name in the user interface |
| 1.4 | TCP 4001 service for authentication certificate registration<br>TCP 80 distribution of the global configuration | Ports for inbound connections (from the external network to the central server) |
| 1.5 | TCP 80 software updates | Ports for outbound connections (from the central server to the external network) |
| 1.6 | TCP 80 HTTP between the central server and the management services' security server<br>TCP 4000 user interface<br>TCP 4001 HTTPS between the central server and the management services' security server<br>TCP 4400 HTTP between central server and management services' security server | Internal network ports, the user interface port, and management service ports for the management services' security server |
| 1.7 |  | central server internal IP address(es) and hostname(s) |
| 1.8 |  | central server public IP address, NAT address |
| 1.9 | <by default, the server’s IP addresses and names are added to the certificate’s Distinguished Name (DN) field> | Information about the user interface TLS certificate |
| 1.10 | <by default, the server’s IP addresses and names are added to the certificate’s Distinguished Name (DN) field> | Information about the services TLS certificate |

### 2.3 Requirements to the Central Server

Minimum recommended hardware parameters:
- the server hardware (motherboard, CPU, network interface cards, storage system) must be supported by Ubuntu in general;
- a 64-bit dual-core Intel, AMD or compatible CPU;
- 2 GB RAM;
- 100 Mbps network interface card.

Requirements for software and settings:
- an installed and configured Ubuntu 14.04 or 18.04 LTS x86-64 operating system;
- the necessary connections are allowed in the firewall (reference data: 1.4; 1.5; 1.6),
- if the central server has a private IP address, a corresponding NAT record must be created in the firewall (reference data: 1.8).

### 2.4 Preparing OS

- Add a system user (reference data: 1.3) whom all roles in the user interface are granted to. 

  Add the new user with the command: `sudo adduser username`.  
  User roles are discussed in detail in the X-Road Security Server User Guide [UG-SS](#Ref_UG-SS).

- Set the operating system locale.

  Add the following line to the file /etc/environment: `LC_ALL=en_US.UTF-8`  
  Ensure that the locale is generated: `sudo locale-gen en_US.UTF-8`

### 2.5 Installation

Add the X-Road repository’s signing key to the list of trusted keys (**reference data: 1.2**):
  ```
  curl https://artifactory.niis.org/api/gpg/key/public | sudo apt-key add -
  ```

Add X-Road package repository (**reference data: 1.1**)

  ```
  sudo apt-add-repository -y "deb https://artifactory.niis.org/xroad-release-deb $(lsb_release -sc)-current main"
  ```

  Add nginx repository
  ```
  sudo apt-add-repository -y ppa:ondrej/nginx
  ```

  *Ubuntu 14.04 only*: Add openjdk repository
  ```
  sudo apt-add-repository -y ppa:openjdk-r/ppa
  ```

Issue the following commands to install the central server packages:

  ```
  sudo apt-get update
  sudo apt-get install xroad-centralserver
  ```

Upon the first installation of the central server software, the system asks for the following information.

- Account name for the user who will be granted the rights to perform all activities in the user interface (reference data: 1.3).
- The Distinguished Name of the owner of the user interface self-signed TLS certificate (subjectDN) and its alternative names (subjectAltName). The certificate is used for securing connections to the user interface (reference data: 1.7; 1.9). The name and IP addresses detected from the operating system are suggested as default values. 

  The certificate owner’s Distinguished Name must be entered in the format: `/CN=server.domain.tld`. 
  All IP addresses and domain names in use must be entered as alternative names in the format: `IP:1.2.3.4,IP:4.3.2.1,DNS:servername,DNS:servername2.domain.tld`

- Identification of the TLS certificate that is used for securing the HTTPS access point used for providing management services (reference data: 1.7; 1.10). The name and IP addresses detected from the operating system are suggested as default values.

  ATTENTION: The central server IP address or DNS name that security servers will use to connect to the server must be added to the certificate owner’s Distinguished Name (subjectDN) or alternative name forms (subjectAltName) list (reference data: 1.8).

  The certificate owner’s Distinguished Name must be entered in the format: `/CN=server.domain.tld`
  All IP addresses and domain names in use must be entered as alternative names in the format: `IP:1.2.3.4,IP:4.3.2.1,DNS:servername,DNS:servername2.domain.tld`

### 2.6 Installing the Support for Hardware Tokens

To configure support for hardware security tokens (smartcard, USB token, Hardware Security Module), act as follows.

1.  Install the hardware token support module using the following command:

        sudo apt-get install xroad-addon-hwtokens

2.  Install and configure a PKCS\#11 driver for the hardware token according to the manufacturer's instructions.

3.  Add the path to the PKCS\#11 driver to the file `/etc/xroad/devices.ini` (as described in the example given in the file).

4.  After installing and configuring the driver, the xroad-signer service must be restarted:

        sudo service xroad-signer restart

If you are running a high availability (HA) hardware token setup (such as a cluster with replicated tokens) then you may need to constrain the token identifier format such that the token replicas can be seen as the same token. The token identifier format can be changed in `/etc/xroad/devices.ini` via the `token_id_format` property (default value: `{moduleType}{slotIndex}{serialNumber}{label}`). Removing certain parts of the identifier will allow the HA setup to work correctly when one of the tokens goes down and is replaced by a replica. For example, if the token replicas are reported to be on different slots the `{slotIndex}` part should be removed from the identifier format.

Depending on the hardware token there may be a need for more additional configuration. All possible configurable parameters in the `/etc/xroad/devices.ini` are described in the next table.

Parameter   | Type    | Default Value | Explanation
----------- | ------- |-------------- | ---------------------------------------
*enabled*     | BOOLEAN | *true* | Indicates whether this device is enabled.
*library*     | STRING  |      | The path to the pkcs#11 library of the device driver.
*library_cant_create_os_threads* | BOOLEAN | *false* | Indicates whether application threads, which are executing calls to the pkcs#11 library, may not use native operating system calls to spawn new threads (in other words, the library’s code may not create its own threads). 
*os_locking_ok* | BOOLEAN | *false* | Indicates whether the pkcs#11 library may use the native operation system threading model for locking.
*sign_verify_pin* | BOOLEAN | *false* | Indicates whether the PIN should be entered per signing operation.
*token_id_format* | STRING | *{moduleType}{slotIndex}{serialNumber}{label}* | Specifies the identifier format used to uniquely identify a token. In certain high availability setups may need be constrained to support replicated tokens (eg. by removing the slot index part which may be diffirent for the token replicas).
*sign_mechanism*  | STRING | *CKM_RSA_PKCS* | Specifies the signing mechanism. Supported values: *CKM_RSA_PKCS*, *CKM_RSA_PKCS_PSS*.
*pub_key_attribute_encrypt*  | BOOLEAN | *true* | Indicates whether public key can be used for encryption.
*pub_key_attribute_verify* | BOOLEAN | *true* | Indicates whether public key can be used for verification.
*pub_key_attribute_wrap* | BOOLEAN | | Indicates whether public key can be used for wrapping other keys.
*pub_key_attribute_allowed_mechanisms* | STRING LIST | | Specifies public key allowed mechanisms. Supported values: *CKM_RSA_PKCS*, *CKM_SHA256_RSA_PKCS*, *CKM_SHA384_RSA_PKCS*, *CKM_SHA512_RSA_PKCS*, and *CKM_RSA_PKCS_PSS*, *CKM_SHA256_RSA_PKCS_PSS*, *CKM_SHA384_RSA_PKCS_PSS*, *CKM_SHA512_RSA_PKCS_PSS*.
*priv_key_attribute_sensitive* | BOOLEAN | *true* | Indicates whether private key is sensitive.
*priv_key_attribute_decrypt* | BOOLEAN | *true* | Indicates whether private key can be used for encryption.
*priv_key_attribute_sign* | BOOLEAN | *true* | Indicates whether private key can be used for signing.
*priv_key_attribute_unwrap* | BOOLEAN | | Indicates whether private key can be used for unwrapping wrapped keys.
*priv_key_attribute_allowed_mechanisms* | STRING LIST | | Specifies private key allowed mechanisms. Supported values: *CKM_RSA_PKCS*, *CKM_SHA256_RSA_PKCS*, *CKM_SHA384_RSA_PKCS*, *CKM_SHA512_RSA_PKCS*, and *CKM_RSA_PKCS_PSS*, *CKM_SHA256_RSA_PKCS_PSS*, *CKM_SHA384_RSA_PKCS_PSS*, *CKM_SHA512_RSA_PKCS_PSS*.

**Note 1:** Only parameter *library* is mandatory, all the others are optional.  
**Note 2:** The item separator of the type STRING LIST is ",".

### 2.7 Installing the Support for Monitoring

The optional configuration for monitoring parameters is installed by package xroad-centralserver-monitoring. This package also includes the components that validate the updated xml monitoring configuration. The package is included in the central server installation by default.

The central monitoring client may be configured as specified in the [UG-CS](#Ref_UG-CS).

### 2.8 Post-Installation Checks

The installation is successful if the system services are started and the user interface is responding.

-   Ensure from the command line that relevant X-Road services are in the `running` state (example output follows). Notice that it is normal for the xroad-confclient to be in `stopped` state on the central server since it operates in one-shot mode.

    - Ubuntu 14.04
        ```
        sudo initctl list | grep "^xroad-"

        xroad-jetty start/running, process 19796
        xroad-confclient stop/waiting, process 19563
        xroad-signer start/running, process 19393
        ```

    - Ubuntu 18.04
        ```
        sudo systemctl list-units "xroad*"

        UNIT                     LOAD   ACTIVE SUB     DESCRIPTION
        xroad-jetty.service      loaded active running X-Road Jetty server
        xroad-signer.service     loaded active running X-Road signer
        ```

-   Ensure that the central server user interface at https://SECURITYSERVER:4000/ (**reference data: 1.8; 1.6**) can be opened in a Web browser. To log in, use the account name chosen during the installation (**reference data: 1.3**). While the user interface is still starting up, the Web browser may display the “502 Bad Gateway” error.

## 3 Initial Configuration

### 3.1 Reference Data

Note: The information in empty cells will be entered at the latest during the installation, by the person performing the installation.

Attention: Data necessary for the functioning of the operating system is not included.

| **Ref** |        | **Explanation**                                           |
|---------|--------|-----------------------------------------------------------|
| 2.1     |        | The X-Road instance identifier                            |
| 2.2     |        | The external DNS name or IP address of the central server |
| 2.3     |        | The softtoken PIN                                         |
| 2.4     |        | Codes and descriptions of the member classes used in the X-Road instance |


### 3.2 Initializing the Central Server

The central server user interface can be accessed at https://CENTRALSERVER:4000/ (reference data: 1.7; 1.6)

1. Set the X-Road instance identifier (reference data: 2.1).
2. Set the central server public DNS hostname or public IP address (reference data: 2.2).
3. Set the PIN of the software token (reference data: 2.3). The PIN will be used to protect the keys stored in the software token. The PIN must be stored in a secure place, because it will be no longer possible to use or recover the private keys in the token once the PIN is lost.

### 3.3 Configuring the Central Server and the Management Services' Security Server

Upon the first configuration of the central server and the management services' security server, the following actions must be carried out.

Actions 7 and 8 must be performed in the management services' security server.

1. Generate the internal and external configuration signing keys. Refer to [UG-CS](#Ref_UG-CS) section „Generating a Configuration Signing Key“.
2. Configure the member classes. Refer to [UG-CS](#Ref_UG-CS) section „Managing the Member Classes“. (reference data: 2.4).
3. Configure the management service provider:
add the X-Road member who will be responsible for management services - [UG-CS](#Ref_UG-CS) section „Adding a Member“;
add the subsystem that will provide the management services to the X-Road member - [UG-CS](#Ref_UG-CS) section “Adding a Subsystem to an X-Road Member”;
appoint the subsystem as the management service provider - [UG-CS](#Ref_UG-CS) section “Appointing the Management Service Provider”.
4. Configure the certification services. Refer to [UG-CS](#Ref_UG-CS) section „Managing the Approved Certification Services“.
5. Configure the timestamping services. Refer to [UG-CS](#Ref_UG-CS) section „Managing the Approved Timestamping Services“.
6. Verify that the global configuration generation succeeds (no global error messages should be displayed in the user interface at this point) and download the internal configuration anchor - [UG-CS](#Ref_UG-CS) section “Downloading the Configuration Anchor”. The anchor is needed to set up the management services' security server.
7. Install and configure the management services' security server as described in [IG-SS](#Ref_IG-SS).
8. Register the management services' security server in the central server. Refer to [UG-SS](#Ref_UG-SS) section „Security Server Registration“.
9. Complete the registration of the management services' security server - [UG-CS](#Ref_UG-CS) section “Registering a Member's Security Server”.
10. Register the management service provider as a client of the management services' security server - [UG-CS](#Ref_UG-CS) section “Registering the Management Service Provider as a Security Server Client”.
11. Add the management service provider as a client to the management services' security server. Refer to [UG-SS](#Ref_UG-SS) section „Adding a Security Server Client”. (The client should appear in “Registered” state, as the association between the client and the security server was already registered in the central server in the previous step). If necessary, configure the signing keys and certificates for the client - [UG-SS](#Ref_UG-SS) section „Configuring a Signing Key and Certificate for a Security Server Client”
12. Configure the management services. Refer to [UG-CS](#Ref_UG-CS) section „Configuring the Management Services in The Management Services’ Security Server”.


## 4 Additional configuration

### 4.1 Global configuration V1 support

The support for global configuration version V1 has been removed in X-Road version 6.20. The central server produces only V2 global configuration which is expected by security servers from version 6.8.x and up.

## 5 Installation Error Handling

### 5.1 Cannot Set LC_ALL to Default Locale

If running the locale command results in the error message

`locale: Cannot set LC_ALL to default locale: No such file or directory`

then the support for the particular language has not been installed. To install it, run the command (example uses the English language):

`sudo apt-get install language-pack-en`

Then, to update the system’s locale files, run the following commands (this example uses the US locale):

`sudo locale-gen en_US.UTF-8`<br>
`sudo update-locale en_US.UTF-8`

Set the operating system locale. Add following line to /etc/environment file.

`LC_ALL=en_US.UTF-8`

After updating the system’s locale settings, it is recommended to restart the operating system.

### 5.2 PostgreSQL Is Not UTF8 Compatible

If the central server installation is aborted, with the error message

`postgreSQL is not UTF8 compatible`

then the PostgreSQL package is installed with the wrong locale. One way to fix it is to remove the data store created upon the PostgreSQL installation and recreate it with the correct encoding. WARNING: All data in the database will be erased!

`sudo pg_dropcluster --stop 9.3 main`<br>
`LC_ALL="en_US.UTF-8" sudo pg_createcluster --start 9.3 main`

To complete the interrupted installation, run the command:

`sudo apt-get -f install`

### 5.3 Could Not Create Default Cluster

If the following error message is displayed during PostgreSQL installation

`Error: The locale requested by the environment is invalid.`<br>
`Error: could not create default cluster. Please create it manually with pg_createcluster 9.3 main –start`

Use the following command to create the PostgreSQL data cluster:

`LC_ALL="en_US.UTF-8" sudo  pg_createcluster --start 9.3 main`

The interrupted installation can be finished using

`sudo apt-get -f install`

### 5.4 Is Postgres Running on Port 5432?

If the following error message appears during installation

`Is postgres running on port 5432 ?`<br>
`Aborting installation! please fix issues and rerun with apt-get -f install`

Then check if any of the following errors occurred during the installation of PostgreSQL.

- Error installing the data cluster. Refer to section 4.3.
- The PostgreSQL data cluster installed during the installation of the security server is not configured to listen on port 5432. To verify and configure the listening port, edit the PostgreSQL configuration file in /etc/postgresql/9.3/main/postgresql.conf. If you change the listening port, the postgresql service must be restarted.

The interrupted installation can be finished using

`sudo apt-get -f install`
