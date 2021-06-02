# X-Road technologies

**Technical Specification**

Version: 1.6<br/>
15.09.2020
<!-- 3 pages -->
Doc. ID: ARC-TEC

---

## Version history

 Date       | Version | Description                                                 | Author
 ---------- | ------- | ----------------------------------------------------------- | --------------------
 02.02.2018 | 1.0     | Initial version                                             | Antti Luoma
 02.03.2018 | 1.1     | Added uniform terms and conditions reference                | Tatu Repo
 17.04.2019 | 1.2     | Added RHEL7, Ubuntu 18.04, systemd and Postgres 10          | Petteri Kivimäki
 11.09.2019 | 1.3     | Remove Ubuntu 14.04 support                                 | Jarkko Hyöty
 12.05.2020 | 1.4     | Add link to X-Road core tech radar                          | Petteri Kivimäki
 15.09.2020 | 1.5     | Updated to match security server REST API architecture      | Janne Mattila
 02.06.2021 | 1.6     | Backup encryption related updates                           | Andres Allkivi

## Table of Contents

<!-- toc -->

- [License](#license)
- [1 Introduction](#1-introduction)
  * [1.1 Terms and abbreviations](#11-terms-and-abbreviations)
  * [1.2 References](#12-references)
- [2 Overview matrix of the X-Road technology](#2-overview-matrix-of-the-x-road-technology)
- [3 Central server technologies](#3-central-server-technologies)
- [4 Configuration proxy technologies](#4-configuration-proxy-technologies)
- [5 Security server technologies](#5-security-server-technologies)
- [6 Operational monitoring daemon technologies](#6-operational-monitoring-daemon-technologies)

<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/

## 1 Introduction

This document describes the general technology composition of X-Road components. To better illustrate the role of main technologies in X-Road, the information is collected in to several technology matrices highlighting the technology relationships between components.   

Besides, the [X-Road Core Tech Radar](https://nordic-institute.github.io/X-Road-tech-radar/) is a list of technologies used in the implementation of the core components of X-Road. 

## 1.1 Terms and abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\].


## 1.2 References

1. <a name="ARC-CP"></a>**ARC-CP** -- X-Road: Configuration Proxy Architecture. Document ID: [ARC-CP](../Architecture/arc-cp_x-road_configuration_proxy_architecture.md).  
2. <a name="ARC-CS"></a>**ARC-CS** -- X-Road: Central Server Architecture. Document ID: [ARC-CS](../Architecture/arc-cs_x-road_central_server_architecture.md).  
3. <a name="ARC-SS"></a>**ARC-SS** -- X-Road: Security Server Architecture. Document ID: [ARC-SS](../Architecture/arc-ss_x-road_security_server_architecture.md).
4. <a name="ARC-OPMOND"></a>**ARC-OPMOND** -- X-Road: Operational Monitoring Daemon Architecture. Document ID: [ARC-OPMOND](../OperationalMonitoring/Architecture/arc-opmond_x-road_operational_monitoring_daemon_architecture_Y-1096-1.md).
5. <a name="ARC-G"></a>**ARC-G** --  X-Road Architecture. Document ID: [ARC-G](../Architecture/arc-g_x-road_arhitecture.md).   
1. <a name="Ref_TERMS" class="anchor"></a>**TA-TERMS** -- X-Road Terms and Abbreviations. Document ID: [TA-TERMS](../terms_x-road_docs.md).


## 2 Overview matrix of the X-Road technology

[Table 1](#Ref_Technology_matrix_of_the_X_Road) presents the list of technologies used in the X-Road and mapping between the technologies and X-Road components.

<a id="Ref_Technology_matrix_of_the_X_Road" class="anchor"></a>
Table 1. Technology matrix of the X-Road

 **Technology**                     | **Security server** | **Central server** | **Configuration proxy** | **Operational Monitoring Daemon**
----------------------------------- | ------------------- | ------------------ | ----------------------- | -------------------
 Java 8                             | X                   | X                  | X                       | X
 C                                  | X                   | X                  |                         |
 Logback                            | X                   | X                  | X                       | X
 Akka 2                             | X                   | X                  | X                       | X
 Jetty 9                            | X\[[3](#Ref_3)\]    | X                  |                         |
 JRuby 9                            |                     | X                  |                         |
 Ubuntu 18.04                       | X                   | X                  | X                       | X
 Red Hat Enterprise Linux 7 (RHEL7) | X                   |                    |                         | X
 PostgreSQL 9.4                     |                     | X\[[1](#Ref_1)\]   |                         |
 PostgreSQL 10                      | X                   | X                  |                         | X
 nginx                              |                     | X                  | X                       |
 PAM                                | X                   | X                  |                         |
 Liquibase 3                        | X                   | X                  |                         | X
 systemd                            | X                   | X                  | X                       | X
 PKCS \#11\[[2](#Ref_2)\]           | X                   | X                  | X                       |
 Dropwizard Metrics 4               | X                   |                    |                         | X
 Spring Boot 2                      | X                   |                    |                         |  
 Vue.js 2                           | X                   |                    |                         |  
 Npm 6                              | X                   |                    |                         |  
 Node 12                            | X                   |                    |                         |  
 Typescript                         | X                   |                    |                         |  
 OpenAPI 3                          | X                   |                    |                         |  
 Embedded Tomcat 9                  | X                   |                    |                         |
 GNU Privacy Guard                  | X                   |                    |                         |

See [[ARC-G]](#ARC-G) for general X-Road architecture details.

<a id="Ref_1" class="anchor"></a>
\[1\] PostgreSQL 9.4 is used in High-Availability installation of X-Road central server.

<a id="Ref_2" class="anchor"></a>
\[2\] The use of hardware cryptographic devices requires that a PKCS \#11 driver is installed and configured in the system.

<a id="Ref_3" class="anchor"></a>
\[3\] Security Server uses embedded Jetty for clientproxy, serverproxy and OCSP responder

## 3 Central server technologies

[Table 2](#Ref_Technology_matrix_of_the_central_server) presents the list of technologies used in the security server and the mapping between technologies and central server components.

<a id="Ref_Technology_matrix_of_the_central_server" class="anchor"></a>
Table 2. Technology matrix of the central server

 **Technology** | **Signer** | **Web Server** | **Password Store** | **Management Services** | **Database** | **User Interface** | **Servlet Engine**
----------------|------------|----------------|--------------------|-------------------------|--------------|--------------------|--------------------
 Java 8         | X          |                |                    | X                       |              | X                  | X
 C              |            |                | X                  |                         |              |                    |
 Logback        | X          |                |                    | X                       |              | X                  |
 Akka 2         | X          |                |                    | X                       |              | X                  |
 Jetty 9        |            |                |                    |                         |              |                    | X
 JRuby 9        |            |                |                    |                         |              | X                  |
 Javascript     |            |                |                    |                         |              | X                  |
 PostgreSQL 9.4 |            |                |                    |                         | X\[[1](#Ref_1)\]         |                    |                  
 PostgreSQL 10  |            |                |                    |                         | X            |                    |
 nginx          |            | X              |                    |                         |              |                    |
 PAM            |            |                |                    |                         |              |                    | X
 Liquibase 3    |            |                |                    |                         | X            |                    |
 systemd        | X          | X              |                    |                         |              |                    | X
 PKCS \#11\[[2](#Ref_2)\]   | X          |                |                    |                         |              |                    |                  

See [[ARC-CS]](#ARC-CS) for the central server details.

## 4 Configuration proxy technologies

[Table 3](#Ref_Technology_matrix_of_the_configuration) presents the list of technologies used in the configuration proxy and the mapping between technologies and configuration proxy components.

<a id="Ref_Technology_matrix_of_the_configuration" class="anchor"></a>
Table 3. Technology matrix of the configuration proxy

 **Technology**   | **Web Server**   | **Configuration Processor**   | **Signer**   | **Configuration Client**
----------------- | ---------------- | ----------------------------- | ------------ | ------------------------
 Java 8           |                  | X                             | X            | X
 Logback          |                  | X                             | X            | X
 Akka 2           |                  | X                             | X            |
 nginx            | X                |                               |              |
 systemd          | X                | X                             | X            | X
 PKCS \#11\[[2](#Ref_2)\]   |                |                             | X          |

<a id="Ref_2" class="anchor"></a>
\[2\] The use of hardware cryptographic devices requires that a PKCS \#11 driver is installed and configured in the system.

See [[ARC-CP]](#ARC-CP) for the configuration proxy details.

## 5 Security server technologies

[Table 4](#Ref_Technology_matrix_of_the_security_server) presents the list of technologies used in the security server and the mapping between technologies and security server components.

<a id="Ref_Technology_matrix_of_the_security_server" class="anchor"></a>
Table 4. Technology matrix of the security server

 **Technology**                 | **Signer** | **Proxy** | **Password Store** | **Message Log**  | **Metadata Services** | **Database** | **Configuration Client** | **User Interface frontend** | **REST API** | **Monitor** | **Environmental Monitoring Service** | **Operational Monitoring Buffer** | **Operational Monitoring Services**
------------------------------- | ---------- | --------- | ------------------ | ---------------- | --------------------- | ------------ | ------------------------ | --------------------------- | ------------ | ----------- | ------------------------------------ | --------------------------------- | ---
 Java 8                         | X          | X         |                    | X                | X                     |              | X                        |                             | X            | X           | X                                    | X                                 | X
 C                              |            |           | X                  |                  |                       |              |                          |                             |              |             |                                      |                                   |
 Logback                        | X          | X         |                    | X                | X                     |              | X                        |                             | X            |             | X                                    | X                                 | X
 Akka 2                         | X          | X         |                    | X                |                       |              |                          |                             | X            | X           | X                                    | X                                 |
 Embedded Jetty 9               |            | X         |                    |                  |                       |              |                          |                             |              |             |                                      |                                   |
 Javascript                     |            |           |                    |                  |                       |              |                          | X                           |              |             |                                      |                                   |
 PostgreSQL 10                  |            |           |                    |                  |                       | X            |                          |                             | X            |             |                                      |                                   |
 PAM                            |            |           |                    |                  |                       |              |                          |                             | X            |             |                                      |                                   |
 Liquibase 3                    |            |           |                    |                  |                       | X            |                          |                             |              |             |                                      |                                   |
 systemd                        | X          | X         |                    |                  |                       |              | X                        |                             | X            |             |                                      |                                   |
 PKCS \#11\[[2](#Ref_2)\]       | X          |           |                    |                  |                       |              |                          |                             |              |             |                                      |                                   |
 Dropwizard Metrics 4           |            |           |                    |                  |                       |              |                          |                             |              | X           |                                      |                                   |
 Spring Boot 2                  |            |           |                    |                  |                       |              |                          |                             | X            |             |                                      |                                   |
 Vue.js 2                       |            |           |                    |                  |                       |              |                          | X                           |              |             |                                      |                                   |
 Npm 6                          |            |           |                    |                  |                       |              |                          | X                           |              |             |                                      |                                   |
 Node 12                        |            |           |                    |                  |                       |              |                          | X                           |              |             |                                      |                                   |
 Typescript                     |            |           |                    |                  |                       |              |                          | X                           |              |             |                                      |                                   |
 OpenAPI 3                      |            |           |                    |                  |                       |              |                          | X                           | X            |             |                                      |                                   |      
 Embedded Tomcat 9              |            |           |                    |                  |                       |              |                          |                             | X            |             |                                      |                                   |
 GNU Privacy Guard              |            |           |                    |                  |                       |              |                          |                             | X            |             |                                      |                                   |

See [[ARC-SS]](#ARC-SS) for the security server details.


## 6 Operational monitoring daemon technologies

[Table 5](#Ref_Technology_matrix_of_the_operational_monitoring_daemon) presents the list of the technologies used in the operational monitoring daemon and the mapping between technologies and monitoring daemon components. 
Note: OP-monitoring daemon is an additional component of the X-Road.

<a id="Ref_Technology_matrix_of_the_operational_monitoring_daemon" class="anchor"></a>
Table 5. Technology matrix of the operational monitoring daemon

Technology           | Op. Mon.<br/>Daemon Main | Op. Mon.<br/>Database | Op. Mon.<br/>Service | Configuration<br/>Client
:------------------- | :----------------------: | :-------------------: | :------------------: | :---:
Java 8               | X                        | X                     | X                    | X
Logback              | X                        | X                     | X                    | X
Akka 2               | X                        | X                     |                      |
PostgreSQL 10        | X                        | X                     |                      |
Liquibase 3          | X                        | X                     |                      |
Dropwizard Metrics 4 | X                        | X                     |                      |
systemd              | X                        |                       |                      | X

See [[ARC-OPMOND]](#ARC-OPMOND) for the operational monitoring daemon details.
