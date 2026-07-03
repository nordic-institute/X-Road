# X-Road Terms and Abbreviations

**X-ROAD 8**

Version: 0.12  
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
## Table of Contents

<!-- toc -->

- [License](#license)
- [1 Dataspace](#1-dataspace)
- [2 Governance and Roles](#2-governance-and-roles)
- [3 Participants of the Dataspace](#3-participants-of-the-dataspace)
- [4 Trust Services](#4-trust-services)
- [5 Data Sharing Concepts](#5-data-sharing-concepts)
- [6 Technical Components](#6-technical-components)
- [7 Identifier Structure](#7-identifier-structure)
- [8 Technical Terms](#8-technical-terms)

<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike
3.0 Unported License. To view a copy of this license,
visit http://creativecommons.org/licenses/by-sa/3.0/

## 1 Dataspace

### Dataspace

A governance framework and supporting technical services that enable
trusted data sharing between Participants based on agreed policies,
semantic models, protocols and processes.

In X-Road context, a Dataspace replaces the concept previously known as
an X-Road instance.

### Federation

Interconnection between two or more dataspaces enabling cross-dataspace
data sharing.

## 2 Governance and Roles

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

### Trust Framework

A composition of policies, rules, standards, and procedures designed
for trust decisions in Dataspaces based on assurances. Trust Framework
is part of the Dataspace Governance Framework.

## 3 Participants of the Dataspace

### Participant Role

A set of activities within a Dataspace for the purpose of data sharing
or related activities.

### Participant

A legal person acting in a Dataspace Participant role and
registered in a Dataspace.

A Participant may provide and/or consume Datasets.

### Provider

A Participant that offers a Dataset to other Participants.

### Consumer

A Participant that requests access to an offered Dataset.

## 4 Trust Services

Trust Services provide cryptographic assurance mechanisms that support
authentication, integrity, and non-repudiation within the Dataspace.

### Trust Anchor

A Trust Anchor is an entity accredited by the Dataspace Governance Authority
to formally confirm that certain requirements, properties, or conditions
are met.

Trust Anchors issue attestations that other Participants in the Dataspace
can rely on as trustworthy statements about specific claims.

A Trust Anchor may be a Certificate Authority (CA) or another approved
authority authorised to issue such attestations.

### Certification Authority (CA)

An entity that issues digital certificates.

A digital certificate binds a public key to the identity of its subject and
enables verification of authenticity and integrity.

### Approved Certification Service Provider

A Certification Authority approved by the Dataspace Governance Authority
to issue certificates used within the Dataspace.

An Approved Certification Service Provider may provide:

- Authentication certificates for Connectors
- Sign certificates for Participants
- Certificate status validation services (OCSP)

Within X-Road, an Approved Certification Service Provider acts as a Trust Anchor. It
may be a Root CA or an intermediate CA.

### Validation service (OCSP)

A service that provides real-time validation of the status of digital
certificates. It confirms whether a certificate is valid, revoked, or expired.

The service is provided by an Approved Certification Service Provider.

### Timestamping Authority (TSA)

An entity that issues cryptographic timestamps.

Timestamps provide verifiable proof that specific data existed at
a certain point in time and prevent backdating.

### Approved Timestamp Service Provider

A Timestamping Authority approved by the Dataspace Governance Authority
to provide timestamp services within the Dataspace.

Within X-Road, an Approved Timestamping Authority acts as a Trust Anchor.

### Timestamp

Data in electronic form that binds other data to a particular time,
establishing evidence that the bound data existed at that time (EU No 910/2014).

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
Connectors in a Dataspace.

### Decentralized Claims Protocol

A protocol that operates as an overlay to the Dataspace Protocol for
conveying Participant identities and verifiable credentials, and for
establishing trust in a decentralized way.

It supports issuing, storing and presenting Verifiable Credentials using
multiple trust anchors, without relying on third-party verification.

### Data Sharing Contract

A legally binding agreement between Participants containing policies,
terms and conditions for data sharing.

## 6 Technical Components

### Connector

A technical system that performs operations and interactions in a
Dataspace on behalf of a Participant.

A Connector:

- Implements the Dataspace Protocol (DSP) and Decentralized Claims Protocol (DCP)
- Manages Contract Negotiation and Transfer Processes
- Enables Dataset sharing

In the Dataspace Protocol, a Connector is a Participant Agent that performs
Contract Negotiation and Transfer Process operations.

In X-Road context, a Connector replaces the component previously known as a Security Server.

### Registry

A system that maintains the authoritative state of Participants and
their Connectors within a Dataspace.

In X-Road context, a Registry replaces the component previously known as a Central Server.

### Registry Proxy

A component that distributes or relays Registry configuration.

In X-Road context, a Registry Proxy replaces the component previously known as a Configuration Proxy.

### Information System

A technical system operated by a Participant that processes, stores,
or manages data and supports the Participant’s activities.

An Information System may act as a Provider, a Consumer, or both within
a Dataspace by exposing or consuming Datasets through one or more
Subsystems.

An Information System may consist of multiple technical components
and may expose one or more Subsystems that are independently identifiable
within a Dataspace.

#### Subsystem

A logical part of a Participant's information system that is
independently identifiable within the Dataspace. A Subsystem must
be registered in the Dataspace Registry and is used as a client
on a Connector to consume and/or provide Datasets.

Subsystem is an X-Road-specific concept and has no direct dataspace
equivalent.

## 7 Identifier Structure

X-Road uses structured identifiers to ensure their global uniqueness.

Identifiers are hierarchical and composable.

### Dataspace Identifier

An identifier that uniquely identifies a Dataspace.

The Dataspace Identifier ensures global uniqueness across federated
dataspaces.

In X-Road context, a Dataspace Identifier replaces the concept previously known as an Instance Identifier.

### Participant Identifier

Uniquely identifies a Participant within a Dataspace.

It consists of:

- Dataspace Identifier
- Participant Class
- Participant Code

In X-Road context, a Participant Identifier replaces the concept previously known as a Member Identifier.

#### Participant Class

A classification assigned by the Dataspace Governance Authority to
distinguish categories of Participants with similar characteristics.

All Participants within the same Participant Class must be uniquely
identifiable by their Participant Code.

In X-Road context, a Participant Class replaces the concept previously known as a Member Class.

#### Participant Code

A unique code identifying a Participant within its Participant Class.

The Participant Code remains stable throughout the lifetime of the
Participant.

In X-Road context, a Participant Code replaces the concept previously known as a Member Code.

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

### Connector Identifier

Uniquely identifies a Connector within a Dataspace.

It consists of:

- Participant Identifier
- Connector Code

In X-Road context, a Connector Identifier replaces the
concept previously known as a Security Server Identifier.

#### Connector Code

A code uniquely identifying a Connector under a specific
Participant.

In X-Road context, a Connector Code replaces the concept previously known as a Security Server Code.

#### Formal Structure

Connector Identifier = Participant Identifier + Connector Code

## 8 Technical Terms

**CA** -- Certification Authority\
**OCSP** -- Online Certificate Status Protocol\
**TSA** -- Timestamping Authority
