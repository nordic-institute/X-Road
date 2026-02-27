# X-Road Terms and Abbreviations

**X-ROAD 8**

Version: 0.11  
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
| 11.11.2025 | 0.10    | Drop JMX                                                                                                                                                         | Petteri Kivimäki |
| 27.02.2026 | 0.11    | Dataspace-aligned terminology, simplified model                                                                                                                                                         | Justas Samuolis  |
## Table of Contents

<!-- toc -->

- [License](#license)
- [1 Dataspace and Dataspace Identifier](#1-dataspace-and-dataspace-identifier)
- [2 Participants of the Dataspace](#2-participants-of-the-dataspace)
- [3 Trust Services](3#-trust-services)
- [4 Governance and Roles](#3-governance-and-roles)
- [5 Data Sharing Concepts](#4-data-sharing-concepts)
- [6 Technical Components](#5-technical-components)
- [7 Identifier Structure](#6-identifier-structure)
- [8 Technical Terms](#7-technical-terms)

<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/

## 1 Dataspace and Dataspace Identifier

### Dataspace

A governance framework and supporting technical services that enable
trusted data sharing between Participants based on agreed policies,
semantic models, protocols and processes.

In X-Road context, a Dataspace replaces the concept previously known as
an X-Road instance.

### Federation

Interconnection between two or more dataspaces enabling cross-dataspace
data sharing.

## 2 Participants of the Dataspace

### Participant

A legal or natural person acting in a dataspace participant role and
registered in a Dataspace.

A Participant may provide and/or consume Datasets.

### Provider

A Participant that offers a Dataset to other Participants.

### Consumer

A Participant that requests access to an offered Dataset.

### Participant Role

A set of activities within a Dataspace for the purpose of data sharing
or related activities.

## 3 Trust Services

Trust Services provide cryptographic assurance mechanisms that support authentication, integrity, and non-repudiation within the Dataspace.

### Certification Authority (CA)

An entity that issues digital certificates.

A digital certificate binds a public key to the identity of its subject and enables verification of authenticity and integrity.

Within X-Road, a Certification Authority acts as a trust anchor. It may be a Root CA or an intermediate CA.

### Approved Certification Service Provider

A Certification Authority approved by the Dataspace Governance Authority to issue certificates used within the Dataspace.

An Approved Certification Service Provider may provide:

- Authentication certificates for Participant Agents
- Sign certificates for Participants
- Certificate status validation services (OCSP)

### Validation service (OCSP)

A service that provides real-time validation of the status of digital certificates.

It confirms whether a certificate is valid, revoked, or expired.

### Timestamping Authority (TSA)

An entity that issues cryptographic timestamps.

Timestamps provide verifiable proof that specific data existed at a certain point in time and prevent backdating.

### Approved Timestamp Service Provider

A Timestamping Authority approved by the Dataspace Governance Authority to provide timestamp services within the Dataspace.

### Timestamp

Data in electronic form that binds other data to a particular time, establishing evidence that the bound data existed at that time (EU No 910/2014).

## 4 Governance and Roles

### Dataspace Governance Authority

The role responsible for establishing, governing, managing and enforcing
the technical policies and business rules of a Dataspace.

### Dataspace Operator

An entity responsible for operating technical services of a Dataspace on
behalf of the Governance Authority.

Operational responsibilities may be delegated, but governance control
remains with the Governance Authority.

### Governance Framework

Strategies, policies and decision-making structures through which
Dataspace governance operates.

## 5 Data Sharing Concepts

### Dataset

Data or a technical service that can be shared by a Participant.

This replaces the former concept of dataservice.

### Policy

A set of rules, duties and obligations defining the terms of use for a
Dataset.

### Offer

A concrete Policy associated with a specific Dataset.

### Agreement

A Policy agreed between a Provider and a Consumer as the result of
Contract Negotiation.

### Contract Negotiation

A set of interactions between Provider and Consumer that establish an
Agreement.

### Transfer Process

Interactions between Provider and Consumer that give access to a Dataset
under an Agreement.

### Dataspace Protocol

A set of messages and message sequences enabling interaction between
Participant Agents in a Dataspace.

### Data Sharing Contract

A legally binding agreement between Participants containing policies,
terms and conditions for data sharing.

## 6 Technical Components

### Participant Agent

A technical system that performs operations and interactions in a
Dataspace on behalf of a Participant.

A Participant Agent:

- Implements Dataspace Protocols
- Manages Contract Negotiation and Transfer Processes
- Enables Dataset sharing

The term Connector may be used as an equivalent term in dataspace
protocol contexts.

### Registry

A system that maintains the authoritative state of Participants and
their technical endpoints within a Dataspace.

### Registry Proxy

A component that distributes or relays Registry configuration.

### Subsystem

A logical part of a Participant's information system that is
independently identifiable within the Dataspace. Participants
must declare parts of their information system as subsystems
to consume or provide Data Assets.

Subsystem is an X-Road-specific concept and has no direct dataspace
equivalent.

## 7 Identifier Structure

X-Road uses structured identifiers to ensure their global uniqueness.

Identifiers are hierarchical and composable.

### Dataspace Identifier

An identifier that uniquely identifies a Dataspace.

The Dataspace Identifier ensures global uniqueness across federated
dataspaces.

### Participant Identifier

Uniquely identifies a Participant within a Dataspace.

It consists of:

- Dataspace Identifier
- Participant Class
- Participant Code

#### Participant Class

A classification assigned by the Dataspace Governance Authority to
distinguish categories of Participants with similar characteristics.

All Participants within the same Participant Class must be uniquely
identifiable by their Participant Code.

#### Participant Code

A unique code identifying a Participant within its Participant Class.

The Participant Code remains stable throughout the lifetime of the
Participant.

#### Formal Structure

Participant Identifier = Dataspace Identifier + Participant Class + Participant Code

### Subsystem Identifier

Uniquely identifies a Subsystem within a Participant.

It consists of:

- Participant Identifier
- Subsystem Code

#### Subsystem Code

A code uniquely identifying a Subsystem within a Participant.

#### Formal Structure

Subsystem Identifier = Participant Identifier + Subsystem Code

### Participant Agent Identifier

Uniquely identifies a Participant Agent within a Dataspace.

It consists of:

- Participant Identifier
- Agent Code

#### Agent Code

A code uniquely identifying a Participant Agent under a specific
Participant.

## 8 Technical Terms

**API** -- Application Programming Interface\
**HTTP** -- Hypertext Transfer Protocol\
**HTTPS** -- Hypertext Transfer Protocol Secure\
**JSON** -- JavaScript Object Notation\
**REST** -- Representational State Transfer\
**SOAP** -- Simple Object Access Protocol\
**TLS** -- Transport Layer Security\
**OCSP** -- Online Certificate Status Protocol\
**CA** -- Certification Authority\
**TSA** -- Timestamping Authority
