
| ![European Union / European Regional Development Fund / Investing in your future](img/eu_rdf_75_en.png "Documents that are tagged with EU/SF logos must keep the logos until 1.1.2022, if it has not stated otherwise in the documentation. If new documentation is created  using EU/SF resources the logos must be tagged appropriately so that the deadline for logos could be found.") |
| -------------------------: |

# Security Server Installation Guide for Ubuntu <!-- omit in toc -->

**X-ROAD 6**

Version: 2.25  
Doc. ID: IG-SS

---


## Version history <!-- omit in toc -->

 Date       | Version | Description                                                     | Author
 ---------- | ------- | --------------------------------------------------------------- | --------------------
 01.12.2014 | 1.0     | Initial version                                                 |
 19.01.2015 | 1.1     | License information added                                       |
 18.03.2015 | 1.2     | Meta-package for security server added. Legacy securelog module removed |
 02.04.2015 | 1.3     | “sdsb” change to “xroad”                                        |
 27.05.2015 | 1.4     | Some typos fixed                                                |
 30.06.2015 | 1.5     | Minor corrections done                                          |
 06.07.2015 | 1.6     | New repository address                                          |
 18.09.2015 | 1.7     | Reference data in [3.2](#32-reference-data) updated                                   |
 18.09.2015 | 2.0     | Editorial changes made                                          |
 13.10.2015 | 2.1     | Editorial changes made                                          |
 10.12.2015 | 2.2     | Updated the installing of the support for hardware tokens ([2.7](#27-installing-the-support-for-hardware-tokens)) |
 17.12.2015 | 2.3     | Added *xroad-addon-wsdlvalidator* package                       |
 19.05.2016 | 2.4     | Merged changes from xtee6-doc repo. Updated table [2.2](#22-reference-data) with p 1.12, added chapter [2.8](#28-installing-support-for-monitoring) and updated [3.2](#32-reference-data). |
 30.09.2016 | 2.5     | Added chapter „[Different versions of xroad-\* package after successful upgrade](#45-different-versions-of-xroad--packages-after-successful-upgrade)“. |
 07.12.2016 | 2.6     | Added operational data monitoring packages. 2 GB RAM -&gt; 3 GB RAM |
 23.02.2017 | 2.7     | Converted to Github flavoured Markdown, added license text, adjusted tables for better output in PDF | Toomas Mölder
 13.04.2017 | 2.8     | Added token ID formatting                                       | Cybernetica AS
 25.08.2017 | 2.9     | Update environmental monitoring installation information | Ilkka Seppälä
 15.09.2017 | 2.10    | Added package with configuration specific to Estonia xroad-securityserver-ee | Cybernetica AS
 05.03.2018 | 2.11    | Added terms and abbreviations reference and document links | Tatu Repo
 10.04.2018 | 2.12    | Updated chapter "[Installing the Support for Hardware Tokens](#27-installing-the-support-for-hardware-tokens)" with configurable parameters described in the configuration file 'devices.ini' | Cybernetica AS
 14.10.2018 | 2.13    | Update package repository address | Petteri Kivimäki
 25.10.2018 | 2.14    | Add RHEL7 as supported platform, update section 2.2 Reference data | Petteri Kivimäki
 15.11.2018 | 2.15    | Add Ubuntu 18 installation instructions | Jarkko Hyöty
 28.01.2018 | 2.16    | Update port 2080 documentation | Petteri Kivimäki
 30.05.2019 | 2.17    | Added package installation instructions on chapter "[2.4 Preparing OS](#24-preparing-os)" | Raul Martinez
 11.09.2019 | 2.18    | Remove Ubuntu 14.04 from supported platforms | Jarkko Hyöty
 20.09.2019 | 2.19    | Add instructions for using remote databases | Ilkka Seppälä
 12.04.2020 | 2.20    | Add note about the default value of the *connector-host* property in the EE-package | Petteri Kivimäki
 29.04.2020 | 2.21    | Add instructions how to use remote database located in Microsoft Azure | Ilkka Seppälä
 12.06.2020 | 2.22    | Update reference data regarding JMX listening ports | Petteri Kivimäki
 24.06.2020 | 2.23    | Add repository sign key details in section [2.2 Reference data](#22-reference-data) | Petteri Kivimäki
 24.06.2020 | 2.24    | Remove environmental and operational monitoring daemon JMX listening ports from section [2.2 Reference data](#22-reference-data) | Petteri Kivimäki
 24.06.2020 | 2.25    | Update ports information in section [2.2 Reference data](#22-reference-data), add section [2.2.1 Network Diagram](#221-network-diagram) | Petteri Kivimäki
      
## Table of Contents <!-- omit in toc -->

<!-- toc -->

- [License](#license)
- [1 Introduction](#1-introduction)
  - [1.1 Target Audience](#11-target-audience)
  - [1.2 Terms and abbreviations](#12-terms-and-abbreviations)
  - [1.3 References](#13-references)
- [2 Installation](#2-installation)
  - [2.1 Supported Platforms](#21-supported-platforms)
  - [2.2 Reference Data](#22-reference-data)
    - [2.2.1 Network Diagram](#221-network-diagram)
  - [2.3 Requirements for the Security Server](#23-requirements-for-the-security-server)
  - [2.4 Preparing OS](#24-preparing-os)
  - [2.5 Installation](#25-installation)
  - [2.6 Post-Installation Checks](#26-post-installation-checks)
  - [2.7 Installing the Support for Hardware Tokens](#27-installing-the-support-for-hardware-tokens)
  - [2.8 Installing the Support for Environmental Monitoring](#28-installing-the-support-for-environmental-monitoring)
  - [2.9 Remote Database Post-Installation Tasks](#29-remote-database-post-installation-tasks)
- [3 Security Server Initial Configuration](#3-security-server-initial-configuration)
  - [3.1 Prerequisites](#31-prerequisites)
  - [3.2 Reference Data](#32-reference-data)
  - [3.3 Configuration](#33-configuration)
- [4 Installation Error handling](#4-installation-error-handling)
  - [4.1 Cannot Set LC\_ALL to Default Locale](#41-cannot-set-lcall-to-default-locale)
  - [4.2 PostgreSQL Is Not UTF8 Compatible](#42-postgresql-is-not-utf8-compatible)
  - [4.3 Could Not Create Default Cluster](#43-could-not-create-default-cluster)
  - [4.4 Is Postgres Running On Port 5432?](#44-is-postgres-running-on-port-5432)
  - [4.5 Different versions of xroad-\* packages after successful upgrade](#45-different-versions-of-xroad--packages-after-successful-upgrade)

<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/

## 1 Introduction


### 1.1 Target Audience

The intended audience of this Installation Guide are X-Road Security server system administrators responsible for installing and using X-Road software. The daily operation and maintenance of the security server is covered by its User Guide \[[UG-SS](#Ref_UG-SS)\].

The document is intended for readers with a moderate knowledge of Linux server management, computer networks, and the X-Road working principles.

### 1.2 Terms and abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\].

### 1.3 References

1.  <a id="Ref_UG-SS" class="anchor"></a>\[UG-SS\] Cybernetica AS. X-Road 6. Security Server User Guide. Document ID: [UG-SS](ug-ss_x-road_6_security_server_user_guide.md)

2.  <a id="Ref_TERMS" class="anchor"></a>\[TA-TERMS\] X-Road Terms and Abbreviations. Document ID: [TA-TERMS](../terms_x-road_docs.md).

3. <a name="Ref_UG-SYSPAR" class="anchor"></a>\[UG-SYSPAR\] X-Road: System Parameters User Guide. Document ID:
[UG-SYSPAR](ug-syspar_x-road_v6_system_parameters.md).

## 2 Installation


### 2.1 Supported Platforms

The security server runs on the following platforms:

* Ubuntu Server 18.04 Long-Term Support (LTS) operating system on a 64-bit platform. The security server software is distributed as .deb packages through the official X-Road repository at https://artifactory.niis.org/xroad-release-deb/
* Red Hat Enterprise Linux 7.3 (RHEL7) or newer operating system. See [IG-SS-RHEL7](ig-ss_x-road_v6_security_server_installation_guide_for_rhel7.md) for more information.

The software can be installed both on physical and virtualized hardware (of the latter, Xen and Oracle VirtualBox have been tested).


### 2.2 Reference Data

*Note*: The information in empty cells should be determined before the server’s installation, by the person performing the installation.

**Caution**: Data necessary for the functioning of the operating system is not included.


 **Ref** |                                        | **Explanation**
 ------ | --------------------------------------- | ----------------------------------------------------------
 1.0    | Ubuntu 18.04, 64-bit<br>3 GB RAM, 3 GB free disk space | Minimum requirements
 1.1    | https://artifactory.niis.org/xroad-release-deb               | X-Road package repository
 1.2    | https://artifactory.niis.org/api/gpg/key/public | The repository key.<br /><br />Hash: `935CC5E7FA5397B171749F80D6E3973B`<br  />Fingerprint: `A01B FE41 B9D8 EAF4 872F  A3F1 FB0D 532C 10F6 EC5B`<br  />3rd party key server: [SKS key servers](http://pool.sks-keyservers.net/pks/lookup?op=vindex&hash=on&fingerprint=on&search=0xFB0D532C10F6EC5B)
 1.3    |                                         | Account name in the user interface
 1.4    | **Inbound ports from external network** | Ports for inbound connections from the external network to the security server
 &nbsp; | TCP 5500                                | Message exchange between security servers
 &nbsp; | TCP 5577                                | Querying of OCSP responses between security servers
 1.5    | **Outbound ports to external network**  | Ports for outbound connections from the security server to the external network
 &nbsp; | TCP 5500                                | Message exchange between security servers
 &nbsp; | TCP 5577                                | Querying of OCSP responses between security servers
 &nbsp; | TCP 4001                                | Communication with the central server
 &nbsp; | TCP 80                                  | Downloading global configuration from the central server
 &nbsp; | TCP 80,443                              | Most common OCSP and time-stamping services
 1.6    | **Inbound ports from internal network** | Ports for inbound connections from the internal network to the security server
 &nbsp; | TCP 4000                                | User interface and management REST API (local network). **Must not be accessible from the internet!**
 &nbsp; | TCP 80, 443                             | Information system access points (in the local network). **Must not be accessible from the external network without strong authentication. If open to the external network, IP filtering is strongly recommended.**
 1.7    | **Outbound ports to internal network**  | Ports for inbound connections from the internal network to the security server 
 &nbsp; | TCP 80, 443, *other*                    | Producer information system endpoints
 &nbsp; | TCP 2080                                | Message exchange between security server and operational data monitoring daemon (by default on localhost)
 1.8  |                                           | Security server internal IP address(es) and hostname(s)
 1.9  |                                           | Security server public IP address, NAT address
 1.10 | &lt;by default, the server’s IP addresses and names are added to the certificate’s Distinguished Name (DN) field&gt; | Information about the user interface TLS certificate
 1.11 | &lt;by default, the server’s IP addresses and names are added to the certificate’s Distinguished Name (DN) field&gt; | Information about the services TLS certificate

It is strongly recommended to protect the security server from unwanted access using a firewall (hardware or software based). The firewall can be applied to both incoming and outgoing connections depending on the security requirements of the environment where the security server is deployed. It is recommended to allow incoming traffic to specific ports only from explicitly defined sources using IP filtering. **Special attention should be paid with the firewall configuration since incorrect configuration may leave the security server vulnerable to exploits and attacks.**

#### 2.2.1 Network Diagram

The network diagram below provides an example of a basic Security Server setup.

![network diagram](img/ig-ss_network_diagram_Ubuntu.png)

### 2.3 Requirements for the Security Server

Minimum recommended hardware parameters:

-   the server’s hardware (motherboard, CPU, network interface cards, storage system) must be supported by Ubuntu in general;

-   a 64-bit dual-core Intel, AMD or compatible CPU; AES instruction set support is highly recommended;

-   3 GB RAM;

-   a 100 Mbps network interface card;

-   if necessary, interfaces for the use of hardware tokens.

Requirements to software and settings:

-   an installed and configured Ubuntu 18.04 LTS x86-64 operating system;

-   if the security server is separated from other networks by a firewall and/or NAT, the necessary connections to and from the security server are allowed (**reference data: 1.4; 1.5; 1.6; 1.7**). The enabling of auxiliary services which are necessary for the functioning and management of the operating system (such as DNS, NTP, and SSH) stay outside the scope of this guide;

-   if the security server has a private IP address, a corresponding NAT record must be created in the firewall (**reference data: 1.9**).


### 2.4 Preparing OS

-   Add system user (**reference data: 1.3**) whom all roles in the user interface are granted to. Add a new user with the command

        sudo adduser <username>

    User roles are discussed in detail in X-Road Security Server User Guide \[[UG-SS](#Ref_UG-SS)\].

-   Set the operating system locale. Add following line to the `/etc/environment` file.

        LC_ALL=en_US.UTF-8

-   Ensure that the packages `locales` and `software-properties-common` are present

        sudo apt-get install locales software-properties-common

-   Ensure that the locale is available

        sudo locale-gen en_US.UTF-8


### 2.5 Installation

To install the X-Road security server software on *Ubuntu* operating system, follow these steps.

1.  Add the X-Road repository’s signing key to the list of trusted keys (**reference data: 1.2**):

        curl https://artifactory.niis.org/api/gpg/key/public | sudo apt-key add -

2.  Add X-Road package repository (**reference data: 1.1**)

        sudo apt-add-repository -y "deb https://artifactory.niis.org/xroad-release-deb $(lsb_release -sc)-current main"

3. (Optional step) If you want to use remote database server instead of the default locally installed one, you need to pre-create a configuration file containing at least the database administrator master password. This can be done by performing the following steps:

        sudo touch /etc/xroad.properties
        sudo chown root:root /etc/xroad.properties
        sudo chmod 600 /etc/xroad.properties
        
    Edit `/etc/xroad.properties` contents. See the example below. Replace parameter values with your own.

        postgres.connection.password = {database superuser password}
        postgres.connection.user = {database superuser name, postgres by default}

    If your remote database is in Microsoft Azure the connection usernames need to be in format `username@servername`. Therefore you need to precreate also db.properties file as follows. First create the directory and file.

      sudo mkdir /etc/xroad
      sudo chown xroad:xroad /etc/xroad
      sudo chmod 751 /etc/xroad
      sudo touch /etc/xroad/db.properties
      sudo chown xroad:xroad /etc/xroad/db.properties
      sudo chmod 640 /etc/xroad/db.properties

    Then edit `/etc/xroad/db.properties` contents. See the example below. Replace parameter values with your own.

        serverconf.hibernate.connection.username = serverconf@servername
        serverconf.hibernate.connection.password = H1nGmB3uqtU7IJ82qqEaMaH2ozXBBkh0
        op-monitor.hibernate.connection.username = opmonitor@servername
        op-monitor.hibernate.connection.password = V8jCARSA7RIuCQWr59Hw3UK9zNzBeP2l
        messagelog.hibernate.connection.username = messagelog@servername
        messagelog.hibernate.connection.password = 1wmJ-bK39nbA4EYcTS9MgdjyJewPpf_w

    In case remote database is used, one should verify that the version of the local PostgreSQL client matches the version of the remote PostgreSQL server.

4.  Issue the following commands to install the security server packages (use package xroad-securityserver-ee to include configuration specific to Estonia; use package xroad-securityserver-fi to include configuration specific to Finland):

        sudo apt-get update
        sudo apt-get install xroad-securityserver

Upon the first installation of the packages, the system asks for the following information.

-   Account name for the user who will be granted the rights to perform all activities in the user interface (**reference data: 1.3**).

-   Database server URL. Locally installed database is suggested as default but remote databases can be used as well. In case remote database is used, one should verify that the version of the local PostgreSQL client matches the version of the remote PostgreSQL server.

-   The Distinguished Name of the owner of the **user interface's and management REST API's** self-signed TLS certificate (*Subject DN*) and its alternative names (*subjectAltName*) (**reference data: 1.8; 1.10**). The certificate is used for securing connections to the user interface and to the management REST APIs.
    The name and IP addresses detected from the operating system are suggested as default values.

    -   The *Subject DN* must be entered in the format:

            /CN=server.domain.tld

    -   All IP addresses and domain names in use must be entered as alternative names in the format:

            IP:1.2.3.4,IP:4.3.2.1,DNS:servername,DNS:servername2.domain.tld

-   The Distinguished Name of the owner of the TLS certificate that is used for securing the HTTPS access point of information systems (**reference data: 1.8; 1.11**).
    The name and IP addresses detected from the system are suggested as default values.

    -   The *Subject DN* must be entered in the format:

            /CN=server.domain.tld

    -   All IP addresses and domain names in use must be entered as alternative names in the format:

            IP:1.2.3.4,IP:4.3.2.1,DNS:servername,DNS:servername2.domain.tld

The meta-package `xroad-securityserver` also installs metaservices module `xroad-addon-metaservices`, messagelog module `xroad-addon-messagelog` and WSDL validator module `xroad-addon-wsdlvalidator`. Both meta-packages `xroad-securityserver-ee` and `xroad-securityserver-fi` install operational data monitoring module `xroad-addon-opmonitoring`.

**N.B.** In case configuration specific to Estonia (package `xroad-securityserver-ee`) is installed, connections from client applications are restricted to localhost by default. To enable client application connections from external sources, the value of the `connector-host` property must be overridden in the `/etc/xroad/conf.d/local.ini` configuration file. Changing the system parameter values is explained in the System Parameters User Guide \[[UG-SS](#Ref_UG-SS)\].

### 2.6 Post-Installation Checks

The installation is successful if system services are started and the user interface is responding.

-   Ensure from the command line that X-Road services are in the `running` state (example output follows):

    - Ubuntu 18.04
        ```
        sudo systemctl list-units "xroad*"

        UNIT                     LOAD   ACTIVE SUB     DESCRIPTION
        xroad-confclient.service loaded active running X-Road confclient
        xroad-jetty.service      loaded active running X-Road Jetty server
        xroad-monitor.service    loaded active running X-Road Monitor
        xroad-proxy.service      loaded active running X-Road Proxy
        xroad-signer.service     loaded active running X-Road signer
        ```

-   Ensure that the security server user interface at https://SECURITYSERVER:4000/ (**reference data: 1.8; 1.6**) can be opened in a Web browser. To log in, use the account name chosen during the installation (**reference data: 1.3**). While the user interface is still starting up, the Web browser may display the “502 Bad Gateway” error.


### 2.7 Installing the Support for Hardware Tokens

To configure support for hardware security tokens (smartcard, USB token, Hardware Security Module), act as follows.

1.  Install the hardware token support module using the following command:

        sudo apt-get install xroad-addon-hwtokens

2.  Install and configure a PKCS\#11 driver for the hardware token according to the manufacturer's instructions.

3.  Add the path to the PKCS\#11 driver to the file `/etc/xroad/devices.ini` (as described in the example given in the file).

4.  After installing and configuring the driver, the `xroad-signer` service must be restarted:

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


### 2.8 Installing the Support for Environmental Monitoring

The support for environmental monitoring functionality on a security server is provided by package xroad-monitor that is installed by default. The package installs and starts the `xroad-monitor` process that will gather and make available the monitoring information.

### 2.9 Remote Database Post-Installation Tasks

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

-   The global configuration anchor file (**reference data: 2.1**).

    **Please verify anchor hash value with the published value.**

If the configuration is successfully downloaded, the system asks for the following information.

-   The security server owner’s member class (**reference data: 2.2**).

-   The security server owner’s member code (**reference data: 2.3**).
    If the member class and member code are correctly entered, the system displays the security server owner’s name as registered in the X-Road center.

-   Security server code (**reference data: 2.4**), which is chosen by the security server administrator and which has to be unique across all the security servers belonging to the same X-Road member.

-   Software token’s PIN (**reference data: 2.5**). The PIN will be used to protect the keys stored in the software token. The PIN must be stored in a secure place, because it will be no longer possible to use or recover the private keys in the token once the PIN has been lost.


## 4 Installation Error handling


### 4.1 Cannot Set LC\_ALL to Default Locale

If running the locale command results in the error message

    locale: Cannot set LC_ALL to default locale: No such file or directory,

then the support for this particular language has not been installed. To install it, run the command (the example uses the English language):

    sudo apt-get install language-pack-en

Then, to update the system’s locale files, run the following commands (the example uses the US locale):

    sudo locale-gen en_US.UTF-8
    sudo update-locale en_US.UTF-8

Set operating system locale. Add following line to `/etc/environment` file:

    LC_ALL=en_US.UTF-8

After updating the system’s locale settings, it is recommended to restart the operating system.


### 4.2 PostgreSQL Is Not UTF8 Compatible

If the security server installation is aborted with the error message

    postgreSQL is not UTF8 compatible,

then the PostgreSQL package is installed with a wrong locale. One way to resolve it is to remove the data store created upon the PostgreSQL installation and recreate it with the correct encoding.

**WARNING**: All data in the database will be erased!

    sudo pg_dropcluster --stop 9.3 main
    LC_ALL="en_US.UTF-8" sudo pg_createcluster --start 9.3 main

To complete the interrupted installation, run the command

    sudo apt-get -f install


### 4.3 Could Not Create Default Cluster

If the following error message is displayed during PostgreSQL installation:

    Error: The locale requested by the environment is invalid.
    Error: could not create default cluster. Please create it manually with pg_createcluster 9.3 main –start,

use the following command to create the PostgreSQL data cluster:

    LC_ALL="en_US.UTF-8" sudo pg_createcluster --start 9.3 main

The interrupted installation can be finished using

    sudo apt-get -f install


### 4.4 Is Postgres Running On Port 5432?

If the following error message appears during installation

    Is postgres running on port 5432 ?
    Aborting installation! please fix issues and rerun with apt-get -f install,

check if any of the following errors occurred during the installation of PostgreSQL.

-   Error installing the data cluster. Refer to section [“Could not create default cluster”](#43-could-not-create-default-cluster).

-   The PostgreSQL data cluster installed during the installation of the security server is not configured to listen on port 5432. To verify and configure the listening port, edit the PostgreSQL configuration file in `/etc/postgresql/9.3/main/postgresql.conf`. If you change the listening port, the postgresql service must be restarted.

The interrupted installation can be finished using

    sudo apt-get -f install


### 4.5 Different versions of xroad-\* packages after successful upgrade

Sometimes, after using `sudo apt-get upgrade` command, some of the packages are not upgraded. In the following example `xroad-securityserver` package version is still 6.8.3 although other packages are upgraded to 6.8.5:

    # sudo dpkg -l | grep xroad-
    ii xroad-addon-messagelog 6.8.5.20160929134539gitfe60f90
    ii xroad-addon-metaservices 6.8.5.20160929134539gitfe60f90
    ii xroad-addon-wsdlvalidator 6.8.5.20160929134539gitfe60f90
    ii xroad-common 6.8.5.20160929134539gitfe60f90
    ii xroad-jetty9 6.8.5.20160929134539gitfe60f90
    ii xroad-proxy 6.8.5.20160929134539gitfe60f90
    ii xroad-securityserver 6.8.3-3-201605131138

`apt-get upgrade` command doesn’t install new packages - in this particular case new packages `xroad-monitor` and `xroad-addon-proxymonitor` installation is needed for upgrade of `xroad-securityserver` package.

To be sure that packages are installed correctly please use `sudo apt upgrade` or `sudo apt full-upgrade` commands.

