# X-Road: Operational Monitoring Testing Strategy <!-- omit in toc -->

Version: 0.9  
Doc. ID: TEST-OPMONSTRAT

| Date       | Version | Description                                                         | Author           |
|------------|---------|---------------------------------------------------------------------|------------------|
|            | 0.5     | Initial version                                                     |                  |
| 23.01.2017 | 0.6     | Added license text, table of contents and version history           | Sami Kallio      |
| 05.03.2018 | 0.7     | Added terms and abbreviations reference and moved terms to term doc | Tatu Repo        | 
| 05.02.2020 | 0.8     | Update information about the test suites.                           | Ilkka Seppälä    | 
| 01.06.2023 | 0.9     | Update references                                                   | Petteri Kivimäki |

## Table of Contents <!-- omit in toc -->

<!-- toc -->

- [License](#license)
- [1 Introduction](#1-introduction)
  - [1.1 Purpose](#11-purpose)
  - [1.2 Terms and Abbreviations](#12-terms-and-abbreviations)
  - [1.3 References](#13-references)
- [2 Requirements Relevant to Testing](#2-requirements-relevant-to-testing)
- [3 Testability of the System](#3-testability-of-the-system)
- [4. Types of Testing Used](#4-types-of-testing-used)
  - [4.1 Integration Testing](#41-integration-testing)
  - [4.2 Unit Testing](#42-unit-testing)
- [5 Tools, Languages and Libraries](#5-tools-languages-and-libraries)
- [6 Management and Source Control of Automated Tests](#6-management-and-source-control-of-automated-tests)
- [7 Setup and Management of the Testing Environment. Continuous Integration](#7-setup-and-management-of-the-testing-environment-continuous-integration)
- [7.1 The Servers Used for Automated Testing](#71-the-servers-used-for-automated-testing)

<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/.

## 1 Introduction

### 1.1 Purpose

In this document, we define the testing strategy for the development project of the operational monitoring components of the X-Road system.

A detailed testing plan will be delivered as a separate document in \[[TEST-OPMON](#TEST-OPMON)\].

### 1.2 Terms and Abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\].

### 1.3 References

<a name="HD_1"></a>**HD_1** -- Hanke Lisa 1: X-tee monitooringu tehniline kirjeldus, https://riigihanked.riik.ee/register/hange/173409  
<a name="HD_2"></a>**HD_2** -- Hanke Lisa 2: Mittefunktsionaalsed nõuded, https://riigihanked.riik.ee/register/hange/173409  
<a name="HD_4"></a>**HD_4** -- Hanke Lisa 4: Testimise korraldus, https://riigihanked.riik.ee/register/hange/173409  
<a name="ARC-OPMOND"></a>**ARC-OPMOND** -- X-Road: Operational Monitoring Daemon Architecture. Document ID: [ARC-OPMOND](../Architecture/arc-opmond_x-road_operational_monitoring_daemon_architecture_Y-1096-1.md).  
<a name="PP"></a>**PP** -- Tööde kirjeldus koos ajakavaga, https://riigihanked.riik.ee/register/hange/173409  
<a name="REC-OPMON"></a>**REC-OPMON** -- X-Road Operational Monitoring: Requirements. Document ID: REC-OPMON. Location: project *XTEE6* repository *docs* directory *Service monitoring/Requirements*.  
<a name="UC-OPMON"></a>**UC-OPMON** -- X-Road: Operational Monitoring Daemon Use Case Model. Document ID: [UC-OPMON](../UseCases/uc-opmon_x-road_use_case_model_for_operational_monitoring_daemon_Y-1095-2.md).  
<a name="TEST-OPMON"></a>**TEST-OPMON** -- X-Road: Operational Monitoring Testing Plan. Document ID: [TEST-OPMON](test-opmon_x-road_operational_monitoring_testing_plan_Y-1104-2.md).  
<a name="IG-SS"></a>**IG-SS** -- X-Road: Security Server Installation Guide. Document ID: [IG-SS](../../Manuals/ig-ss_x-road_v6_security_server_installation_guide.md).  
<a name="Ref_TERMS" class="anchor"></a>**TA-TERMS** -- X-Road Terms and Abbreviations. Document ID: [TA-TERMS](../../terms_x-road_docs.md).

## 2 Requirements Relevant to Testing

The functional requirements of the monitoring system are described in \[[REC-OPMON](#REC-OPMON)\].  
The architecture of the system is described in \[[ARC-OPMOND](#ARC-OPMOND)\].  
The items of data that are collected and forwarded by the monitoring system, are defined in \[[HD_1](#HD_1)\].  
The required paths of data exchange are defined in \[[HD_1](#HD_1)\].  
The requirements of access control are defined in \[[HD_1](#HD_1)\].  
The proper default configuration of the system is defined in \[[HD_1](#HD_1)\].  
The non-functional requirements of the monitoring system are described in \[[REC-OPMON](#REC-OPMON)\] and \[[HD_2](#HD_2)\].

## 3 Testability of the System

The operational monitoring system is a machine-to-machine data exchange system, and the events that are relevant to monitoring can be triggered programmatically, once the necessary configuration has been carried out. Thus, the system lends itself very well to automated testing.

Regular X-Road message exchange as well as monitoring data exchange via all the required paths can be simulated for both integration and load testing. Because all the relevant configuration will be implemented as text files, modifying the configuration of the monitoring system can be scripted easily, if necessary.

## 4. Types of Testing Used

The required types of testing are defined in \[[HD_4](#HD_4)\]. In the following sections, all the required types are covered.

### 4.1 Integration Testing

The integration tests will be used for testing the required functionality of the security servers and the operational monitoring daemon working together, while regular X-Road requests as well as operational monitoring and health data requests are handled. The goal is to cover the main functionality with automated tests. Manual tests will be carried out by the development team as needed, as well as during acceptance testing if necessary. The descriptions of manual tests will be provided in \[[TEST-OPMON](#TEST-OPMON)\].

### 4.2 Unit Testing

Generally, unit tests are used for testing the behaviour of single units of code (functions, methods) with clearly defined inputs and outputs.

The required coverage and scope of unit tests have not been defined for this project. Thus, unit tests will be written for units of code that implement protocols, conversion between data formats etc, including tests against invalid or unexpected input. Such units of code will be isolated for testability.

For larger components that are used for control logic, system-wide data exchange as well as configuration, unit tests are not justified and will not be written. The behaviour and error handling of such components will be covered by automated or manual integration tests.

Unit tests will not be written for third-party protocol implementations (such as JMXMP).

## 5 Tools, Languages and Libraries

The integration tests will be implemented in Python 3. During the Elaboration Phase, the approach and tools for describing the integration test steps will be selected. Also, the libraries required for networking, parsing of messages etc, will be selected during the Elaboration Phase.

The unit tests will be programmed in Java 8, using the JUnit library.

## 6 Management and Source Control of Automated Tests

All the source code and the necessary data and configuration files (or samples where necessary) of the integration, load and unit tests will be committed to the main Git development repository of the project. The tests and test data, once these are implemented, will be included in the pull request made at the end of each relevant iteration of the project.

## 7 Setup and Management of the Testing Environment. Continuous Integration

The testing environment and continuous integration setup during development will closely mimic the required target testing environment (as described in \[[HD_4](#HD_4)\]) with some additions. The relevant configuration files and documentation will be committed to the main Git development repository for simple migration to the target testing system.

## 7.1 The Servers Used for Automated Testing

The automated testing environment details can be found in document `src/systemtest/op-monitoring/xrd-opmon-tests/README.md`.

A SoapUI-based mock server will be used for providing the X-Road services necessary for testing the system. The mock service along with usage instructions can be found in the source repository of the project at `src/systemtest/op-monitoring/xrd-mock-soapui`.
