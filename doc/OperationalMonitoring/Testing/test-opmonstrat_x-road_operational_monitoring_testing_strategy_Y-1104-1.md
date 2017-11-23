
| ![European Union / European Regional Development Fund / Investing in your future](../img/eu_rdf_75_en.png "Documents that are tagged with EU/SF logos must keep the logos until 1.1.2022, if it has not stated otherwise in the documentation. If new documentation is created  using EU/SF resources the logos must be tagged appropriately so that the deadline for logos could be found.") |
| -------------------------: |

# X-Road: Operational Monitoring Testing Strategy

Version: 0.6  
Doc. ID: TEST-OPMONSTRAT

| Date       | Version     | Description                                                                  | Author             |
|------------|-------------|------------------------------------------------------------------------------|--------------------|
|  | 0.5       | Initial version               |          |
| 23.01.2017 | 0.6       | Added license text, table of contents and version history | Sami Kallio |

## Table of Contents

<!-- toc -->

- [License](#license)
- [1 Introduction](#1-introduction)
  * [1.1 Purpose](#11-purpose)
  * [1.2 Terms and Abbreviations](#12-terms-and-abbreviations)
  * [1.3 References](#13-references)
- [2 Requirements Relevant to Testing](#2-requirements-relevant-to-testing)
- [3 Testability of the System](#3-testability-of-the-system)
- [4. Types of Testing Used](#4-types-of-testing-used)
  * [4.1 Integration Testing](#41-integration-testing)
  * [4.2 Load Requirements and the Scope of Load Tests](#42-load-requirements-and-the-scope-of-load-tests)
    + [4.2.1 Initial Requirements](#421-initial-requirements)
    + [4.2.2 Load Testing in the Context of the Implementation](#422-load-testing-in-the-context-of-the-implementation)
  * [4.3 Unit Testing](#43-unit-testing)
- [5 The Team and the Workflow](#5-the-team-and-the-workflow)
- [6 Tools, Languages and Libraries](#6-tools-languages-and-libraries)
- [7 Management and Source Control of Automated Tests](#7-management-and-source-control-of-automated-tests)
- [8 Setup and Management of the Testing Environment. Continuous Integration](#8-setup-and-management-of-the-testing-environment-continuous-integration)
- [8.1 The Servers Used for Automated Testing](#81-the-servers-used-for-automated-testing)
- [8.2 The X-Road Configuration Used for Automated Testing](#82-the-x-road-configuration-used-for-automated-testing)
  * [8.2.1 X-Road Members](#821-x-road-members)
  * [8.2.2 Security Servers, Clients and Services](#822-security-servers-clients-and-services)
- [8.3 The Results of the Tests](#83-the-results-of-the-tests)

<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/.

## 1 Introduction

### 1.1 Purpose

In this document, we define the testing strategy for the development project of the operational monitoring components of the X-Road system.

A detailed testing plan will be delivered as a separate document in [[TEST-OPMON]](#TEST-OPMON).

### 1.2 Terms and Abbreviations

HTTP -- Hypertext Transfer Protocol  
HTTPS -- Hypertext Transfer Protocol Secure  
JMXMP -- Java Management Extensions Messaging Protocol  
SOAP -- Simple Object Access Protocol  
DSL -- Domain Specific Language  
CA -- Certification Authority  
TSA -- Timestamping Authority  
CI -- Continuous Integration  

### 1.3 References

<a name="HD_1"></a>**HD_1** -- Hanke Lisa 1: X-tee monitooringu tehniline kirjeldus, https://riigihanked.riik.ee/register/hange/173409  
<a name="HD_2"></a>**HD_2** -- Hanke Lisa 2: Mittefunktsionaalsed nõuded, https://riigihanked.riik.ee/register/hange/173409  
<a name="HD_4"></a>**HD_4** -- Hanke Lisa 4: Testimise korraldus, https://riigihanked.riik.ee/register/hange/173409  
<a name="ARC-OPMOND"></a>**ARC-OPMOND** -- Cybernetica AS. X-Road: Operational Monitoring Daemon Architecture. Document ID: [ARC-OPMOND](../Architecture/arc-opmond_x-road_operational_monitoring_daemon_architecture_Y-1096-1.md).  
<a name="PP"></a>**PP** -- Cybernetica AS. Tööde kirjeldus koos ajakavaga, https://riigihanked.riik.ee/register/hange/173409  
<a name="REC-OPMON"></a>**REC-OPMON** -- Cybernetica AS. X-Road Operational Monitoring: Requirements. Document ID: REC-OPMON. Location: project *XTEE6* repository *docs* directory *Service monitoring/Requirements*.  
<a name="UC-OPMON"></a>**UC-OPMON** -- Cybernetica AS. X-Road: Operational Monitoring Daemon Use Case Model. Document ID: [UC-OPMON](../UseCases/uc-opmon_x-road_use_case_model_for_operational_monitoring_daemon_Y-1095-2.md).  
<a name="TEST-OPMON"></a>**TEST-OPMON** -- Cybernetica AS. X-Road: Operational Monitoring Testing Plan. Document ID: [TEST-OPMON](test-opmon_x-road_operational_monitoring_testing_plan_Y-1104-2.md).  
<a name="IG-SS"></a>**IG-SS** -- Cybernetica AS. X-Road: Security Server Installation Guide. Document ID: [IG-SS](../../Manuals/ig-ss_x-road_v6_security_server_installation_guide.md).  

## 2 Requirements Relevant to Testing

The functional requirements of the monitoring system are described in [[REC-OPMON]](#REC-OPMON).  
The architecture of the system is described in [[ARC-OPMOND]](#ARC-OPMOND).  
The items of data that are collected and forwarded by the monitoring system, are defined in [[HD_1]](#HD_1).  
The required paths of data exchange are defined in [[HD_1]](#HD_1).  
The requirements of access control are defined in [[HD_1]](#HD_1).  
The proper default configuration of the system is defined in [[HD_1]](#HD_1).  
The non-functional requirements of the monitoring system are described in [[REC-OPMON]](#REC-OPMON) and [[HD_2]](#HD_2).

## 3 Testability of the System

The operational monitoring system is a machine-to-machine data exchange system, and the events that are relevant to monitoring can be triggered programmatically, once the necessary configuration has been carried out. Thus, the system lends itself very well to automated testing.

Regular X-Road message exchange as well as monitoring data exchange via all the required paths can be simulated for both integration and load testing. Because all the relevant configuration will be implemented as text files, modifying the configuration of the monitoring system can be scripted easily, if necessary.

## 4. Types of Testing Used

The required types of testing are defined in [[HD_4]](#HD_4). In the following sections, all the required types are covered.

### 4.1 Integration Testing

The integration tests will be used for testing the required functionality of the security servers and the operational monitoring daemon working together, while regular X-Road requests as well as operational monitoring and health data requests are handled. The goal is to cover the main functionality with automated tests. Manual tests will be carried out by the development team as needed, as well as during acceptance testing if necessary. The descriptions of manual tests will be provided in [[TEST-OPMON]](#TEST-OPMON).

### 4.2 Load Requirements and the Scope of Load Tests

#### 4.2.1 Initial Requirements

A general test plan and the requirements for hardware for load testing was specified by RIA by e-mail (message ID `CC0A4AA94EE060438CD300941B8EF1EED37D61@exc2a.ria.ee`), with the following contents:

* As a result of load testing, the throughput of the system is noted. No comparable benchmarks are available. The format of the tests will be suggested by Cybernetica AS.
* The security servers of the service provider and the service consumer are installed on separate hosts that conform to the minimum requirements defined in [[IG-SS]](#IG-SS).
* Virtual machines can be used during testing -- both by the developers and at RIA's environment.
* The operational monitoring application is installed at localhost at both security servers and TLS in not used.

Load testing should be carried out using the following plan:
* At first, X-Road requests are made by a single user without the operational monitoring software being used. The average round-trip time is calculated.  
* The number of parallel requests by different users is increased until the average round-trip time becomes 5 times the initial average value. This number of parallel requests is noted.  
* The operational monitoring software is installed and the test with a single user is repeated.  
* The test is repeated with X parallel users.  
* A load testing report is written, in which the following is stated:
  * the additional latency in percents that the operational monitoring software adds  
  * the additional latency in percents that the operational monitoring software adds when X parallel users make requests  

Three types of requests should be used with the load testing plan:
* a request with an empty body  
* a request with a 1MB body  
* a request with an empty body and a 1MB attachment  

#### 4.2.2 Load Testing in the Context of the Implementation

Due to the asynchronous nature of the operational monitoring system, the overhead of request exchange is minimal, when operational data is stored. Operational data about each request is written to an in-memory operational data buffer at the security server and forwarded to the operational monitoring daemon in an asynchronous manner. In such a situation, the host running the security server will mostly experience additional load due to the forwarding of the records, especially during high loads or if operational data cannot be forwarded for some time.

Under a heavy load and if the operational monitoring database component is unavailable, the monitoring buffer may use a considerable amount of memory or drop the eldest operational data records before having forwarded these to the operational monitoring database component.

Thus, the focus of load testing is to find the balance between memory usage and possible data loss. Details of load testing are provided in [[TEST-OPMON]](#TEST-OPMON).

### 4.3 Unit Testing

Generally, unit tests are used for testing the behaviour of single units of code (functions, methods) with clearly defined inputs and outputs.

The required coverage and scope of unit tests have not been defined for this project. Thus, unit tests will be written for units of code that implement protocols, conversion between data formats etc, including tests against invalid or unexpected input. Such units of code will be isolated for testability.

For larger components that are used for control logic, system-wide data exchange as well as configuration, unit tests are not justified and will not be written. The behaviour and error handling of such components will be covered by automated or manual integration tests.

Unit tests will not be written for third-party protocol implementations (such as JMXMP).

## 5 The Team and the Workflow

The schedule of the project and the roles of the team are described in [[PP]](#project_plan).

The testing activities and the programming of tests are carried out by testers and developers. The architecture and the tools of testing are reviewed by the architect of the project.

Unit tests will be implemented by the developers in parallel with the functionality of the system. The scope of unit tests has been described ([above](#4-3-unit-testing)). The build configuration of the project will set up to automatically run the unit tests at each build.

Integration and load testing will be carried out according to the following plan:
- *Elaboration phase:* the tools for testing at the required levels will be chosen and an initial testing plan will be written.
- *Construction phase:* the initial version of load tests will be written and the framework of automated integration tests will be constructed.
- *Transition phase:* The load tests and the automated integration tests will be implemented and integrated with the Continuous Integration system and the testing environment. If manual integration test cases are written, these will be formatted as part of [[TEST-OPMON]](#TEST-OPMON).

## 6 Tools, Languages and Libraries

The integration tests will be implemented in Python 3. During the Elaboration Phase, the approach and tools for describing the integration test steps will be selected. Also, the libraries required for networking, parsing of messages etc, will be selected during the Elaboration Phase.

The load tests will be programmed, and simulations will be described, in Scala and the Gatling DSL, with orchestration scripts programmed in Python 3.

The unit tests will be programmed in Java 8, using the JUnit library.

## 7 Management and Source Control of Automated Tests

All the source code and the necessary data and configuration files (or samples where necessary) of the integration, load and unit tests will be committed to the main Git development repository of the project. The tests and test data, once these are implemented, will be included in the pull request made at the end of each relevant iteration of the project.

## 8 Setup and Management of the Testing Environment. Continuous Integration

The testing environment and continuous integration setup during development will closely mimic the required target testing environment (as described in [[HD_4]](#HD_4)) with some additions. The relevant configuration files and documentation will be committed to the main Git development repository for simple migration to the target testing system.

## 8.1 The Servers Used for Automated Testing
The main automated testing environment consists of the following virtual machines, with the name of the corresponding machine at RIA given in parentheses:
- Central server (*xtee7.ci.kit*)
- Management security server with a local operational monitoring daemon (*xtee8.ci.kit*)
- Security server with a local operational monitoring daemon (*xtee9.ci.kit*)
- Operational monitoring daemon (*xtee11.ci.kit*)
- Security server (*xtee10.ci.kit*) using the external monitoring daemon above
- A mock SOAP server for providing X-Road services (*xtee2.ci.kit*)

The CA/TSA server of RIA (*xtee1.ci.kit*) will be used for trust services, to enable simple migration of the additions to the system's configuration back to RIA-s environment. The configuration of local name resolution will enable the host names used in RIA's testing system to be used in the automated testing environment of Cybernetica.

A SoapUI-based mock server will be used for providing the X-Road services necessary for testing the system. The configuration files of the mock services can be found in the source repository of the project at `src/systemtest/op-monitoring/mock`.

## 8.2 The X-Road Configuration Used for Automated Testing
The X-Road configuration of the testing system at RIA (the instance XTEE-CI-XM) that was present at the beginning of the project, is used as the basis for the configuration of the system under test. The final required configuration of the system consists of the following items.

### 8.2.1 X-Road Members

The following X-Road members and member classes are required in the configuration.

| Member Code | Member Class | Member Name   |
|-------------|--------------|---------------|
| 00000000    | GOV          | X-Road Center |
| 00000001    | GOV          | Test member 1 |
| 00000002    | COM          | Test member 2 |

The management service provider must be `SUBSYSTEM:XTEE-CI-XM/GOV/00000000/Center`.

In the central server, the central monitoring client must be configured with the following contents of `monitoring-params.xml`:

```xml
<tns:conf xsi:schemaLocation="http://x-road.eu/xsd/xroad.xsd">
  <monitoringClient>
    <monitoringClientId id:objectType="SUBSYSTEM">
      <id:xRoadInstance>XTEE-CI-XM</id:xRoadInstance>
      <id:memberClass>GOV</id:memberClass>
      <id:memberCode>00000001</id:memberCode>
      <id:subsystemCode>Central monitoring client</id:subsystemCode>
    </monitoringClientId>
  </monitoringClient>
</tns:conf>
```

### 8.2.2 Security Servers, Clients and Services

The following security servers are required in the configuration.

| Server Code | Owner Code | Owner Class | Hostname      |
|-------------|------------|-------------|---------------|
| 00000000_1  | 00000000   | GOV         | xtee8.ci.kit  |
| 00000001_1  | 00000001   | GOV         | xtee9.ci.kit  |
| 00000002_1  | 00000002   | COM         | xtee10.ci.kit |

The following subsystems and service access rights are required in the configuration, grouped by security servers.

**xtee8.ci.kit**

| Subsystem ID                              | Services          | Access Rights |
|-------------------------------------------|-------------------|---------------|
| SUBSYSTEM:XTEE-CI-XM:COM:00000002:System2 | xroadGetRandom.v1 | SUBSYSTEM:XTEE-CI-XM:GOV:00000001:System1 |
|                                           | bodyMassIndex.v1  | -             |
| SUBSYSTEM:XTEE-CI-XM:GOV:00000000:Center  | bodyMassIndex.v1  | SUBSYSTEM:XTEE-CI-XM:GOV:00000001:System1 |
|                                           | xroadGetRandom.v1 | SUBSYSTEM:XTEE-CI-XM:GOV:00000001:System1 |
|                                           | clientDeletion<br>clientReg<br>authCertDeletion | GLOBALGROUP:XTEE-CI-XM:security-server-owners |

**xtee9.ci.kit**

| Subsystem ID                              | Services          | Access Rights |
|-------------------------------------------|-------------------|---------------|  
| SUBSYSTEM:XTEE-CI-XM:GOV:00000001:System1 | exampleService.v1 | -             |
|                                           | exampleServiceMtom.v1 | SUBSYSTEM:XTEE-CI-XM:GOV:00000001:System1 |
|                                           | exampleServiceSwaRef.v1 | SUBSYSTEM:XTEE-CI-XM:GOV:00000001:System1 |
| SUBSYSTEM:XTEE-CI-XM:GOV:00000001:Central monitoring client | - | -           |

**xtee10.ci.kit**

| Subsystem ID                              | Services          | Access Rights |
|-------------------------------------------|-------------------|---------------|  
| SUBSYSTEM:XTEE-CI-XM:COM:00000002:System2 | exampleService.v1 | SUBSYSTEM:XTEE-CI-XM:GOV:00000001:System1 |
|                                           | exampleServiceMtom.v1 |	SUBSYSTEM:XTEE-CI-XM:GOV:00000001:System1 |
|                                           | exampleServiceSwaRef.v1 | SUBSYSTEM:XTEE-CI-XM:GOV:00000001:System1 |
|                                           | bodyMassIndex.v1  | -             |
|                                           | xroadGetRandom.v1 | SUBSYSTEM:XTEE-CI-XM:GOV:00000001:System1 |

## 8.3 The Results of the Tests
The results of automatic integration tests and load tests will be browsable in the Continuous Integration system (Jenkins) at RIA after each run, once these have been implemented and the CI system has been configured.

The results of manual integration testing will be observable by the participants and can be written to a log file.

The results of unit tests will be browsable in the Continuous Integration system (Jenkins) at RIA after each build of the source tree, once the changes have been merged to the repository at RIA and the CI system has been configured.
