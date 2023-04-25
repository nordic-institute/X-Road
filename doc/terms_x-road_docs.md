# X-Road Terms and Abbreviations

**X-ROAD 7**

Version: 0.9  
Doc. ID:  TA-TERMS

## Version history

| Date        | Version | Description                                                                                                                                                      | Author           |
|-------------|---------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------|
| 06.07.2015  | 0.1     | Initial draft                                                                                                                                                    |                  |
| 23.02.2017  | 0.2     | Converted to Github flavoured Markdown, added license text, adjusted tables and identification for better output in PDF. Added explanation of monitoring service | Toomas Mölder    |
| 14.11.2017  | 0.3     | All the descriptions in Estonian language removed. Couple of new descriptions added                                                                              | Antti Luoma      |
| 06.03.2018  | 0.4     | Moved/merged terminology explanations from other X-Road repository MD-documents to this document                                                                 | Tatu Repo        |
| 03.01.2019  | 0.5     | Minor changes - typos fixed.                                                                                                                                     | Yamato Kataoka   |
| 16.04.2019  | 0.6     | Add description of REST services.                                                                                                                                | Petteri Kivimäki |
| 02.06.2021  | 0.7     | Add backup encryption related terms.                                                                                                                             | Andres Allkivi   |
| 25.08.2021  | 0.8     | Update X-Road references from version 6 to 7                                                                                                                     | Caro Hautamäki   |
| 17.04.2023  | 0.9     | Remove central services support                                                                                                                                  | Justas Samuolis  |

## Table of Contents

<!-- toc -->

- [License](#license)
- [1 X-Road and X-Road Instance](#1-x-road-and-x-road-instance)
- [2 Participants of X-Road](#2-participants-of-x-road)
- [3 Trust services](#3-trust-services)
- [4 Roles of X-Road member](#4-roles-of-x-road-member)
  * [4.1 In terms of dataservice](#41-in-terms-of-dataservice)
  * [4.2 In terms of management of security server](#42-in-terms-of-management-of-security-server)
- [5 X-Road interfacing steps](#5-x-road-interfacing-steps)
- [6 Elements of X-Road technology](#6-elements-of-x-road-technology)
  * [6.1 Technology in general](#61-technology-in-general)
  * [6.2 X-Road internal components](#62-x-road-internal-components)
  * [6.3 X-Road external components](#63-x-road-external-components)
  * [6.4 Elements of X-Road software](#64-elements-of-x-road-software)
    + [6.4.1 Service and message](#641-service-and-message)
    + [6.4.2 Subsystems and access rights](#642-subsystems-and-access-rights)
  * [6.5 X-Road protocols](#65-x-road-protocols)
  * [6.6 Logging and security](#66-logging-and-security)
  * [6.7 Identifiers and codes](#67-identifiers-and-codes)
  * [6.8 Global configuration concepts](#68-global-configuration-concepts)
- [7 Technical terms](#7-technical-terms)
  * [7.1 Trust and security terminology](#71-trust-and-security-terminology)
  * [7.2 General software terminology](#72-general-software-terminology)

<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/

## 1 X-Road and X-Road Instance

**External X-Road instance** – an instance that has been federated with the local instance. For example, the FI-instance is defined as an external instance in the EE's local point of view.

**Local X-Road instance** – a group of members that are registered in a particular instance.

**United/federated X-Road** – a legal, organizational and technical environment, enabling universal internet-based secure data exchange between the members of united/federated X-Road instances

**X-Road instance** – a legal, organizational and technical environment, enabling universal internet-based secure data exchange between the members of X-Road and limited to the participants administered by one governing authority.

## 2 Participants of X-Road

**Approved trust service provider** – participant of X-Road, who meets the requirements established by X-Road governing authority and has passed the process of recognition of X-Road trust service provider.

**End user of dataservice** – information system, part of information system or physical person, who uses data service through the information system of X-Road member.

**Local member** – a member entitled to exchange data/messages on the united X-Road and managed by governing authority of the local X-Road instance.

**United / Federated member** – a member entitled to exchange data/messages on their behalf on the united X-Road, but managed by governing authority of the external X-Road instance.

**X-Road Center** – participant of X-Road administering components of the X-Road software centre.

**X-Road governing authority** – authority, that sets the requirements for using X-road and establishing the procedure for using X-Road, managing and regulating participants of X-Road.

**X-Road member / member** – participant of X-Road entitled to exchange data/messages on X-Road.

## 3 Trust services

**Approved certification service provider** – Provider of a trust service approved on X-Road, who provides at least following trust services approved on X-Road: service of authentication certificate of security server, service of signature certificate of a member, and certificate validation service (OCSP).

**Approved timestamp service provider** – Provider of a trust service approved on X-Road, who provides the timestamp service.

**Authentication certificate of security server** – qualified certificate of e-stamp issued by certification service provider approved on X-Road and bound to security server, certifying authenticity of security server and used for authentication of security servers upon establishment of connection between security servers. Upon establishment of connection, it is checked from global configuration, if the security server trying to establish connection has registered the used authentication certificate in X-Road governing authority (i.e. the used authentication certificate is bound to the ID of security server).

**Certification authority** (**CA**) – is an entity that issues digital certificates. A digital certificate certifies the ownership of a public key by the named subject of the certificate.

**Certification service CA** – is used in the X-Road system as a trust anchor for a certification service. The certification service CA may, but does not have to be a Root CA.

**Certificate signing request**  (**CSR**) – is generated in the security server for a certain approved certification authority for signing a public key and associated information.

**Internal TLS certificates** – are used for setting up the TLS connection between the security server and the client information systems.

**Signature certificate of a member** – qualified certificate of e-stamp issued by certification service provider approved on X-Road and bound to a member, used for verification of the integrity of mediated messages and association of the member with the message.

**Timestamp** – means data in electronic form which binds other data in electronic form to a particular time establishing evidence that the latter data existed at that time (EU No 910/2014)

**Timestamping authority** (**TSA**) – is an entity that issues timestamps. Timestamps are used to prove the existence of certain data before a certain point of time without the possibility that the owner can backdate the timestamps.

**TLS certificate** – is a certificate used by the security server to authenticate the information system when HTTPS protocol is used for connections between the service client's or service provider's security server and information system.

**Validation service** (**OCSP**) – Validation service of the validity of certificate issued by certification service provider approved on X-Road.

## 4 Roles of X-Road member

### 4.1 In terms of dataservice

**Dataservice client** – member of X-Road responsible for using the dataservice in accordance with dataservice usage agreements. Technically, dataservice client is a party of interaction sending the request.

**Dataservice host** – A member enabling access to X-Road services through their information system (as the provider or user of the service) for natural or legal persons, who need not be members of X-Road.

**Dataservice provider** – member of X-Road responsible for dataservice provision, incl. granting the service SLA, managing the agreements with dataservice clients, granting access rights etc. Technically, dataservice provider is a party of interaction sending the response.

### 4.2 In terms of management of security server

**Security server client** – a member or a subsystem of a member, whose relation with the security server is registered in X-Road governing authority and who can use the security server on behalf of a member to exchange data on X-Road.

**Security server host** – a member who provides security server hosting services to third parties and other members.

**Security server owner** – a member responsible for security server and creation of a secure data exchange channel.

## 5 X-Road interfacing steps

**Affiliation of membership** – a process ending with becoming a member of X-Road. Becoming a member requires conclusion of affiliation contract and registration of data of the member (name and ID of the member) in X-Road central server. Requirements for affiliation are established by X-Road governing authority with relevant regulation/affiliation conditions

**Dataservice interfacing** – a process, where a member of X-Road creates organizational and technical capacity for offering or using dataservice. Interfacing includes development of the service by the member as well as its setup in security server, conclusion of service usage contracts and granting access rights. In order to use the service, service provider, as well as service client, shall undergo interfacing.

**Interaction** – activation procedure of dataservice (single use), bilateral information exchange through dataservice, i.e. request of dataservice by the service client by sending a request, to which the service provider will send a response.

**Registration of security server** – a process, where organizational and technical capacity of a member of X-Road is created to enable contacting the information system of the member of X-Road via X-Road. The result is a member of X-Road, with whom a secure data exchange channel of X-Road can be established. To ensure this, at least one security server shall be bound to the member in the central server.

**Registration of subsystem** – a process for establishing organizational and technical capacity to distinguish organizational users or user groups on the level of a subsystem. Technically, subsystems shall be registered as security server clients.

## 6 Elements of X-Road technology

### 6.1 Technology in general

**Core technology** – Component of X-Road software, ensuring integrity and verification value of messages between members. Core technology includes central server, configuration proxy, and security server.

**Service technology** – Component of X-Road software, simplifying or enabling the use of core technology.

### 6.2 X-Road internal components

**Central components** – are central server and configuration proxy.

**Central server** – the component that manages all registrations of a local X-Road instance (security servers, members, subsystems). It is the primary configuration source in an X-Road system. Central server always manages an internal configuration source (i.e. configuration source distributing the internal configuration) and in addition, an external configuration source (i.e. configuration source distributing the external configuration) in case the X-Road system is federation-capable.  

**Configuration proxy** – an intermediary that may optionally be used to mediate configuration originating from the central server to the configuration clients. Configuration proxy manages configuration sources that are used to distribute configuration downloaded from other configuration sources.
  - **Configuration proxy instance** – a process within the configuration proxy that deals with distributing the global configuration files of a specific X-Road instance.

**Security server** – standard software solution for using secure data exchange channel of X-Road and ensuring confidentiality, authenticity and integrity of messages/data exchanged on X-Road.
  
**System configuration** –  consists of data stored in the database, and in the various configuration files held in the file system of an X-Road component.

### 6.3 X-Road external components

**Adapter Service** – converts a request or response query to X-Road Message Protocol for SOAP or X-Road Message Protocol for REST. 

**Information system** – a system including technological as well as organizational information processing of a member of X-Road. The information system (IS) uses and/or provides services via the X-Road.

**Subsystem** – represents a part of an X-Road member's information system. X-Road members must declare parts of its information system as subsystems to use or provide X-Road services.

-   The access rights of an X-Road members’ subsystems are independent – access rights given to one subsystem do not affect the access rights of the members’ other subsystems.

-   Services provided by a subsystem are independent of the services provided by the members’ other subsystems.

-   To sign the messages sent by a subsystem when using or providing X-Road services, the signing certificate of the member that manages the subsystem is used. An X-Road member can associate several different subsystems with one security server, and one subsystem can be associated with several security servers.
 

### 6.4 Elements of X-Road software

#### 6.4.1 Service and message

**Dataservice** – web-service executed by a member of X-Road, in order to enable access to the resources of information system of X-Road dataservice provider. The predefined request-response, sent by the information system of a member to the information system of another member and receiving agreed data in response.

**Management service** – services provided by the X-Road governing organization to manage security servers and security server clients. Management services are implemented as standard X-Road services following X-Road message protocol.

**Message** – Data set meeting profile description and service description required by X-Road governing authority. Messages are divided into requests and responses. SOAP message consists of headers and a SOAP body that contains service specific content. REST message consists of HTTP verb, path, query parameters, HTTP headers and message body.

**Metadata service** – services between members executed by X-Road governing authority, enabling members of X-Road to get an overview of X-Road (e.g. enabling to get an overview of completed services and access rights needed for the consumption of services). Generally, it shall meet the description of X-Road service.

**Monitoring services** – The X-Road monitoring solution is conceptually split into two parts: environmental and operational monitoring. 

- **Environmental monitoring** – is the monitoring of the X-Road environment: details of the security servers such as operating system, memory, disk space, CPU load, running processes and installed packages, etc.

- **Operational monitoring** – is the monitoring of operational statistics such as which services have been called, how many times, what is the average response time, etc.
    + **Operational monitoring data** – contains operational data (such as which services have been called, how many times, what was the size of the response, etc.) of the X-Road security server(s).
    + **Operational monitoring daemon** – collects and shares operational monitoring data of the X-Road security server(s), calculates and shares health data of the X-Road security server(s) that is based on collected operational monitoring data.

**Service client** – is an X-Road member, subsystem, local access rights group or global access rights group that has access rights to one or more services of a security server client.

**X-Road service** – SOAP- or REST-based web service or API that is offered by an X-Road member or by a subsystem and that can be used by other X-Road members or subsystems.

#### 6.4.2 Subsystems and access rights

**Access right** – in X-Road technology enables specifying the rights of security server clients (subsystems) to use dataservices.

**Access right group** – set of security server clients (subsystems), enabling to grant access rights to the entire group of subsystems and to delegate administration of access rights to the group administrator. Logical name can be assigned to an access right group.

**Global access right group** – access right group administered in the central server by the central server administrator, usable in the entire X-Road federation.

**Local access right group** – access right group administered in security server by security server administrator, usable only in the specific security server within one security server client.

### 6.5 X-Road protocols

**Federation Protocol** – protocol that is used to distribute configuration between two federated X-Road instances.

**Message protocol** – protocol that is used between information systems and security servers in the X-Road system.

**Message Transport Protocol** – communications protocol that is used by service client's and service provider's security servers to exchange messages with each other.

**Protocol for Downloading Configuration** – protocol that is used to distribute configuration to security servers of an X-Road instance.

**Service Metadata Protocol** – protocol that describes methods that can be used by X-Road participants to discover what services are available to them and download the WSDL files describing these services.

### 6.6 Logging and security

**Audit log** – log, where the user actions (through user interface), when the user changes the system state or configuration, are logged regardless of whether the outcome was a success or failure.

**Batch signature** – e-stamp provided to a set of documents, enabling to separate a single document from the set and verify its signature.

**Message log** – a log, where exchanged X-Road messages are logged and provided with batch signature. Records all regular messages passing through the security server into the database. The messages are stored together with their signatures and signatures are timestamped. The purpose of the message log is to provide means to prove the reception of a request/response message to a third party.

**System service log** – a log which is made from a running system service of a security server, for example from xroad-confclient, -proxy, signer services.  

### 6.7 Identifiers and codes

**Global access group identifier** – identifier, that uniquely identifies access group in X-Road Network. Global access group identifier consists of X-Road instance identifier and global group code.

**Local access group identifier** – identifier, that uniquely identifies access group for a security server client. Global access group identifier consists of X-Road instance identifier and global group code.

**Member class** – identifier, that is identified by the X-Road governing authority and that uniquely identifies members with similar characteristics. All members with the same member class must be uniquely identifiable by their member codes.

**Member code** – identifier, that uniquely identifies an X-Road member within its member class. The member code remains unchanged during the entire lifetime of the member.

**Member identifier** – identifier, that uniquely identifies a member in the X-Road Network. Member identifier consists of X-Road instance identifier, member class, and member code.

**Security server code** – identifier, that uniquely identifies the security server in all of the security servers of the security server owner.

**Security server identifier** – identifier, that uniquely identifies security server in X-road Network. The security server identifier consists of security server owner identifier and security server code.

**Service identifier** – identifier, that uniquely identifies service in X-Road Network. The service identifier consists of member identifier of the service provider, service code and version of the service. Including version of the service in the service identifier is optional.

**Subsystem code** – code, that uniquely identifies subsystem in all of the subsystems of the member.

**Subsystem identifier** – identifier, that uniquely identifies subsystem in X-Road Network. Subsystem identifier consists of member identifier and subsystem code.

**X-Road instance identifier** – identifier, that uniquely identifies the X-road instance in the X-Road Network.

### 6.8 Global configuration concepts

**Configuration** – Set of parameters that are distributed by a configuration source. Configuration consists of one or more configuration parts that contain groups of related parameters.

**Configuration Anchor** – is a set of information that can be used by configuration clients to access a configuration source and to verify the downloaded configuration. The configuration anchor is distributed as either a separate XML file in case the anchor points to a local configuration source or as a part of private parameters in case the anchor points to the configuration source managed by a federation partner.

**Configuration Client** – is an entity that uses configuration anchor(s) for downloading configuration from configuration source(s). In an X-Roads system, security server and configuration proxy act as configuration clients. 

**Configuration part (file)** – is an XML file containing system parameters.

**Configuration Provider** – is an entity responsible for maintaining and distributing global configuration. The configuration provider manages one or two configuration sources through which configuration is made available for configuration clients. In an X-Roads system, the central server and the configuration proxy act as configuration providers.

**Configuration Source** – is a component (HTTP server) managed by a configuration provider. The configuration distributed by the source can either be internal configuration or external configuration. The information needed to access and download configuration from a source is contained in the configuration anchor.

**External configuration** – is distributed by a configuration source and only contains the shared parameters configuration part.

**Global configuration** – a technical solution, through which X-Road governing authority regulates participants of X-Road. Global configuration consists of XML-files, which are downloaded periodically from the central server of X-Road governing authority by security servers. Global configuration includes following information:

-   the addresses and public keys of trust anchors (certification service CAs and time stamping services);

-   the public keys of intermediate CAs;

-   the addresses and public keys of OCSP services (if not already available through the certificates' *Authority Information Access* extension);

-   information about X-Road members and their subsystems;

-   the addresses of the members' security servers registered in X-Road;

-   information about the security servers' authentication certificates registered in X-Road;

-   information about the security servers' clients registered in X-Road;

-   information about global access rights groups;

-   X-Road system parameters.  

**Internal configuration** – is distributed by a configuration source and is composed of the following configuration parts: private parameters; shared parameters, and; optionally, other configuration parts that are specific to an X-Road instance – optional parameters.

**Monitoring Parameters** – Set of parameters that control monitoring of security servers

**Optional parameters** – is an optional configuration part that carries system parameters that have a contextual meaning only to a specific X-Road system installation.

**Private parameters** – is a configuration part that holds system parameters that are only used by security servers that are part of the local X-Road system (i.e. the same X-Road system as the central server the configuration part originates from). In case of federated X-Road systems, the private parameters contain configuration anchors pointing to configuration sources distributing external configuration of federation partners.

**Shared parameters** – is a configuration part that holds system parameters that are used both by the security servers of the local X-Road system and by the security servers belonging to X-Road systems federated with the local system.

**Trusted anchor** – is a configuration anchor that points to the external configuration source of a federation partner and has been uploaded to the central server during the federation process. Trusted anchors are distributed to the configuration clients of the local X-Road system as a part of private parameters.

## 7 Technical terms

### 7.1 Trust and security terminology

**CA** - Certification Authority    

**HSM** – Hardware security module

**OCSP** – Online Certificate Status Protocol 

**SSH** - Secure Shell

**TLS** - Transport Layer Security

**TSA** - Timestamping Authority 

**TSP** - Time Stamp Provider

### 7.2 General software terminology

**API** Application Programming Interface

**CI** - Continuous Integration

**DSL** - Domain Specific Language

**GPG / GnuPG** - The GNU Privacy Guard

**HTTP** - Hypertext Transfer Protocol  

**HTTPS** - Hypertext Transfer Protocol Secure

**JMX** - The Java Management Extensions  
  
**JMXMP** - Java Management Extensions Messaging Protocol
  
**JSON** - JavaScript Object Notation  

**MBean** - Java Managed Bean  

**MIME** - Multipurpose Internet Mail Extensions

**RPC** – Remote Procedure Call

**REST** - Representational State Transfer

**SDK** - Software Development Kit

**SOAP** - Simple Object Access Protocol  

