# X-Road: Operational Monitoring Testing Plan

Version: 0.3

Document ID: TEST-OPMON

## 1 Introduction

### 1.1 Purpose

The purpose of this document is to describe the levels and procedures of testing used during the development of the operational monitoring components of the X-Road system. While the testing strategy of the development project ([[TEST-OPMONSTRAT]](#TEST-OPMONSTRAT)) describes the overall approach to testing, this plan offers a more technical and detailed view of the testing levels, the tools used and the functionality covered by testing.

This is a living document and will be constantly updated as the project evolves.

### 1.2 Terms and Abbreviations

HTTP -- Hypertext Transfer Protocol  
HTTPS -- Hypertext Transfer Protocol Secure  
JMXMP -- Java Management Extensions Messaging Protocol  
JSON -- JavaScript Object Notation  
SOAP -- Simple Object Access Protocol  
DSL -- Domain Specific Language  

### 1.3 References

<a name="HD_1">HD_1</a> -- Hanke lisa 1: X-tee monitooringu tehniline kirjeldus  
<a name="ARC-OPMON">ARC-OPMON</a> -- Cybernetica AS. X-Road: Operational Monitoring Daemon Architecture  
<a name="TEST-OPMONSTRAT">TEST-OPMONSTRAT</a> -- Cybernetica AS. X-Road: Operational Monitoring Testing Strategy  
<a name="UC-OPMON">UC-OPMON</a> -- Cybernetica AS. X-Road: Operational Monitoring Daemon Use Case Model  

## 2 Components of the Operational Monitoring System in the Context of Testing

The operational monitoring system involves the X-Road security server and the operational monitoring daemon. In addition, global configuration is obtained from the X-Road central server.

According to the architecture of the operational monitoring daemon ([[ARC-OPMON]](#ARC-OPMON)), the daemon is divided into the following components:
* Operational Monitoring Database
* Operational Monitoring Service

In the security server, the operational monitoring buffer is involved in forwarding operational data to the monitoring daemon.

In this project, testing will mainly focus on the behaviour of the operational monitoring daemon. The behavior of the operational monitoring buffer of the security server will be verified indirectly with integration and load testing, which involves X-Road message exchange, and will not be covered separately in this testing plan.

### 2.1 Testing the Operational Monitoring Database

The operational monitoring database component collects operational data of the X-Road security server(s) via the Store Operational Data interface. The requirements for data stored in the database are defined in [[HD_1]](#HD_1).

The database is tested at the following levels:
* Direct SQL is used by the developers and testers for ad-hoc queries during development and testing. The source code of the operational monitoring daemon does not make any raw SQL queries, however.
* Build-time unit tests written in Java 8 are used for testing the behaviour of the Hibernate library in Java code using the HSQLDB in-memory database, for checking the access to, and operations with the database records as Java objects.
* Also, build-time unit tests are used for testing transactional operations on the database in Java code.
* Conversion of operational monitoring data from the JSON representation to objects encapsulating database records, is covered by build-time unit tests in Java 8.
* The behaviour of the database component is tested during automated integration testing and load testing as part of the message exchange carried out.
* Creation and upgrades of the database schema using the combination of Hibernate and the PostgreSQL database are tested manually during the installation and upgrades of the testing environment. The source code of the operational monitoring daemon does not (and cannot) alter the database schema at runtime.

### 2.2 Testing the Operational Monitoring Service

The operational monitoring service receives and processes operational data requests via the Operational Monitoring Query interface. This service is used by the security server. This component is tested at the following levels:

* Build-time unit tests are used for testing the conversion of query criteria in SOAP requests to the corresponding criteria of database queries.
* At system integration level, the operational monitoring service is tested directly as a central part of the automated integration tests.

## 3 The Protocols and Interfaces used in the Operational Monitoring System in the Context of Testing

The operational monitoring system supports the following protocols and interfaces:
* Store Operational Data (JSON)
* X-Road Message Protocol (SOAP, both regular X-Road messages and operational monitoring query requests)
* JMXMP interface for providing third-party monitoring systems (Zabbix) installed at security servers with health check data.

The JSON and SOAP-based interfaces are tested as part of the build-time unit tests as well as during automated integration testing. Within the set of unit tests, the focus is on low-level error handling, as opposed to the integration tests which focus on expected behavior in both successful and unsuccessful situations.

The JMXMP interface is tested automatically using the Zabbix API, as part of the integration test suite. Tests using the Zabbix API will be implemented during the Transition Phase of the project.

## 4 The Use Cases of the Operational Monitoring Daemon in the Context of Testing

The use cases defined in [[UC-OPMON]](#UC-OPMON) are directly or indirectly covered by the automated integration tests. Because the full list of use cases is a work in progress, detailed information about the coverage of these will be added during the Transition Phase of the project.

## 5 Automated Integration Testing in Detail

Automated integration tests are carried out on a pre-configured testing system with the required X-Road components installed and configured, as described in [[TEST-OPMONSTRAT]](#TEST-OPMONSTRAT). It is assumed that during integration testing, no manual testing is carried out on the same systems. Otherwise, arbitrary X-Road message exchange would interfere with the tests and cause false negative results.

The following test cases have been automated at integration testing level by the end of the Construction Phase of the project:
* **test_attachments:** verifying that information about attachments in X-Road requests and responses is stored and returned as required
* **test_get_metadata:** verifying that information about HTTP GET metadata requests (getWsdl, listClients, verificationconf) is stored (or not) and returned (or not) as required
* **test_health_data:** verifying that correct health data is returned for each service for which X-Road requests are handled
* **test_limited_operational_data_response:** verifying that returning operational data in batches (depending on the configuration) works as required
* **test_metaservices:** verifying that information about metaservice requests (listMethods, getSecurityServerMetrics) is stored and returned as required
* **test_outputspec:** verifying that the set of fields in operational data responses can be provided in operational data requests and that the responses are consistent with this set of fields
* **test_service_cluster:** verifying that information about requests to services that are in a cluster, is stored and returned as required
* **test_simple_store_and_query:** verifying that operational data is stored about regular X-Road requests as well as operational data requests themselves
* **test_soap_fault:** verifying that information about requests resulting in SOAP faults at various points in the request exchange chain, is stored and returned as required
* **test_time_interval:** verifying that operational data can be queried about specific time intervals as required, and that errors are handled as required

Several automated test cases will be added during the Transition Phase of the project.

The test scripts and their input files can be found in the source repository of the project at /xtee6/systemtest/op-monitoring/ .

## 6 Load Testing

Due to the asynchronous nature of the operational monitoring system, the overhead of request exchange is minimal as operational data about each request is written to an in-memory operational data buffer at the security server. However, under a heavy load, and if the operational monitoring database component is unavailable, the operational monitoring buffer of the security server may use a considerable amount of memory or drop the eldest operational data records before having forwarded these to the operational monitoring database component.

### 6.1 Limitations of the System

Depending on the hardware of the system, several variables exist that limit the actual ability of the security servers to exchange requests, particularly:
* the amount of memory in the system
* the amount of memory available to the internal Jetty server instances of the security servers
* ... (TBD)

If sufficient memory is available, several limitations define the number of possible parallel requests made to the security servers:
* the thread pool size of the internal Jetty server instances of the security servers
* the Anti-DOS settings of the security servers
* the overhead of encrypted communication if the operational monitoring daemon has been installed to a server other than the security server
* ... (TBD)

### 6.2 Setup of the Simulations

Load simulations will be run automatically, but manual configuration of the system is necessary before the tests. Also, the reports of the simulation should be analyzed for information about the behaviour of the system under the simulated load.

The setup of the simulations is configurable using the Gatling DSL.

The setup of a sample load simulation is as follows.

* The amount of memory allocated to the Jetty instance under test is documented, and the theoretically possible number of parallel requests is calculated.
* The size of the connection pool of the internal Jetty web server of the client's and producer's security servers is set to a value that will not be reached during the load simulation, according to the relevant calculations.
* The maximum size of the operational monitoring buffer is set to a value that will be reached during the specific load simulation (based on observations or calculations), without Jetty running out of memory or the available threads in the connection pool while the load is being achieved.
* The simulation setup is described in Gatling DSL, so that parallel requests of various types will be made (see below), within the limits described above.
* The simulation is started and the system logs are observed to see that the operational monitoring buffer does not fill up under normal circumstances.
* The operational monitoring daemon database component (the daemon process) is stopped. The system logs are observed to see that the buffer fills up and starts dropping the eldest records.
* The database component is started again. System logs are observed to see that the size of the operational monitoring buffer decreases as the accumulated records which were not dropped, are forwarded to the operational monitoring daemon.

The following test data can be used in load simulations in the desired combination:
* A request with an empty body
* A request with a 1MB body
* An empty request with a 1MB attachment (TODO).

The details of load testing will be defined by the end of the Transition Phase of the project.

## 7 Testing the JMXMP Interface

The JMXMP interface will be tested automatically using the the Zabbix API and Python3, depending on pre-configured Zabbix items in one or more Zabbix instances available in the testing environment. Also, manual tests and observations will be made using the jconsole application and the Zabbix user interface. The details of testing the JMXMP interface will be defined by the end of the Transition Phase of the project.
