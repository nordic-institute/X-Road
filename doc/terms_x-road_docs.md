# X-Road Terms and Abbreviations

**X-ROAD 8**

Version: 1.0  
Doc. ID:  TA-TERMS

## Version history

| Date       | Version | Description                                                                                                                                                      | Author           |
|------------|---------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------|
| 06.07.2015 | 0.1     | Initial draft                                                                                                                                                    |                  |
| 23.02.2017 | 0.2     | Converted to Github flavoured Markdown, added license text, adjusted tables and identification for better output in PDF. Added explanation of monitoring service | Toomas Mölder    |
| 14.11.2017 | 0.3     | All the descriptions in Estonian language removed. Couple of new descriptions added                                                                              | Antti Luoma      |
| 06.03.2018 | 0.4     | Moved/merged terminology explanations from other X-Road repository MD-documents to this document                                                                 | Tatu Repo        |
| 03.01.2019 | 0.5     | Minor changes - typos fixed.                                                                                                                                     | Yamato Kataoka   |
| 16.04.2019 | 0.6     | Add description of REST services.                                                                                                                                | Petteri Kivimäki |
| 02.06.2021 | 0.7     | Add backup encryption related terms.                                                                                                                             | Andres Allkivi   |
| 25.08.2021 | 0.8     | Update X-Road references from version 6 to 7                                                                                                                     | Caro Hautamäki   |
| 17.04.2023 | 0.9     | Remove central services support                                                                                                                                  | Justas Samuolis  |
| 11.11.2025 | 0.10    | Drop JMX                                                                                                                                                         | Justas Samuolis  |
| 27.02.2026 | 0.11    | Dataspace-aligned terminology, simplified model                                                                                                                  | Petteri Kivimäki |
| 03.07.2026 | 0.12    | Rename Participant Agent to Connector; add Decentralized Claims Protocol                                                                                         | Petteri Kivimäki |
| 07.09.2026 | 0.13    | Align terminology with the latest changes in dataspace standards and specifications, add additional terms                                                        | Petteri Kivimäki |
| 23.11.2026 | 1.0     | Frozen X-Road 8 vocabulary: all terms, with new, changed and replaced terms marked; X-Road 7 term mapping | Egidijus Milierius |

## Table of Contents

<!-- toc -->

- [License](#license)
- [Reading This Document](#reading-this-document)
- [1 Dataspace](#1-dataspace)
- [2 Governance and Roles](#2-governance-and-roles)
- [3 Participants of the Dataspace](#3-participants-of-the-dataspace)
- [4 Trust Services](#4-trust-services)
- [5 Data Sharing Concepts](#5-data-sharing-concepts)
- [6 Identity and Credentials](#6-identity-and-credentials)
- [7 Technical Components](#7-technical-components)
- [8 Services and Messages](#8-services-and-messages)
- [9 Protocols](#9-protocols)
- [10 Logging](#10-logging)
- [11 Identifier Structure](#11-identifier-structure)
- [12 Global Configuration](#12-global-configuration)
- [13 Abbreviations](#13-abbreviations)
- [14 X-Road 7 Term Mapping](#14-x-road-7-term-mapping)
- [15 Removed X-Road 7 Terms](#15-removed-x-road-7-terms)

<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike
3.0 Unported License. To view a copy of this license,
visit http://creativecommons.org/licenses/by-sa/3.0/

## Reading This Document

Terms that differ from X-Road 7 are marked *New in X-Road 8*, *Changed in X-Road 8* or *Replaces the X-Road 7
term*. Unmarked terms keep their X-Road 7 meaning. Section 14 maps every renamed X-Road 7 term to its new name.

Defined component, role and identifier names are capitalised (Connector, Registry, Participant, Dataspace).
"Dataspace" is one word. Examples use the identifier `DEV/COM/222`.

## 1 Dataspace

### Environment

Context determining the setting and circumstances of all influences upon a system.

*New in X-Road 8.*

### Dataspace

Environment enabling trusted Data Sharing between participating parties, based on an agreed
Governance Framework, along with an agreed set of Policies, semantic models, standardised protocols,
processes, and facilitating services. In X-Road, a Dataspace is one Registry with the Participants
and Connectors registered in it. As a lower-case qualifier, "dataspace" means "of the Dataspace
Protocol and Decentralised Claims Protocol features" (dataspace TLS, dataspace provisioning).

*Replaces the X-Road 7 term "X-Road instance".*

### Local Dataspace

The Dataspace whose Registry a Connector or Participant is registered in.

*Replaces the X-Road 7 term "Local X-Road instance".*

### External Dataspace

A Dataspace federated with the local Dataspace.

*Replaces the X-Road 7 term "External X-Road instance".*

### Federation

Interconnection between two or more Dataspaces enabling cross-dataspace data sharing. Never used for
the Participants of one Dataspace.

*Replaces the X-Road 7 term "United/federated X-Road".*

## 2 Governance and Roles

### Dataspace Governance Authority

The role responsible for establishing, governing, managing and enforcing the technical policies and
business rules of a Dataspace.

*Replaces the X-Road 7 term "X-Road governing authority".*

### Dataspace Operator

An entity responsible for operating technical services of a Dataspace on behalf of the Dataspace
Governance Authority, for example the Registry. Operational responsibilities may be delegated, but
governance control remains with the Dataspace Governance Authority. Always written in full; a person
who runs a Connector or a Registry is its administrator.

*Replaces the X-Road 7 terms "X-Road Center", "X-Road operator".*

### Governance

Human-based system comprising directing, overseeing and accountability.

*New in X-Road 8.*

### Governance Framework

Strategies, policies, decision-making structures and accountabilities through which the Dataspace
Governance arrangements operate.

*New in X-Road 8.*

### Trust Framework

A composition of policies, rules, standards, and procedures designed for trust decisions in
Dataspaces based on assurances. The Trust Framework is part of the Governance Framework.

*New in X-Road 8.*

## 3 Participants of the Dataspace

### Participant Role

A set of activities within a Dataspace for the purpose of data sharing or related activities, for
example Provider or Consumer. Not an administrator role in a user interface (Security Officer,
System Administrator, …).

*New in X-Road 8.*

### Participant

A legal person acting in a Dataspace Participant Role and registered in a Dataspace. A Participant
may provide and/or consume Datasets.

*Replaces the X-Road 7 term "X-Road member".*

### Local Participant

A Participant registered in the local Dataspace.

*Replaces the X-Road 7 term "Local member".*

### Federated Participant

A Participant registered in an external Dataspace that can exchange data with the local Dataspace
through Federation.

*Replaces the X-Road 7 term "United/Federated member".*

### Provider

A Participant that offers a Dataset to other Participants.

*Replaces the X-Road 7 term "Dataservice provider".*

### Consumer

A Participant that requests access to an offered Dataset.

*Replaces the X-Road 7 term "Dataservice client".*

### Connector owner

The Participant responsible for a Connector. Its Participant Identifier is part of the Connector
Identifier.

*Replaces the X-Road 7 term "Security server owner".*

### Connector client

A Participant or a Subsystem whose relation with a Connector is registered in the Registry, and
which uses that Connector to exchange data.

*Replaces the X-Road 7 term "Security server client".*

### Connector host

A Participant that runs Connectors for other Participants.

*Replaces the X-Road 7 term "Security server host".*

### End user

A person or system that uses a service through the Information System of a Participant.

*Replaces the X-Road 7 term "End user of dataservice".*

### Registration of a Participant

The process of becoming a Participant: concluding the agreement required by the Dataspace Governance
Authority and registering the Participant's name and Participant Identifier in the Registry.

*Replaces the X-Road 7 term "Affiliation of membership".*

### Registration of a Connector

The process of registering a Connector, its owner and its authentication certificate in the
Registry.

*Replaces the X-Road 7 term "Registration of security server".*

### Registration of a Subsystem

The process of registering a Subsystem in the Registry and as a client of a Connector.

## 4 Trust Services

Trust Services provide cryptographic assurance mechanisms that support authentication, integrity,
and non-repudiation within the Dataspace.

### Trust Anchor

An entity accredited by the Dataspace Governance Authority to formally confirm that certain
requirements, properties, or conditions are met. Others in the Dataspace rely on its attestations.
In X-Road the Trust Anchors are the Approved Certification Service Providers, the Approved Timestamp
Service Providers, the Approved Dataspace TLS Certification Authorities and the credential issuers
(the Issuer Service). Not a configuration anchor or a trusted anchor (section 12).

*New in X-Road 8.*

### Certification Authority (CA)

An entity that issues digital certificates. A digital certificate binds a public key to the identity
of its subject and enables verification of authenticity and integrity.

*Changed in X-Road 8.*

### Approved Certification Service Provider

A Certification Authority approved by the Dataspace Governance Authority to issue certificates used
within the Dataspace. It may issue authentication certificates for Connectors and signing
certificates for Participants, and provide Validation services (OCSP). It acts as a Trust Anchor. It
may be a root CA or an intermediate CA.

*Changed in X-Road 8.*

### Certification service CA

The CA certificate of an Approved Certification Service Provider that is explicitly trusted by the
Dataspace. It may be either a root CA certificate or an intermediate CA certificate.

### Approved Dataspace TLS Certification Authority

A Certification Authority approved by the Dataspace Governance Authority to issue Dataspace TLS
certificates. It is listed separately from Approved Certification Service Providers and may be a
public CA. It acts as a Trust Anchor for dataspace TLS only.

*New in X-Road 8.*

### Validation service (OCSP)

A service that provides real-time validation of the status of digital certificates. It confirms
whether a certificate is valid, revoked, or expired. Provided by an Approved Certification Service
Provider; user interfaces call it an OCSP responder.

*Changed in X-Road 8.*

### Timestamping Authority (TSA)

An entity that issues cryptographic timestamps. Timestamps provide verifiable proof that specific
data existed at a certain point in time and prevent backdating.

*Changed in X-Road 8.*

### Approved Timestamp Service Provider

A Timestamping Authority approved by the Dataspace Governance Authority to provide timestamp
services within the Dataspace. It acts as a Trust Anchor.

*Changed in X-Road 8.*

### Timestamp

Data in electronic form that binds other data to a particular time, establishing evidence that the
bound data existed at that time (EU No 910/2014).

*Changed in X-Road 8.*

### Authentication certificate

A certificate issued by an Approved Certification Service Provider and bound to a Connector. It
authenticates the Connector when Connectors connect to each other. The Registry records which
authentication certificates belong to which Connector.

*Replaces the X-Road 7 term "Authentication certificate of security server".*

### Signing certificate

A certificate issued by an Approved Certification Service Provider and bound to a Participant. It
verifies the Participant's signatures on messages.

*Replaces the X-Road 7 term "Signature certificate of a member".*

### Certificate signing request (CSR)

A request generated in a Connector or a Registry, asking a Certification Authority to sign a public
key and the information bound to it.

### Dataspace TLS certificate

The TLS server certificate on the dataspace endpoints of a Connector or a Registry (Dataspace
Protocol, `did:web`, Decentralised Claims Protocol and, on the Registry, credential issuance).
Issued only by an Approved Dataspace TLS Certification Authority, usually through ACME.

*New in X-Road 8.*

### TLS certificate (information system)

The certificate a Connector uses to authenticate an Information System, or to be authenticated by
it, when they connect over HTTPS.

### Internal TLS certificates

Certificates used for TLS between the Connector and the Information Systems of its clients.

### ACME

Automatic Certificate Management Environment, a protocol through which a Connector or a Registry
obtains and renews certificates from a Certification Authority automatically. **External account
binding (EAB)** links an ACME account to an account the operator already holds at the CA.

*New in X-Road 8.*

### Secret store

The component that holds a server's keys and secrets, including the Dataspace TLS key. X-Road uses
OpenBao.

*New in X-Road 8.*

## 5 Data Sharing Concepts

### Dataset

Data or a technical service that can be shared by a Participant. In X-Road, each X-Road service is
offered as one Dataset in the Dataspace Protocol catalog.

*Replaces the X-Road 7 term "Dataservice".*

### Data Sharing

Access to the same data by more than one authorised entity.

*New in X-Road 8.*

### Policy

A set of rules, duties and obligations defining the terms of use for a Dataset. Also called usage
policy. In X-Road, a Connector derives Policies from the access rights of its services. Other
policies (PIN, password, security policy) are always qualified.

*New in X-Road 8.*

### Offer

A concrete Policy associated with a specific Dataset.

*New in X-Road 8.*

### Agreement

A Policy agreed between a Provider and a Consumer as the result of Contract Negotiation.

*New in X-Road 8.*

### Agreement token

A signed token that the consumer Connector sends with a negotiated call to prove the Agreement
behind it. The provider Connector checks it before the access rights. Signed with the agreement
token signing key.

*New in X-Road 8.*

### Contract Negotiation

A set of interactions between Provider and Consumer that establish an Agreement.

*New in X-Road 8.*

### Transfer Process

A set of interactions between a Provider and Consumer that give access to a Dataset under the terms
of an Agreement.

*New in X-Road 8.*

### Data Sharing Contract

A legally binding agreement between Participants containing policies, terms and conditions for data
sharing. It is a legal document; the Agreement is its technical counterpart in the Dataspace
Protocol.

*New in X-Road 8.*

### Negotiated call

A call between Connectors that follows a Contract Negotiation and carries an agreement token.

*New in X-Road 8.*

### Unnegotiated call

A call between Connectors without a Contract Negotiation before it, authorised only by mutual TLS
and the provider's access rights. It exists for compatibility with X-Road 7 and is used only when
the called Participant has no published DID.

*New in X-Road 8.*

### Legacy protocol

The settings that allow or refuse unnegotiated calls: `legacyProtocolDisabled` for the Dataspace and
`xroad.proxy.legacy-protocol-disabled` per Connector. When set, a Connector refuses legacy calls.
Not protocol version 4.

*New in X-Road 8.*

## 6 Identity and Credentials

### Decentralised Identifier (DID)

A globally unique identifier that resolves to a DID document with the subject's public keys and
service endpoints. X-Road uses `did:web`. Not a Dataspace Identifier and not a Participant
Identifier.

*New in X-Road 8.*

### did:web

A DID method that publishes the DID document over HTTPS on the subject's own web address.

*New in X-Road 8.*

### DID document

The JSON document a DID resolves to: public keys, service endpoints, verification methods.

*New in X-Road 8.*

### Participant DID

The DID of one Participant on a Connector. The Connector creates it, binds it and publishes it
through the Registry.

*New in X-Road 8.*

### System DID

The DID of a Connector itself, used for the Connector's own traffic.

*New in X-Road 8.*

### Participant context

The Dataspace Protocol state that a Connector holds for one identity: one per Participant it serves,
plus one system context for the Connector itself. A system context is not a Participant. Always
written in full.

*New in X-Road 8.*

### Participant binding

The stored record of a Participant's DID and participant context on a Connector. A Participant is
bound once the record exists and unbound before.

*New in X-Road 8.*

### DSP base URL

The address at which a Connector serves the Dataspace Protocol. It is published through the Registry
with the Connector's DIDs.

*New in X-Road 8.*

### Verifiable credential

A tamper-evident credential whose issuer can be verified cryptographically.

*New in X-Road 8.*

### Verifiable presentation

A signed bundle of one or more verifiable credentials, presented by their holder.

*New in X-Road 8.*

### Credential issuer

An entity that issues verifiable credentials. In X-Road, the Issuer Service; global configuration
lists the trusted credential issuers.

*New in X-Road 8.*

### Issuer Service

The Registry component that issues membership credentials to Participants. Always written in full,
never "IS".

*New in X-Road 8.*

### Identity Hub

The Connector component that serves the `did:web` documents of its Participants and holds their
credentials.

*New in X-Road 8.*

### Membership credential

The verifiable credential that the Issuer Service issues to a Participant to prove it is registered
in the Dataspace.

*New in X-Road 8.*

## 7 Technical Components

### Connector

A technical system that performs operations and interactions in a Dataspace on behalf of a
Participant. A Connector implements the Dataspace Protocol and the Decentralised Claims Protocol,
manages Contract Negotiation and Transfer Processes and enables Dataset sharing. The Dataspace
Protocol calls such a system a Participant Agent. Used only for this component: Jetty listeners are
"HTTP listeners", EDC components are the "EDC runtime".

*Replaces the X-Road 7 term "Security server".*

### Registry

A system that maintains the authoritative state of Participants and their Connectors within a
Dataspace. A container image registry is always called "container image registry".

*Replaces the X-Road 7 term "Central server".*

### Registry Proxy

A component that distributes or relays Registry configuration.

*Replaces the X-Road 7 term "Configuration proxy".*

### Central components

The Registry and the Registry Proxy.

*Changed in X-Road 8.*

### Management Connector

The Connector through which the management services are provided to other Connectors.

*Replaces the X-Road 7 term "Management security server".*

### Connector cluster

Connector nodes that share one database and one identity; to other Connectors a cluster is one
Connector.

*New in X-Road 8.*

### Information System

A technical system operated by a Participant that processes, stores, or manages data and supports
the Participant's activities. It may expose one or more Subsystems.

*Changed in X-Road 8.*

### Subsystem

A logical part of a Participant's Information System that is independently identifiable within the
Dataspace. A Subsystem must be registered in the Registry and is used as a client on a Connector to
consume and/or provide services. X-Road-specific; no direct dataspace equivalent.

*Changed in X-Road 8.*

### Adapter Service

Converts a request or response to the X-Road message protocol for SOAP or for REST.

### Catalog

In the Dataspace Protocol, a collection of entries representing Offers advertised by a Provider.
More broadly, a service that lets Participants discover Datasets, services and other resources in
one or more Dataspaces.

*New in X-Road 8.*

### Control plane

The Connector layer that discovers a Provider and negotiates the terms before any request is sent.

*New in X-Road 8.*

### X-Road data plane

The Connector layer that carries the request itself between two Connectors: mutual TLS plus a signed
X-Road message.

*New in X-Road 8.*

### Sidecar

The containerised Connector distribution.

*New in X-Road 8.*

### System configuration

Data stored in the database and in configuration files of an X-Road component.

## 8 Services and Messages

### X-Road service

A SOAP or REST service offered by a Participant or a Subsystem through a Connector. In the Dataspace
Protocol it is offered as a Dataset.

### Service provider

The Participant or Subsystem that offers an X-Road service.

### Service client

A Participant, Subsystem, or access right group that has access rights to one or more X-Road
services.

### Management service

A service provided by the Dataspace Operator to manage Connectors and Connector clients, built as an
X-Road service.

### Management request

A request that a Connector sends through a management service, for example to register a client or
publish its DIDs.

*New in X-Road 8.*

### Metadata service

A service that lets Participants discover what is available in the Dataspace, for example which
services a Provider offers.

### Monitoring services

Environmental and operational monitoring of Connectors.

### Message

A request or a response that follows the X-Road message protocol.

### Interaction

One request–response exchange between a service client and a service provider.

### Access right

Permission for a Connector client, a Participant or an access right group to use an X-Road service.

### Access right group

A set of Connector clients that receive access rights together.

### Global access right group

An access right group administered in the Registry and usable in the whole Dataspace and its
Federation.

### Local access right group

An access right group administered in one Connector and usable only for services belonging to one
Connector client.

## 9 Protocols

### Dataspace Protocol (DSP)

A set of messages and message sequences enabling interaction between Connectors in a Dataspace.

*New in X-Road 8.*

### Decentralised Claims Protocol (DCP)

A protocol that operates as an overlay to the Dataspace Protocol for conveying Participant
identities and verifiable credentials, and for establishing trust in a decentralised way.

*New in X-Road 8.*

### Message protocol

The protocol between Information Systems and Connectors: X-Road message protocol for SOAP and for
REST.

### Protocol version

The version of the X-Road SOAP message protocol a call uses: 4 today, with X-Road 7 names; 5 later,
with the names of this vocabulary. The Dataspace Operator enables versions per Dataspace. The REST
message protocol stays `r1`.

*New in X-Road 8.*

### Message Transport Protocol

The protocol used for exchanging messages between the Connectors of a service client and a service
provider, effectively implementing the X-Road data plane.

### Federation Protocol

The protocol that distributes configuration between two federated Dataspaces.

### Protocol for Downloading Configuration

The protocol that distributes global configuration to the Connectors of a Dataspace.

### Service Metadata Protocol

The protocol through which Participants discover available services and download their descriptions.

## 10 Logging

### Audit log

A log of user attempts to change the state or configuration of a system, regardless of success or
failure.

### Message log

A log of the X-Road messages passing through a Connector, with batch signatures.

### Batch signature

A signature over a set of documents that lets one document be separated from the set and verified
alone.

### System service log

The log of a system service of a Connector or Registry.

## 11 Identifier Structure

X-Road uses structured identifiers to ensure their global uniqueness. Identifiers are hierarchical
and composable. Examples use the `DEV` Dataspace.

### Dataspace Identifier

An identifier that uniquely identifies a Dataspace and ensures uniqueness across federated
Dataspaces. Example: `DEV`. Not a DID.

*Replaces the X-Road 7 term "X-Road instance identifier".*

### Participant Identifier

Uniquely identifies a Participant: Dataspace Identifier + Participant Class + Participant Code.
Example: `DEV/COM/222`. Not a DID and not a participant context id.

*Replaces the X-Road 7 term "Member identifier".*

### Participant Class

A classification assigned by the Dataspace Governance Authority to distinguish categories of
Participants with similar characteristics. All Participants within the same Participant Class must
be uniquely identifiable by their Participant Code.

*Replaces the X-Road 7 term "Member class".*

### Participant Code

A unique code identifying a Participant within its Participant Class. It stays stable for the
Participant's lifetime.

*Replaces the X-Road 7 term "Member code".*

### Subsystem Identifier

Uniquely identifies a Subsystem in the Dataspace: Participant Identifier + Subsystem Code. Example:
`DEV/COM/222/TESTCLIENT`.

*Changed in X-Road 8.*

### Subsystem Code

A code uniquely identifying a Subsystem within a Participant.

### Connector Identifier

Uniquely identifies a Connector within a Dataspace: Participant Identifier of the Connector owner +
Connector Code.

*Replaces the X-Road 7 term "Security server identifier".*

### Connector Code

A code uniquely identifying a Connector among the Connectors of its owner.

*Replaces the X-Road 7 term "Security server code".*

### Service identifier

Uniquely identifies an X-Road service: Participant or Subsystem Identifier of the service provider +
service code (+ optional version). Example: `DEV/COM/222/TESTSERVICE/petstore`.

### Global access group identifier

Dataspace Identifier + group code.

### Local access group identifier

Identifies an access right group within one Connector client.

## 12 Global Configuration

### Global configuration

The signed configuration through which the Dataspace Governance Authority regulates the Dataspace.
The Registry produces it; Connectors download it.

### Global configuration version

The schema version of global configuration. X-Road 8 introduces version 7; versions 2 to 6 stay for
X-Road 7 Connectors.

*New in X-Road 8.*

### Configuration

A set of parameters distributed by a configuration source, made of configuration parts.

### Configuration part

An XML file containing system parameters.

### Configuration provider

The entity that maintains and distributes global configuration: the Registry, or a Registry Proxy.

### Configuration source

An HTTP server managed by a configuration provider.

### Configuration client

An entity that uses configuration anchors to download configuration; in X-Road, Connectors and
Registry Proxies.

### Configuration anchor

The information a configuration client uses to reach a configuration source and verify the
downloaded configuration. Not a Trust Anchor.

### Trusted anchor

A configuration anchor of an external Dataspace, uploaded to the Registry to set up Federation. Not
a Trust Anchor.

### Internal configuration

Configuration for the local Dataspace: private parameters, shared parameters and optional parts.

### External configuration

Configuration for federated Dataspaces: shared parameters only.

### Monitoring parameters

The configuration part with the parameters that control the monitoring of Connectors.

### Optional parameters

An optional configuration part with system parameters that only a specific X-Road installation uses.

### Private parameters

The configuration part with system parameters used only by the Connectors of the local Dataspace.

### Shared parameters

The configuration part with system parameters used by the Connectors of the local Dataspace and of
federated Dataspaces.

### Maintenance mode

A Connector state, published in global configuration, that marks the Connector as not available.
Clients calling it receive an error with the administrator's optional message.

*New in X-Road 8.*

## 13 Abbreviations

| Abbreviation | Meaning |
|---|---|
| API | Application Programming Interface |
| CA | Certification Authority |
| CI | Continuous Integration |
| CSR | Certificate Signing Request |
| DCP | Decentralised Claims Protocol |
| DID | Decentralised Identifier |
| DSL | Domain Specific Language |
| DSP | Dataspace Protocol |
| EAB | External Account Binding |
| GPG | GNU Privacy Guard |
| HSM | Hardware Security Module |
| HTTP | Hypertext Transfer Protocol |
| HTTPS | Hypertext Transfer Protocol Secure |
| JSON | JavaScript Object Notation |
| MIME | Multipurpose Internet Mail Extensions |
| OCSP | Online Certificate Status Protocol |
| REST | Representational State Transfer |
| RPC | Remote Procedure Call |
| SDK | Software Development Kit |
| SOAP | Simple Object Access Protocol |
| SSH | Secure Shell |
| TLS | Transport Layer Security |
| TSA | Timestamping Authority |

Connector, Registry, Registry Proxy, Information System and Issuer Service are never abbreviated.
"DS" appears only inside code names.

## 14 X-Road 7 Term Mapping

| X-Road 7 | X-Road 8 |
|---|---|
| X-Road instance / local / external | Dataspace / local / external Dataspace |
| United / federated X-Road | Federation |
| X-Road instance identifier | Dataspace Identifier |
| X-Road member, local / federated member | Participant, local / federated Participant |
| Member class / code / identifier | Participant Class / Code / Identifier |
| X-Road governing authority | Dataspace Governance Authority |
| X-Road Center, X-Road operator | Dataspace Operator |
| Security server | Connector |
| Security server owner / client / host / code / identifier | Connector owner / client / host / Code / Identifier |
| Management security server | Management Connector |
| Central server | Registry |
| Configuration proxy | Registry Proxy |
| Dataservice | Dataset |
| Dataservice provider / client | Provider / Consumer |
| End user of dataservice | End user |
| Authentication certificate of security server | Authentication certificate |
| Signature certificate of a member | Signing certificate |
| Affiliation of membership | Registration of a Participant |
| Registration of security server | Registration of a Connector |

## 15 Removed X-Road 7 Terms

| Term | Why |
|---|---|
| Core technology, Service technology | X-Road 7 product framing; not used in X-Road 8 docs |
| Dataservice host | Covered by Connector host and Provider |
| Dataservice interfacing | Covered by Registration of a Subsystem and Access right |
| Approved trust service provider | Covered by Trust Anchor and the two Approved … Service Provider entries |
| TSP (Time Stamp Provider) | Ambiguous with Trust Service Provider; see Timestamping Authority |
