# Technical Specification

**X-Road Architecture**

Version: 1.13  
01.06.2023
<!-- 16 pages -->
Doc. ID: ARC-G

---


## Version history

 Date       | Version | Description                                                                                                   | Author
 ---------- |---------|---------------------------------------------------------------------------------------------------------------| --------------------
 02.06.2015 | 0.1     | Initial version                                                                                               | Margus Freudenthal
 03.07.2015 | 0.2     | Updated component diagram, added technology matrix, updated interface descriptions, added references          | Margus Freudenthal
 08.09.2015 | 0.3     | Made editorial changes                                                                                        | Vello Hanson
 08.09.2015 | 0.4     | Added signed document download interface and UIs to component diagram, added components to deployment diagram | Margus Freudenthal
 17.09.2015 | 0.5     | Incorporated feedback from Vitali                                                                             | Margus Freudenthal
 18.09.2015 | 1.0     | Editorial changes made                                                                                        | Imbi Nõgisto
 13.10.2015 | 1.1     | Incorporated feedback from Vitali                                                                             | Ilja Kromonov
 28.10.2015 | 1.2     | Typos fixed                                                                                                   | Siim Annuk
 16.12.2015 | 1.3     | Add environmental monitoring                                                                                  | Ilkka Seppälä
 20.12.2016 | 1.4     | Added operational monitoring                                                                                  | Kristo Heero
 20.02.2017 | 1.5     | Converted to Github flavoured Markdown, added license text, adjusted tables for better output in PDF          | Toomas Mölder
 21.12.2017 | 1.6     | Matrix of technologies moved to arc-x-road_technologies.md and chapters reordered                             | Antti Luoma
 02.03.2018 | 1.7     | Moved terms and abbreviations into the terms document, added terms reference and document links               | Tatu Repo
 02.11.2018 | 1.8     | Described environmental monitoring daemon and its interfaces                                                  | Ilkka Seppälä
 16.04.2019 | 1.9     | Added REST support and X-Road Message Protocol for REST                                                       | Petteri Kivimäki
 28.02.2021 | 1.10    | Update Figure 1 and Figure 2                                                                                  | Petteri Kivimäki
 07.09.2021 | 1.11    | Fix broken link                                                                                               | Ilkka Seppälä
 23.09.2022 | 1.12    | Added new Registration Web Service                                                                            | Eneli Reimets
 01.06.2023 | 1.13    | Update references                                                                                             | Petteri Kivimäki

## Table of Contents

<!-- toc -->

- [Technical Specification](#technical-specification)
  - [Version history](#version-history)
  - [Table of Contents](#table-of-contents)
  - [License](#license)
  - [1 Introduction](#1-introduction)
    - [1.1 Overview](#11-overview)
    - [1.2 Design Goals](#12-design-goals)
  - [1.3 Terms and abbreviations](#13-terms-and-abbreviations)
    - [1.4 References](#14-references)
  - [2 System Components](#2-system-components)
    - [2.1 Central Server](#21-central-server)
    - [2.2 Security Server](#22-security-server)
    - [2.3 Information System](#23-information-system)
    - [2.4 Time-Stamping Authority](#24-time-stamping-authority)
    - [2.5 Certification Authority](#25-certification-authority)
    - [2.6 Configuration Proxy](#26-configuration-proxy)
    - [2.7 Operational Monitoring Daemon](#27-operational-monitoring-daemon)
    - [2.8 Environmental Monitoring Daemon](#28-environmental-monitoring-daemon)
  - [3 Protocols and Interfaces](#3-protocols-and-interfaces)
    - [3.1 X-Road Message Protocol](#31-x-road-message-protocol)
    - [3.2 Protocol for Downloading Configuration](#32-protocol-for-downloading-configuration)
    - [3.3 Message Transport Protocol](#33-message-transport-protocol)
    - [3.4 Service Metadata Protocol](#34-service-metadata-protocol)
    - [3.5 Download Signed Document](#35-download-signed-document)
    - [3.6 Management Services Protocol](#36-management-services-protocol)
    - [3.7 OCSP Protocol](#37-ocsp-protocol)
    - [3.8 Time-Stamping Protocol](#38-time-stamping-protocol)
    - [3.9 Security Server User Interface](#39-security-server-user-interface)
    - [3.10 Central Server User Interface](#310-central-server-user-interface)
    - [3.11 Store Operational Monitoring Data](#311-store-operational-monitoring-data)
    - [3.12 Operational Monitoring Query](#312-operational-monitoring-query)
    - [3.13 Operational Monitoring Protocol](#313-operational-monitoring-protocol)
    - [3.14 Operational Monitoring JMX](#314-operational-monitoring-jmx)
    - [3.15 Environmental Monitoring Protocol](#315-environmental-monitoring-protocol)
    - [3.16 Environmental Monitoring JMX](#316-environmental-monitoring-jmx)
    - [3.17 Environmental Monitoring Query](#317-environmental-monitoring-query)
  - [4 Deployment View](#4-deployment-view)

<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/

## 1 Introduction


### 1.1 Overview

X-Road is a system for enabling secure communication between organizations. This document describes technical architecture of the X-Road core. The goal is to give general overview of the X-Road system and the components that it contains. Detailed description of components and protocols can be found in separate documents. For information on the processes implemented by the X-Road components, refer to the use case documentation.


### 1.2 Design Goals

The following list contains main design goals and design decisions of the X-Road system.

-   X-Road is **decentralized** – the data exchange happens directly between organizations. There are no intermediaries. If the two organizations have established secure connection, the continuous data exchange depends only on availability of the organizations and the network between them.

-   **Ownership of data** – X-Road does not change ownership of data. The data owner (service provider) controls who can access particular services.

-   **Availability** is a central concern – the protocols are designed so that there is no single bottleneck in the system. Additionally, no component should become a single point of failure.

-   All the messages processed by the X-Road are usable as **digital evidence**. The technical solution must comply with requirements for digital seals according to eIDAS \[[EIDAS](#Ref_EIDAS)\]. This implies support for secure signature creation devices (SSCDs).

-   All the communication is implemented as \[[SOAP](#Ref_SOAP)\] or REST **service calls**. SOAP services are described using the \[[WSDL](#Ref_WSDL)\] language and REST services are described using the \[[OPENAPI](#Ref_OpenAPI)\] Specification v3.

-   **Cross-border services** – it is possible for an organization to invoke services provided by an organization belonging to a different instance of X-Road.

-   **Encapsulating the security protocol** – the security measures and the security protocol are encapsulated in standard components. The organizations are not required to implement security-related functionality for data exchange.

-   **Standardization** – X-Road aims to standardize the communication protocol between organizations. This enables the organizations to connect to any number of service providers without implementing additional protocols. X-Road core does not perform protocol and data conversion. If necessary, these conversions can be performed by the organization's information system.

-   **No predetermined roles** – once an organization has joined the X-Road infrastructure, it can act as both service client and service provider without having to perform any additional registration.

-   **Two-level authentication** – X-Road core handles authentication and access control on the organization level. End-user authentication is performed by information system of the service client.

## 1.3 Terms and abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\].

### 1.4 References

1.  <a id="Ref_ARC-CP" class="anchor"></a>\[ARC-CP\] X-Road: Configuration Proxy Architecture. Document ID: [ARC-CP](arc-cp_x-road_configuration_proxy_architecture.md).

2.  <a id="Ref_ARC-CS" class="anchor"></a>\[ARC-CS\] X-Road: Central Server Architecture. Document ID: [ARC-CS](arc-cs_x-road_central_server_architecture.md).

3.  <a id="Ref_ARC-SS" class="anchor"></a>\[ARC-SS\] X-Road: Security Server Architecture. Document ID: [ARC-SS](arc-ss_x-road_security_server_architecture.md).

4.  <a id="Ref_ARC-ENVMON" class="anchor"></a>\[ARC-ENVMON\] X-Road: Monitoring Architecture. Document ID: [ARC-ENVMON](../EnvironmentalMonitoring/Monitoring-architecture.md).

5.  <a id="Ref_ARC-OPMOND" class="anchor"></a>\[ARC-OPMOND\] X-Road: Operational Monitoring Daemon Architecture. Document ID: [ARC-OPMOND](../OperationalMonitoring/Architecture/arc-opmond_x-road_operational_monitoring_daemon_architecture_Y-1096-1.md).

6.  <a id="Ref_BATCH-TS" class="anchor"></a>\[BATCH-TS\] Freudenthal, Margus. Using Batch Hashing for Signing and Time-Stamping. Cybernetica Research Reports, T-4-20, 2013.

7.  <a id="Ref_EIDAS" class="anchor"></a>\[EIDAS\] EU Regulation No 910/2014 – Regulation (EU) No 910/2014 of the European Parliament and of the Council of 23 July 2014 on electronic identification and trust services for electronic transactions in the internal market and repealing Directive 1999/93/EC

8.  <a id="Ref_OCSP" class="anchor"></a>\[OCSP\] X.509 Internet Public Key Infrastructure Online Certificate Status Protocol - OCSP. Internet Engineering Task Force, RFC 6960, 2013.

9.  <a id="Ref_PKCS10" class="anchor"></a>\[PKCS10\] Certification Request Syntax Standard. RSA Laboratories, PKCS \#10.

10. <a id="Ref_PR-GCONF" class="anchor"></a>\[PR-GCONF\] X-Road: Protocol for Downloading Configuration. Document ID: [PR-GCONF](../Protocols/pr-gconf_x-road_protocol_for_downloading_configuration.md).

11. <a id="Ref_PR-MSERV" class="anchor"></a>\[PR-MSERV\] X-Road: Management Services Protocol. Document ID: [PR-MSERV](../Protocols/pr-mserv_x-road_protocol_for_management_services.md).

12.  <a id="Ref_PR-MESS" class="anchor"></a>\[PR-MESS\] X-Road: Profile of Messages. Document ID: [PR-MESS](../Protocols/pr-mess_x-road_message_protocol.md).

13. <a id="Ref_PR-MESSTRANSP" class="anchor"></a>\[PR-MESSTRANSP\] X-Road: Message Transport Protocol. Document ID: [PR-MESSTRANSP](../Protocols/pr-messtransp_x-road_message_transport_protocol.md).

14. <a id="Ref_PR-META" class="anchor"></a>\[PR-META\] X-Road: Service Metadata Protocol. Document ID: [PR-META](../Protocols/pr-meta_x-road_service_metadata_protocol.md).

15. <a id="Ref_PR-OPMON" class="anchor"></a>\[PR-OPMON\] X-Road: Operational Monitoring Protocol. Document ID: [PR-OPMON](../OperationalMonitoring/Protocols/pr-opmon_x-road_operational_monitoring_protocol_Y-1096-2.md).

16. <a id="Ref_PR-OPMONJMX" class="anchor"></a>\[PR-OPMONJMX\] X-Road: Operational Monitoring JMX Protocol. Document ID: [PR-OPMONJMX](../OperationalMonitoring/Protocols/pr-opmonjmx_x-road_operational_monitoring_jmx_protocol_Y-1096-3.md).

17. <a id="Ref_SOAP" class="anchor"></a>\[SOAP\] Simple Object Access Protocol (SOAP) 1.1, 2000.

18. <a id="Ref_TSP" class="anchor"></a>\[TSP\] Internet X.509 Public Key Infrastructure Time-Stamp Protocol (TSP). Intenet Engineering Task Force, RFC 3161, 2001.

19. <a id="Ref_UG-SIGDOC" class="anchor"></a>\[UG-SIGDOC\] X-Road: Signed Document Download and Verification Manual. Document ID: [UG-SIGDOC](../Manuals/ug-sigdoc_x-road_signed_document_download_and_verification_manual.md).

20. <a id="Ref_WSDL" class="anchor"></a>\[WSDL\] Web Services Description Language (WSDL) 1.1, 2001.

21. <a id="Ref_ARC-TEC" class="anchor"></a>\[ARC-TEC\] X-Road technologies. Document ID: [ARC-TEC](arc-tec_x-road_technologies.md).

22. <a id="Ref_TERMS" class="anchor"></a>\[TA-TERMS\] X-Road Terms and Abbreviations. Document ID: [TA-TERMS](../terms_x-road_docs.md).

23. <a id="Ref_OPENAPI" class="anchor"></a>\[OPENAPI\] OpenAPI Specification 3.0.0. 26th July 2017.

24.  <a id="Ref_PR-REST" class="anchor"></a>\[PR-REST\] X-Road Message Protocol for REST. Document ID: [PR-REST](../Protocols/pr-rest_x-road_message_protocol_for_rest.md).

## 2 System Components

[Figure 1](#Logical_structure_of_X_Road) shows the main components and interfaces of the X-Road system. The components that are not part of the X-Road core are marked with grey color. The components and the interfaces are described in detail in the following sections.


<a id="Logical_structure_of_X_Road" class="anchor"></a>
![](img/arc-g_logical_structure_of_x_road.svg)

Figure 1. Logical structure of X-Road

See the list of technologies used in components here: [ARC-TEC](#Ref_ARC-TEC)


### 2.1 Central Server

Central server (see \[[ARC-CS](#Ref_ARC-CS)\] for details) manages the database of X-Road members and security servers. In addition, the central server contains the security policy of the X-Road instance. The security policy consists of the following items:

-   list of trusted certification authorities,

-   list of trusted time-stamping authorities,

-   tunable parameters such as maximum allowed lifetime of an OCSP response.

Both the member database and the security policy are made available to the security servers via HTTP protocol (see Section [3.2](#32-protocol-for-downloading-configuration)). This distributed set of data forms the global configuration.

In addition to configuration distribution, the central server provides interface for performing management tasks such as adding and removing security server clients. These tasks are invoked from the user interface of the security servers. The management services are implemented as standard X-Road services and offered via central security server. See Section [3.6](#36-management-services-protocol) for details.


### 2.2 Security Server

The security server (see \[[ARC-SS](#Ref_ARC-SS)\] for details) mediates service calls and service responses between information systems. The security server encapsulates the security aspects of the X-Road infrastructure: managing keys for signing and authentication, sending messages over secure channel, creating the proof value for messages with digital signatures, time-stamping (see Section [3.8](#38-time-stamping-protocol)) and logging. For the service client and the service provider information system, the security server offers a message protocol for SOAP and a message protocol for REST (see Section [3.1](#31-x-road-message-protocol)). The protocols are the same for both the client and the service provider, making the security server transparent to the applications.

A single security server can host several organizations (multi-tenancy). The organization managing the security server is the server owner, the hosted organizations are security server clients.

The security server manages two types of keys. The authentication keys are assigned to a security server and used for establishing cryptographically secure communication channels with the other security servers (see Section [3.3](#33-message-transport-protocol)). The signing keys are assigned to the security server's clients and used for signing the exchanged messages. The keys can be stored either on hard disk (software token) or on an SSCD.

The security server downloads and caches up-to-date global configuration and certificate validity information (see Section [3.7](#37-ocsp-protocol)). Caching allows the security server to operate even when the information sources are unavailable.

The security server contains an optional monitoring component that keeps track of environmental properties such as running processes, available disk space, installed packages etc. The monitoring component publishes this data via environmental monitoring service (see Section [3.11](#311-store-operational-monitoring-data)) and monitoring JMX (see Section [3.12](#312-operational-monitoring-query)) interfaces.


### 2.3 Information System

The information system (IS) uses and/or provides services via the X-Road.

For the service client IS, the security server acts as an entry point to all the X-Road services (see Section [3.1](#31-x-road-message-protocol)). The client IS is responsible for implementing an user authentication and access control mechanism that complies with the requirements of the particular X-Road instance. The identity of the end user is made available to the service provider by including it in the service request. The client can discover the X-Road members and available services by using the X-Road metadata protocol (see Section [3.4](#34-service-metadata-protocol)).

The service provider information system implements a SOAP or a REST service and makes it available over the X-Road. For this purpose, the service must conform to the X-Road message protocol for SOAP or message protocol for REST (see Section [3.1](#31-x-road-message-protocol)). A SOAP service must be accompanied by a WSDL service description, and a REST service may be accompanied by an OpenAPI Specification v3.


### 2.4 Time-Stamping Authority

The time-stamping authority issues time stamps that certify the existence of data items at a certain point of time. The time-stamping authority must implement the time-stamping protocol described in Section [3.8](#38-time-stamping-protocol).

X-Road uses batch time-stamping (see \[[BATCH-TS](#Ref_BATCH-TS)\]). This reduces the load of the time-stamping service. The load does not depend on the number of messages exchanged over the X-Road, instead it depends on the number of security servers in the system.


### 2.5 Certification Authority

The certification authority (CA) issues certificates to security servers (authentication certificates) and to X-Road member organizations (signing certificates). All the certificates are stored in the security servers. The CA must be able to process certificate signing requests conforming to \[[PKCS10](#Ref_PKCS10)\].

The CA must distribute certificate validity information via the OCSP protocol (see Section [3.7](#37-ocsp-protocol)). The security servers cache the OCSP responses to reduce the load in the OCSP service and to increase availability. The load on the OCSP service depends on the number of certificates issued.


### 2.6 Configuration Proxy

The configuration proxy (see \[[ARC-CP](#Ref_ARC-CP)\] for details) implements both the client part and the server part of the configuration distribution protocol (see Section [3.2](#32-protocol-for-downloading-configuration) ). The configuration proxy downloads the configuration, stores it, and makes it available for download. Thus, the configuration proxy can be used to increase system availability by creating an additional configuration source and reduce load on the central server.


### 2.7 Operational Monitoring Daemon

The main functionality of the operational monitoring daemon (see \[[ARC-OPMOND](#Ref_ARC-OPMOND)\] for details) is to collect and store operational data of the X-Road security server and make it available for external monitoring systems via corresponding interfaces.


### 2.8 Environmental Monitoring Daemon

The environmental monitoring daemon (see \[[ARC-ENVMON](#Ref_ARC-ENVMON)\] for details) gathers information about the security server's operating environment and makes it available for external monitoring systems via corresponding interfaces.


## 3 Protocols and Interfaces


### 3.1 X-Road Message Protocol

X-Road Message Protocol is used by service client and service provider information systems for communicating with the X-Road security server.

The protocol is a synchronous RPC style protocol that is initiated by the client IS or by the service provider's security server.

X-Road provides a message protocol for SOAP and a message protocol for REST. The X-Road Message Protocols are based on SOAP/REST over HTTP(S) and adds additional header fields for identifying the service client and the invoked service. See \[[PR-MESS](#Ref_PR-MESS)\] and \[[PR-REST](#Ref_PR-REST)\] for technical details.

These protocols (together with the Message Transport Protocol) form the core of the X-Road data exchange. If the involved components are not available, then the data exchange is not possible. X-Road architecture makes possible to improve the availability of the involved components by using redundancy.


### 3.2 Protocol for Downloading Configuration

Configuration clients download the generated global configuration files from the central server.

The configuration download protocol is a synchronous protocol that is offered by the central server. It is used by configuration clients such as security servers and configuration proxies.

The protocol is based on HTTP and MIME multipart messaging (see \[[PR-GCONF](#Ref_PR-GCONF)\] for details). The configuration is signed by the central server to protect it against modification. Usually the configuration consists of several parts. The protocol allows configuration clients to check whether the configuration has changed and only download the modified parts.

X-Road security servers (and operational monitoring daemons) maintain a local copy of the global configuration, which they periodically update from their respective configuration source. This cached global configuration has a validity period, which, in general, is longer than the period at which configuration clients are configured to update their local copy. Security servers continue to be fully operational while the cached global configuration remains valid. However, an out-of-date copy of the global configuration severely restricts the management capabilities of security server administrators and forbids security servers from processing incoming requests. As such, a short downtime of the interface is permissible within the limits of the configured configuration validity period.


### 3.3 Message Transport Protocol

The X-Road Message Transport Protocol is used by security server to exchange service requests and service responses.

The protocol is a synchronous RPC style protocol that is initiated by the security server of the service client.

The protocol is based on HTTPS and uses mutual certificate-based TLS authentication. The SOAP/REST messages received from the client and the service provider IS are wrapped in MIME multipart message together with additional security-related data, such as signatures and OCSP responses. See \[[PR-MESSTRANSP](#Ref_PR-MESSTRANSP)\] for details.

This protocol (together with X-Road message protocol) forms the core of the X-Road data exchange. If the involved components are not available, then the data exchange is impossible. X-Road architecture makes possible to improve the availability of the involved components by using redundancy.


### 3.4 Service Metadata Protocol

The X-Road Service Metadata Protocol can be used by the service client information systems to gather information about the X-Road instance. In particular, the protocol can be used to find X-Road members, services offered by these members and the WSDL service descriptions.

The protocol is a synchronous RPC style protocol that is initiated by the service client IS.

Some of the information services are implemented as HTTP(S) GET requests to simplify client IS implementation. The other information services are called as standard X-Road services (see Section [3.1](#31-x-road-message-protocol)). See \[[PR-META](#Ref_PR-META)\] for further details.

The Service Metadata Protocol is used for client IS configuration and therefore the availability, throughput and latency of its implementing components are not critical to the functioning of the X-Road.


### 3.5 Download Signed Document

The service for downloading signed documents can be used by the information systems to download signed containers from the security server's message log. In addition, the service provides a convenience method for downloading global configuration that can be used to verify the signed containers.

The protocol is a synchronous RPC-style protocol that is initiated by the IS. The service is implemented as HTTP(S) GET requests. See \[[UG-SIGDOC](#Ref_UG-SIGDOC)\] for further details.

The Download Signed Document protocol is used by IS for downloading data stored in the security server and therefore the availability, throughput and latency of its implementing components are not critical to the functioning of the X-Road.


### 3.6 Management Services Protocol

The management services are called by security servers to perform management tasks such as registering a security server client or deleting an authentication certificate.

The management service protocol is a synchronous RPC-style protocol that is offered by the member management and registration web services. The services are called by security servers.

The management services are implemented as standard X-Road services (see Section 3.1 for details) that are offered by the organization managing the X-Road instance. The exception is the authentication certificate registration service that, for technical reasons, is implemented directly by the registration web service. Full details of the management services are described in \[[PR-MSERV](#Ref_PR-MSERV)\].

In general, the management services are not critical to operation of X-Road and therefore their availability is not paramount. If the management services are unavailable, the security servers cannot manage their clients and authentication certificates. Some actions (such as removing clients and certificates) can be performed manually by central server administrator, without using the management services. The management service operations are not time-critical (the security server user explicitly chooses to send the management request and the user interface does not imply that this operation is instantaneous).


### 3.7 OCSP Protocol

The OCSP protocol (see \[[OCSP](#Ref_OCSP)\]) is used by the security servers to query the validity information about the signing and authentication certificates.

OCSP protocol is synchronous protocol that is offered by the OCSP responder belonging to a certification authority.

In X-Road, each security server is responsible for downloading and caching the validity information about its certificates. The OCSP responses are sent to the other security servers as part of the message transport protocol (see Section [3.3](#33-message-transport-protocol)). This ensures that the security servers do not need to discover the OCSP service used by the other party. Additionally, this arrangement supports the situation where access to the OCSP service is either restricted to certificate owners or is subject to charges.

The security servers never include nonce field in the OCSP request. This allows the OCSP service to employ various optimization strategies, such as pre-creating the OCSP responses.

Because OCSP responses are used in the process of certificate validation, failure of the OCSP service effectively disables X-Road message exchange. When the cached OCSP responses cannot be refreshed, the security servers are unable to communicate. Thus, the lifetime of the OCSP responses determines the maximum amount of time that the OCSP service can be unavailable. The lifetime is defined by the owner of the central server and can vary between different instances of X-Road.


### 3.8 Time-Stamping Protocol

The Time-stamping protocol (see \[[TSP](#Ref_TSP)\]) is used by security servers to ensure long-term proof value of the exchanged messages. The security servers log all the messages and their signatures. These logs are periodically time-stamped to create long-term proof.

Time-stamping protocol is a synchronous protocol that is provided by the time-stamp authority. However, the security servers use the time-stamping protocol in an asynchronous manner. Security servers log all the messages that are exchanged with other security servers. These messages are time-stamped asynchronously using batch time-stamping (see \[[BATCH-TS](#Ref_BATCH-TS)\]). This is done to decouple availability of the message exchange from availability of the time-stamping authority, to decrease the latency of message exchange, and to reduce load on the time-stamping authority.

Because time-stamping is used in an asynchronous manner, temporary unavailability of the time-stamping service does not directly affect the X-Road message exchange. However, if the security servers fail to time-stamp the accumulated messages for certain time period then it may become difficult to prove the exact time of the message exchanges. To minimize this risk the security servers will stop forwarding messages if the time-stamping has been failing for some time. The maximum allowed time period between logging of a message and acquiring a time stamp for that message is defined by the owner of the central server and can very between different instances of X-Road.


### 3.9 Security Server User Interface

The security server user interface is used by the security server administrator to configure and manage the security server.


### 3.10 Central Server User Interface

The central server user interface is used by the central server administrator to configure and manage the central server.


### 3.11 Store Operational Monitoring Data

This protocol is used by the X-Road security server to store its cached operational monitoring data into the database of the operational monitoring daemon. The protocol is a synchronous RPC-style protocol based on JSON over HTTP(S).

The interface is described in more detail in \[[ARC-OPMOND](#Ref_ARC-OPMOND)\].


### 3.12 Operational Monitoring Query

The operational monitoring query interface is used by the X-Road security server to retrieve operational monitoring data from the operational monitoring daemon. The asynchronous RPC-style X-Road operational monitoring protocol \[[PR-OPMON](#Ref_PR-OPMON)\] (based on \[[PR-MESS](#Ref_PR-MESS)\]) is used.

The interface is described in more detail in \[[ARC-OPMOND](#Ref_ARC-OPMOND)\].


### 3.13 Operational Monitoring Protocol

This interface is used by external monitoring systems to gather operational information of the security server. The protocol is synchronous RPC style protocol that is initiated by the external monitoring system. The protocol is described in more detail in \[[PR-OPMON](#Ref_PR-OPMON)\].


### 3.14 Operational Monitoring JMX

This interface is used by a local monitoring system (e.g. Zabbix) to gather local operational health data of the security server via JMXMP. The interface is described in more detail in \[[ARC-OPMOND](#Ref_ARC-OPMOND)\] and \[[PR-OPMONJMX](#Ref_PR-OPMONJMX)\].


### 3.15 Environmental Monitoring Protocol

The environmental monitoring interface responds to queries for monitoring environmental data from security server's serverproxy interface. The environmental monitoring data is collected by environmental monitoring service.


### 3.16 Environmental Monitoring JMX

The environmental monitoring JMX service publishes environmental monitoring data via JMX interface. The environmental monitoring data is collected by environmental monitoring service.


### 3.17 Environmental Monitoring Query

The environmental monitoring query interface is used by the X-Road security server to retrieve environmental monitoring data from the environmental monitoring daemon.


## 4 Deployment View

[Figure 2](#Deployment_view_of_X_Road) shows deployment view of a basic X-Road instance. In practice, all the components can use redundancy to improve availability and throughput. The deployment options for various components are described in the detailed architecture documents.

The diagram also shows what components are installed and hosted by any given organization. The governing authority installs and maintains central server and central security server. The configuration proxy is an optional component that is typically used for distributing configuration to federated X-Road instances. The service client and service provider organizations host their information system and security server that connects the information system to the X-Road.

<a id="Deployment_view_of_X_Road" class="anchor"></a>
![](img/arc-g_deployment_view_of_x_road.svg)

Figure 2. Deployment view of X-Road
