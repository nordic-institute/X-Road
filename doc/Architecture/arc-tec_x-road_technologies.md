# X-Road technologies

**Technical Specification**

Version: 1.9<br/>
08.06.2023
<!-- 3 pages -->
Doc. ID: ARC-TEC

---

## Version history

| Date       | Version | Description                                            | Author           |
|------------|---------|--------------------------------------------------------|------------------|
| 02.02.2018 | 1.0     | Initial version                                        | Antti Luoma      |
| 02.03.2018 | 1.1     | Added uniform terms and conditions reference           | Tatu Repo        |
| 17.04.2019 | 1.2     | Added RHEL7, Ubuntu 18.04, systemd and Postgres 10     | Petteri Kivimäki |
| 11.09.2019 | 1.3     | Remove Ubuntu 14.04 support                            | Jarkko Hyöty     |
| 12.05.2020 | 1.4     | Add link to X-Road core tech radar                     | Petteri Kivimäki |
| 15.09.2020 | 1.5     | Updated to match Security Server REST API architecture | Janne Mattila    |
| 02.06.2021 | 1.6     | Backup encryption related updates                      | Andres Allkivi   |
| 07.09.2021 | 1.7     | Update technologies                                    | Ilkka Seppälä    |
| 26.09.2022 | 1.8     | Remove Ubuntu 18.04 support                            | Andres Rosenthal |
| 08.06.2023 | 1.9     | Central Server technologies update                     | Justas Samuolis  |

## Table of Contents

<!-- toc -->

- [X-Road technologies](#x-road-technologies)
  - [Version history](#version-history)
  - [Table of Contents](#table-of-contents)
  - [License](#license)
  - [1 Introduction](#1-introduction)
    - [1.1 Terms and abbreviations](#11-terms-and-abbreviations)
    - [1.2 References](#12-references)
  - [2 Overview matrix of the X-Road technology](#2-overview-matrix-of-the-x-road-technology)
  - [3 Central Server technologies](#3-central-server-technologies)
  - [4 Configuration proxy technologies](#4-configuration-proxy-technologies)
  - [5 Security Server technologies](#5-security-server-technologies)
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

| **Technology**                     | **Security Server** | **Central Server** | **Configuration proxy** | **Operational Monitoring Daemon** |
|------------------------------------|:-------------------:|:------------------:|:-----------------------:|:---------------------------------:|
| Java 11                            |          X          |         X          |            X            |                 X                 |
| C                                  |          X          |         X          |                         |                                   |
| Logback                            |          X          |         X          |            X            |                 X                 |
| Akka 2                             |          X          |         X          |            X            |                 X                 |
| Jetty 9                            |  X\[[3](#Ref_3)\]   |  X\[[4](#Ref_4)\]  |                         |                                   |
| Ubuntu 20.04                       |          X          |         X          |            X            |                 X                 |
| Ubuntu 22.04                       |          X          |         X          |            X            |                 X                 |
| Red Hat Enterprise Linux 7 (RHEL7) |          X          |                    |                         |                 X                 |
| Red Hat Enterprise Linux 8 (RHEL8) |          X          |                    |                         |                 X                 |
| PostgreSQL 9+\[[5](#Ref_5)\]       |          X          |         X          |                         |                 X                 |
| nginx                              |                     |         X          |            X            |                                   |
| PAM                                |          X          |         X          |                         |                                   |
| Liquibase 4                        |          X          |         X          |                         |                 X                 |
| systemd                            |          X          |         X          |            X            |                 X                 |
| PKCS \#11\[[2](#Ref_2)\]           |          X          |         X          |            X            |                                   |
| Dropwizard Metrics 4               |          X          |                    |                         |                 X                 |
| Spring Boot 2                      |          X          |         X          |                         |                                   |
| Vue.js 2                           |          X          |         X          |                         |                                   |
| Npm 8                              |          X          |         X          |                         |                                   |
| Node 16                            |          X          |         X          |                         |                                   |
| Typescript                         |          X          |         X          |                         |                                   |
| OpenAPI 3                          |          X          |         X          |                         |                                   |
| Embedded Tomcat 9                  |          X          |         X          |                         |                                   |
| GNU Privacy Guard                  |          X          |         X          |                         |                                   |

See [[ARC-G]](#ARC-G) for general X-Road architecture details.

<a id="Ref_2" class="anchor"></a>
\[2\] The use of hardware cryptographic devices requires that a PKCS \#11 driver is installed and configured in the system.

<a id="Ref_3" class="anchor"></a>
\[3\] Security Server uses embedded Jetty for clientproxy, serverproxy and OCSP responder.

<a id="Ref_4" class="anchor"></a>
\[4\] Central Server uses embedded Jetty for management service and registration service.

<a id="Ref_5" class="anchor"></a>
\[5\] PostgreSQL version varies depending on operating system. By default, RHEL7 uses version 9, RHEL8 - 10, Ubuntu 20.04 - 12, Ubuntu 22.04 - 14. User may also use external PostgreSQL server. 

## 3 Central Server technologies

[Table 2](#Ref_Technology_matrix_of_the_central_server) presents the list of technologies used in the Central Server and the mapping between technologies and Central Server components.

<a id="Ref_Technology_matrix_of_the_central_server" class="anchor"></a>
Table 2. Technology matrix of the Central Server

| **Technology**                | **Signer** | **Password Store** | **Management/Registration Service** | **Database** | **User Interface** | **Rest API** | **Backend Scripts** | **Configuration Client** |
|-------------------------------|:----------:|:------------------:|:-----------------------------------:|:------------:|:------------------:|:------------:|:-------------------:|:------------------------:|
| Java 11                       |     X      |                    |                  X                  |              |                    |      X       |                     |            X             |
| C                             |            |         X          |                                     |              |                    |              |                     |                          |
| Logback                       |     X      |                    |                  X                  |              |                    |      X       |                     |            X             |
| Akka 2                        |     X      |                    |                                     |              |                    |      X       |                     |                          |
| Embedded Jetty 9              |            |                    |                  X                  |              |                    |              |                     |                          |
| Embedded Tomcat 9             |            |                    |                                     |              |                    |      X       |                     |                          |
| Spring Boot 2                 |            |                    |                  X                  |              |                    |      X       |                     |                          |
| Vue.js 2                      |            |                    |                                     |              |         X          |              |                     |                          |
| Npm 8                         |            |                    |                                     |              |         X          |              |                     |                          |
| Node 16                       |            |                    |                                     |              |         X          |              |                     |                          |
| Typescript                    |            |                    |                                     |              |         X          |              |                     |                          |
| OpenAPI 3                     |            |                    |                  X                  |              |         X          |      X       |                     |                          |
| PostgreSQL 12+\[[3](#Ref_3)\] |            |                    |                                     |      X       |                    |      X       |          X          |                          |
| nginx                         |            |                    |                  X                  |              |                    |              |                     |                          |
| PAM                           |            |                    |                                     |              |                    |      X       |                     |                          |
| Liquibase 4                   |            |                    |                                     |      X       |                    |              |                     |                          |
| systemd                       |     X      |                    |                  X                  |              |                    |      X       |                     |            X             |
| PKCS \#11\[[2](#Ref_2)\]      |     X      |                    |                                     |              |                    |              |                     |                          |
| GNU Privacy Guard             |            |                    |                                     |              |                    |              |          X          |                          |
 
<a id="Ref_2" class="anchor"></a>
\[2\] The use of hardware cryptographic devices requires that a PKCS \#11 driver is installed and configured in the system.

<a id="Ref_3" class="anchor"></a>
\[3\] PostgreSQL version varies depending on operating system. By default, Ubuntu 20.04 uses 12, Ubuntu 22.04 - 14. User may also use external PostgreSQL server.

See [[ARC-CS]](#ARC-CS) for the Central Server details.

## 4 Configuration proxy technologies

[Table 3](#Ref_Technology_matrix_of_the_configuration) presents the list of technologies used in the configuration proxy and the mapping between technologies and configuration proxy components.

<a id="Ref_Technology_matrix_of_the_configuration" class="anchor"></a>
Table 3. Technology matrix of the configuration proxy

| **Technology**           | **Web Server** | **Configuration Processor** | **Signer** | **Configuration Client** |
|--------------------------|:--------------:|:---------------------------:|:----------:|:------------------------:|
| Java 11                  |                |              X              |     X      |            X             |
| Logback                  |                |              X              |     X      |            X             |
| Akka 2                   |                |              X              |     X      |                          |
| nginx                    |       X        |                             |            |                          |
| systemd                  |       X        |              X              |     X      |            X             |
| PKCS \#11\[[2](#Ref_2)\] |                |                             |     X      |                          |

<a id="Ref_2" class="anchor"></a>
\[2\] The use of hardware cryptographic devices requires that a PKCS \#11 driver is installed and configured in the system.

See [[ARC-CP]](#ARC-CP) for the configuration proxy details.

## 5 Security Server technologies

[Table 4](#Ref_Technology_matrix_of_the_security_server) presents the list of technologies used in the Security Server and the mapping between technologies and Security Server components.

<a id="Ref_Technology_matrix_of_the_security_server" class="anchor"></a>
Table 4. Technology matrix of the Security Server

| **Technology**               | **Signer** | **Proxy** | **Password Store** | **Message Log** | **Metadata Services** | **Database** | **Configuration Client** | **User Interface frontend** | **REST API** | **Monitor** | **Environmental Monitoring Service** | **Operational Monitoring Buffer** | **Operational Monitoring Services** |
|------------------------------|:----------:|:---------:|:------------------:|:---------------:|:---------------------:|:------------:|:------------------------:|:---------------------------:|:------------:|:-----------:|:------------------------------------:|:---------------------------------:|:-----------------------------------:|
| Java 11                      |     X      |     X     |                    |        X        |           X           |              |            X             |                             |      X       |      X      |                  X                   |                 X                 |                  X                  |
| C                            |            |           |         X          |                 |                       |              |                          |                             |              |             |                                      |                                   |                                     |
| Logback                      |     X      |     X     |                    |        X        |           X           |              |            X             |                             |      X       |             |                  X                   |                 X                 |                  X                  |
| Akka 2                       |     X      |     X     |                    |        X        |                       |              |                          |                             |      X       |      X      |                  X                   |                 X                 |                                     |
| Embedded Jetty 9             |            |     X     |                    |                 |                       |              |                          |                             |              |             |                                      |                                   |                                     |
| Javascript                   |            |           |                    |                 |                       |              |                          |              X              |              |             |                                      |                                   |                                     |
| PostgreSQL 9+\[[3](#Ref_3)\] |            |           |                    |                 |                       |      X       |                          |                             |      X       |             |                                      |                                   |                                     |
| PAM                          |            |           |                    |                 |                       |              |                          |                             |      X       |             |                                      |                                   |                                     |
| Liquibase 4                  |            |           |                    |                 |                       |      X       |                          |                             |              |             |                                      |                                   |                                     |
| systemd                      |     X      |     X     |                    |                 |                       |              |            X             |                             |      X       |             |                                      |                                   |                                     |
| PKCS \#11\[[2](#Ref_2)\]     |     X      |           |                    |                 |                       |              |                          |                             |              |             |                                      |                                   |                                     |
| Dropwizard Metrics 4         |            |           |                    |                 |                       |              |                          |                             |              |      X      |                                      |                                   |                                     |
| Spring Boot 2                |            |           |                    |                 |                       |              |                          |                             |      X       |             |                                      |                                   |                                     |
| Vue.js 2                     |            |           |                    |                 |                       |              |                          |              X              |              |             |                                      |                                   |                                     |
| Npm 6                        |            |           |                    |                 |                       |              |                          |              X              |              |             |                                      |                                   |                                     |
| Node 12                      |            |           |                    |                 |                       |              |                          |              X              |              |             |                                      |                                   |                                     |
| Typescript                   |            |           |                    |                 |                       |              |                          |              X              |              |             |                                      |                                   |                                     |
| OpenAPI 3                    |            |           |                    |                 |                       |              |                          |              X              |      X       |             |                                      |                                   |                                     |
| Embedded Tomcat 9            |            |           |                    |                 |                       |              |                          |                             |      X       |             |                                      |                                   |                                     |
| GNU Privacy Guard            |            |           |                    |                 |                       |              |                          |                             |      X       |             |                                      |                                   |                                     |

<a id="Ref_2" class="anchor"></a>
\[2\] The use of hardware cryptographic devices requires that a PKCS \#11 driver is installed and configured in the system.

<a id="Ref_3" class="anchor"></a>
\[3\] PostgreSQL version varies depending on operating system. By default, RHEL7 uses version 9, RHEL8 - 10, Ubuntu 20.04 - 12, Ubuntu 22.04 - 14. User may also use external PostgreSQL server.

See [[ARC-SS]](#ARC-SS) for the Security Server details.

## 6 Operational monitoring daemon technologies

[Table 5](#Ref_Technology_matrix_of_the_operational_monitoring_daemon) presents the list of the technologies used in the operational monitoring daemon and the mapping between technologies and monitoring daemon components. 
Note: OP-monitoring daemon is an additional component of the X-Road.

<a id="Ref_Technology_matrix_of_the_operational_monitoring_daemon" class="anchor"></a>
Table 5. Technology matrix of the operational monitoring daemon

| Technology                   | Op. Mon.<br/>Daemon Main | Op. Mon.<br/>Database | Op. Mon.<br/>Service | Configuration<br/>Client |
|:-----------------------------|:------------------------:|:---------------------:|:--------------------:|:------------------------:|
| Java 11                      |            X             |           X           |          X           |            X             |
| Logback                      |            X             |           X           |          X           |            X             |
| Akka 2                       |            X             |           X           |                      |                          |
| PostgreSQL 9+\[[1](#Ref_1)\] |            X             |           X           |                      |                          |
| Liquibase 3                  |            X             |           X           |                      |                          |
| Dropwizard Metrics 4         |            X             |           X           |                      |                          |
| systemd                      |            X             |                       |                      |            X             |

<a id="Ref_1" class="anchor"></a>
\[1\] PostgreSQL version varies depending on operating system. By default, RHEL7 uses version 9, RHEL8 - 10, Ubuntu 20.04 - 12, Ubuntu 22.04 - 14. User may also use external PostgreSQL server.


See [[ARC-OPMOND]](#ARC-OPMOND) for the operational monitoring daemon details.
