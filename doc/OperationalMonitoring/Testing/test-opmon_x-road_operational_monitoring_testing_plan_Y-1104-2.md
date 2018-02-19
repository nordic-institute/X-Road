
| ![European Union / European Regional Development Fund / Investing in your future](../img/eu_rdf_75_en.png "Documents that are tagged with EU/SF logos must keep the logos until 1.1.2022, if it has not stated otherwise in the documentation. If new documentation is created  using EU/SF resources the logos must be tagged appropriately so that the deadline for logos could be found.") |
| -------------------------: |

# X-Road: Operational Monitoring Testing Plan

Version: 0.6  
Doc ID: TEST-OPMON

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
- [2 Components of the Operational Monitoring System in the Context of Testing](#2-components-of-the-operational-monitoring-system-in-the-context-of-testing)
  * [2.1 Testing the Operational Monitoring Database](#21-testing-the-operational-monitoring-database)
  * [2.2 Testing the Operational Monitoring Service](#22-testing-the-operational-monitoring-service)
- [3 The Protocols and Interfaces used in the Operational Monitoring System in the Context of Testing](#3-the-protocols-and-interfaces-used-in-the-operational-monitoring-system-in-the-context-of-testing)
- [4 The Use Cases of the Operational Monitoring Daemon in the Context of Testing](#4-the-use-cases-of-the-operational-monitoring-daemon-in-the-context-of-testing)
- [5 Automated Integration Testing in Detail](#5-automated-integration-testing-in-detail)
- [6 Load Testing](#6-load-testing)
  * [6.1 Limitations of the System](#61-limitations-of-the-system)
  * [6.2 Setup of the Simulations](#62-setup-of-the-simulations)
  * [6.2.1 A Sample Load Simulation for Observing the Dropping of Records](#621-a-sample-load-simulation-for-observing-the-dropping-of-records)
- [6.3 The Effect of the Operational Monitoring System on Request Throughput](#63-the-effect-of-the-operational-monitoring-system-on-request-throughput)
  * [6.3.1 A Sample Set of Simulations and Results](#631-a-sample-set-of-simulations-and-results)
- [7 Testing the JMXMP Interface](#7-testing-the-jmxmp-interface)
  * [7.1 Testing the JMXMP Interface Using jconsole](#71-testing-the-jmxmp-interface-using-jconsole)
- [8 Manual Integration Testing in Detail](#8-manual-integration-testing-in-detail)
  * [8.1 Test Helpers](#81-test-helpers)
  * [8.2 Send a Request to a Non-operational Service Cluster](#82-send-a-request-to-a-non-operational-service-cluster)
  * [8.3 Run Operational Monitoring Data Cleanup](#83-run-operational-monitoring-data-cleanup)
  * [8.4 Receive Operational Data in Multiple Batches](#84-receive-operational-data-in-multiple-batches)
  * [8.5 Configure an External Monitoring Daemon](#85-configure-an-external-monitoring-daemon)
  * [8.6 Use Invalid Certificates for TLS Connection](#86-use-invalid-certificates-for-tls-connection)

<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/.


## 1 Introduction

### 1.1 Purpose

The purpose of this document is to describe the levels and procedures of testing used during the development of the operational monitoring components of the X-Road system. While the testing strategy of the development project ([[TEST-OPMONSTRAT]](#TEST-OPMONSTRAT)) describes the overall approach to testing, this plan offers a more technical and detailed view of the testing levels, the tools used and the functionality covered by testing.

### 1.2 Terms and Abbreviations

HTTP -- Hypertext Transfer Protocol  
HTTPS -- Hypertext Transfer Protocol Secure  
JMXMP -- Java Management Extensions Messaging Protocol  
JSON -- JavaScript Object Notation  
SOAP -- Simple Object Access Protocol  
DSL -- Domain Specific Language  
SDK -- Software Development Kit  
SSH -- Secure Shell

### 1.3 References

<a name="HD_1"></a>**HD_1** -- Hanke Lisa 1: X-tee monitooringu tehniline kirjeldus, https://riigihanked.riik.ee/register/hange/173409  
<a name="ARC-OPMOND"></a>**ARC-OPMOND** -- Cybernetica AS. X-Road: Operational Monitoring Daemon Architecture. Document ID: [ARC-OPMOND](../Architecture/arc-opmond_x-road_operational_monitoring_daemon_architecture_Y-1096-1.md).  
<a name="TEST-OPMONSTRAT"></a>**TEST-OPMONSTRAT** -- Cybernetica AS. X-Road: Operational Monitoring Testing Strategy. Document ID: [TEST-OPMONSTRAT](test-opmonstrat_x-road_operational_monitoring_testing_strategy_Y-1104-1.md)  
<a name="UC-OPMON"></a>**UC-OPMON** -- Cybernetica AS. X-Road: Operational Monitoring Daemon Use Case Model. Document ID: [UC-OPMON](../UseCases/uc-opmon_x-road_use_case_model_for_operational_monitoring_daemon_Y-1095-2.md).  
<a name="UG-SS"></a>**UG-SS** -- Cybernetica AS. X-Road: Security Server User Guide. Document ID: [UG-SS](../../Manuals/ug-ss_x-road_6_security_server_user_guide_2.14_Y-883-32.docx).  
<a name="PR-OPMONJMX"></a>**PR-OPMONJMX** -- Cybernetica AS. Operational Monitoring Daemon JMXMP Interface. Document ID: [PR-OPMONJMX](../Protocols/pr-opmonjmx_x-road_operational_monitoring_jmx_protocol_Y-1096-3.md).

## 2 Components of the Operational Monitoring System in the Context of Testing

The operational monitoring system involves the X-Road security server and the operational monitoring daemon. In addition, global configuration is obtained from the X-Road central server.

According to the architecture of the operational monitoring daemon ([[ARC-OPMOND]](#ARC-OPMOND)), the daemon is divided into the following components:
* operational monitoring database
* operational monitoring service

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

The operational monitoring service receives and processes operational and health data requests via the Operational Monitoring Query interface. This service is used by the security server. This service is tested at the following levels:

* Build-time unit tests are used for testing the conversion of query criteria in SOAP requests to the corresponding criteria of database queries.
* At system integration level, the operational monitoring service is tested directly as a central part of the automated integration tests.

## 3 The Protocols and Interfaces used in the Operational Monitoring System in the Context of Testing

The operational monitoring system supports the following protocols and interfaces:
* Store Operational Data (JSON)
* X-Road Message Protocol (SOAP, both regular X-Road messages and operational monitoring query requests)
* JMXMP interface for providing third-party monitoring systems (e.g. Zabbix) installed at security servers with health check data.

The JSON and SOAP-based interfaces are tested as part of the build-time unit tests as well as during automated integration testing. Within the set of unit tests, the focus is on low-level error handling, as opposed to the integration tests which focus on expected behavior in both successful and unsuccessful situations.

The JMXMP interface is tested manually.

## 4 The Use Cases of the Operational Monitoring Daemon in the Context of Testing

The use cases defined in [[UC-OPMON]](#UC-OPMON) are directly or indirectly covered by the automated integration tests. In particular:
* UC OPMON_01: Storing operational data (and updating the in-memory health data) is carried out in all the automated test cases, unless the test checks that operational data is *not* written.
* UC OPMON_02: Querying operational data is carried out in all the automated test cases except for [[test_health_data]](#test_health_data) which only queries health data.
* UC OPMON_03: Health data of the security server is queried in the automated test case [[test_health_data]](#test_health_data).

## 5 Automated Integration Testing in Detail

Automated integration tests are carried out on a pre-configured testing system with the required X-Road components installed and configured, as described in [[TEST-OPMONSTRAT]](#TEST-OPMONSTRAT). It is assumed that during integration testing, no manual testing is carried out on the same systems. Otherwise, arbitrary X-Road message exchange would interfere with the tests and cause false negative results.

The testcases are listed in alphabetical order. They can be run in an arbitrary order and do not depend on the results of each other.

**NOTE** The test cases require specific values for some configuration parameters of the security servers or the operational monitoring daemon. If such values are not present, the services are reconfigured and restarted automatically. After each test case, the initial configuration is restored, and the services are restarted.

The test scripts and their input files can be found in the source repository of the project at `src/systemtest/op-monitoring/integration`. Please refer to `src/systemtest/op-monitoring/integration/README` for instructions on running the tests.

The following test cases have been automated at integration testing level:
1. `test_attachments`, verifying that information about attachments in X-Road requests and responses is stored and returned as required.
2. `test_client_filter`, verifying the access rights to monitoring data of various types of clients (the owner of the security server, the central monitoring client and regular clients).
3. `test_get_metadata`, verifying that information about HTTP GET metadata requests (getWsdl, listClients, verificationconf) is stored (or not) and returned (or not) as required.
4. <a name="test_health_data"></a>`test_health_data`, verifying that correct health data is returned for each service for which X-Road requests are handled.
5. <a name="test_limited_operational_data_response"></a>`test_limited_operational_data_response`, verifying that returning operational data in batches (depending on the configuration) works as required.
6. `test_metaservices`, verifying that information about metaservice requests (listMethods, getSecurityServerMetrics) is stored and returned as required.
7. `test_outputspec`, verifying that the set of fields present in operational data responses can be provided in operational data requests and that the responses are consistent with this set of fields.
8. <a name="test_service_cluster"></a>`test_service_cluster`, verifying that information about requests to services that are in a cluster, is stored and returned as required.
9. `test_simple_store_and_query`, verifying that operational data is stored about regular X-Road requests as well as operational data requests themselves.
10. `test_soap_fault`, verifying that information about requests resulting in SOAP faults at various points in the request exchange chain, is stored and returned as required.
11. `test_zero_buffer_size`, verifying that in case the operational monitoring buffer size has been set to zero, the operational monitoring data of X-Road requests is not stored by the operational monitoring daemon and can't be queried.
12. `test_time_interval`, verifying that operational data can be queried about specific time intervals as required, and that errors are handled as required.

## 6 Load Testing

Due to the asynchronous nature of the operational monitoring system, the overhead of request exchange is minimal as operational data about each request is written to an in-memory operational data buffer at the security server. However, under a heavy load, and if the operational monitoring database component is unavailable, the operational monitoring buffer of the security server may use a considerable amount of memory or drop the eldest operational data records before having forwarded these to the operational monitoring database component.

### 6.1 Limitations of the System

Depending on the hardware of the system, several variables exist that limit the actual ability of the security servers to exchange requests, particularly:
* the amount of memory in the system
* the amount of memory available to the internal Jetty server instances of the security servers

If sufficient memory is available, several limitations define the number of possible parallel requests made to the security servers:
* the thread pool size of the internal Jetty server instances of the security servers
* the Anti-DOS settings of the security servers
* the overhead of encrypted communication if the operational monitoring daemon has been installed to a server other than the security server

During general load testing of the operational monitoring system, it was observed that the recommended minimum amount of memory must be raised for the security servers. 3GB of physical memory must be available to the security servers under test.

### 6.2 Setup of the Simulations

Load simulations can be run automatically, provided the testing environment has been set up as described in [[TEST-OPMONSTRAT]](#TEST-OPMONSTRAT). The reports of the simulation should be analyzed for information about the behaviour of the system under the simulated load.

The load test runner and the related source code can be found in the source repository of the project at `src/systemtest/op-monitoring/load`.

The setup of the simulations is configurable using the Gatling DSL. A sample simulation has been provided in `src/systemtest/op-monitoring/load/SimulationSetup.scala_sample` . This file can be used as documented in `src/systemtest/op-monitoring/load/README`.

The following predefined request types can be used in load simulations in the desired combination:
* A request with an empty body
* A request with a 1MB body
* An empty request with a 1MB attachment.

For information on interpreting the reports of the simulations, please refer to [the documentation on Gatling reports](http://gatling.io/docs/2.2.3/general/reports.html).

### 6.2.1 A Sample Load Simulation for Observing the Dropping of Records
The setup of a sample load simulation is as follows.

* The amount of memory allocated to the Jetty instance under test is documented, and the theoretically possible number of parallel requests is calculated.
* The size of the connection pool of the internal Jetty web server of the client's and producer's security servers is set to a value that will not be reached during the load simulation, according to the relevant calculations.
* The maximum size of the operational monitoring buffer is set to a value that will be reached during the specific load simulation (based on observations or calculations), without Jetty running out of memory or the available threads in the connection pool while the load is being achieved.
* The simulation setup is described in Gatling DSL, so that parallel requests of various types will be made, within the limits described above.
* The simulation is started and the system logs are observed to see that the operational monitoring buffer does not fill up under normal circumstances.
* The operational monitoring daemon database component (the daemon process) is stopped. The system logs are observed to see that the buffer fills up and starts dropping the eldest records.
* The database component is started again. System logs are observed to see that the size of the operational monitoring buffer decreases as the accumulated records which were not dropped, are forwarded to the operational monitoring daemon.

## 6.3 The Effect of the Operational Monitoring System on Request Throughput

In order to measure the effect on the round-trip times of X-Road requests by the operational monitoring system, one has to gather statistics of request exchange in two setups:
1. The operational monitoring buffer has been disabled by specifying *op-monitor-buffer.size=0* in the configuration of the security server under test (and the xroad-proxy service restarted).
2. The operational monitoring buffer has been enabled by removing the parameter *op-monitor-buffer.size* from the configuration of the security server under test (and the xroad-proxy service restarted).

Please refer to [[UG-SS]](#UG-SS) for instructions on configuring and restarting the components of the security server.

Under both setups, the desired load simulation must be be run at least 3 times, assuming there are no other sources of load on the system under test during the simulations. In the output of the simulations, the request statistics will be observed, comparing the results for both setups. Because the initial handshake of the TLS session between security servers affects the round-trip time of the first request in the simulation by the order of a second, the mean response time is not a reliable value. Rather the "response time 50th percentile" value will give a good average estimate of response times.

### 6.3.1 A Sample Set of Simulations and Results

In a sample set of simulations, the following set of requests were used:

```
SimulationScenarios.SimpleRequestScenario.inject(
      constantUsersPerSec(1) during(10 seconds),
      constantUsersPerSec(5) during(10 seconds)
    ),

    SimulationScenarios.RequestWith1MbBodyScenario.inject(
      constantUsersPerSec(1) during(10 seconds),
      constantUsersPerSec(2) during(10 seconds)
    ),

    SimulationScenarios.RequestWithAttachmentScenario.inject(
      constantUsersPerSec(1) during(10 seconds),
      constantUsersPerSec(1) during(10 seconds)
    )
```

The target servers were virtual machines using 2.4GHz Intel processors (2 cores each). Default memory settings were used for the services under test (as defined in `/etc/xroad/services/proxy.conf` and `/etc/xroad/services/opmonitor.conf` in the servers).

The following results are an illustration of how to interpret the output of the available load testing suite, and do not represent an exhaustive load test.

The simulation attempts with the operational monitoring buffer disabled, yielded the following results:

| parameter                          | #1  | #2  | #3  |
|------------------------------------|-----|-----|-----|
| request count                      | 110 | 110 | 110 |
| response time 50th percentile (ms) | 257 | 244 | 243 |
| response time 75th percentile (ms) | 432 | 355 | 367 |

The attempts with the operational monitoring buffer enabled, yielded the following results:

| parameter                          | #1  | #2  | #3  |
|------------------------------------|-----|-----|-----|
| request count                      | 110 | 110 | 110 |
| response time 50th percentile (ms) | 299 | 256 | 240 |
| response time 75th percentile (ms) | 442 | 370 | 380 |

In the environment under test, enabling the operational monitoring buffer added around 10-20 ms to the response time in the 50th and 75th percentiles.

## 7 Testing the JMXMP Interface

The JMXMP interface gets the data it exposes from the same component that is used to serve health data over the Operational Monitoring Query interface. Thus, the internal implementation of the health metrics registry is tested in the automated test case [test_health_data](#test_health_data). The main difference when directly using the JMXMP interface is in the format that the items are presented. The data exposed over JMXMP and their format are described in detail in [[PR-OPMONJMX]](#PR-OPMONJMX).

For quick reference, a couple of examples follow.

The keys of JMX items related to services are similar to this example:
  ```
  metrics:name=requestDuration(XTEE-CI-XM/GOV/00000001//getSecurityServerOperationalData)
  ```
where "//" represents a missing subsystem (the `getSecurityServerOperationalData` service is provided by the owner of the security server).

The keys of general JMX items are similar to this example:
  ```
  metrics:name=monitoringStartupTimestamp
  ```

During the project, the JMXMP interface will  be tested manually, using pre-configured Zabbix items in one or more Zabbix instances available in the testing environment, and using the `jconsole` application for directly observing the JMX metrics as they become available.

The configuration and usage of Zabbix is out of the scope of this document. The `jconsole` application is available in Java SDK-s, the installation of which is not in the scope of this document.

Direct observation of JMX metrics in `jconsole` is described in the following section.

### 7.1 Testing the JMXMP Interface Using jconsole

By default, the JMX interface of the operational monitoring daemon has been configured to listen on localhost, with authentication disabled and TLS enabled. In order to conveniently access this interface from a remote host, either directly or through an SSH tunnel, the following configuration must be used in `/etc/xroad/services/local.conf`, effectively overriding the value of the variable OPMON_PARAMS:

```bash
OPMON_PARAMS=" -javaagent:$METRICS_BUGFIX_AGENT -Xms50m -Xmx256m -XX:MaxMetaspaceSize=70m \
-Dlogback.configurationFile=/etc/xroad/conf.d/op-monitor-logback.xml \
-Djava.rmi.server.hostname=<address> \
-Dcom.sun.management.jmxremote \
-Dcom.sun.management.jmxremote.port=<port> \
-Dcom.sun.management.jmxremote.authenticate=false \
-Dcom.sun.management.jmxremote.ssl=false "
```

where `address` should be set to the desired listening address for access by `jconsole` and `port` should be set to the port number suitable for the system under test.

The health metrics of the operational monitoring daemon will appear on the `MBeans` tab, in the `metrics` subtree.

The items appearing under this subtree can be observed as the automated integration tests are run. Please refer to [[PR-OPMONJMX]](#PR-OPMONJMX) for the exact set of items required for each mediated request. Note that a separate `jconsole` session should be opened for the producer and the consumer security servers, to gain access to all the metrics made available.

**NOTE** Because the health metrics related to mediated services are reset upon each restart of the operational monitoring daemon, the necessary configuration of the system should be carried out before running each automated test case. Please refer to `src/systemtest/op-monitoring/integration/run_tests.py` (`LOCAL_INI_PARAMETERS` and each test case in `OperationalMonitoringIntegrationTest`) for information about the necessary configuration.

## 8 Manual Integration Testing in Detail

This chapter contains the descriptions of manual test cases of the operational monitoring system. Manual test cases cover only the functionality that is not covered by automated integration tests. Manual integration tests are carried out on a pre-configured testing system with the required X-Road components installed and configured, as described in [[TEST-OPMONSTRAT]](#TEST-OPMONSTRAT).

### 8.1 Test Helpers
The test steps described here are to be executed when refered to in test cases.
* <a name="log_in_db"></a>**Logging in to operational monitoring database as user opmonitor**: In the server running the operational monitoring daemon, enter the command
   ```bash
   psql -h 127.0.0.1 -U opmonitor op-monitor
   ```

   The password for user opmonitor can be found in `/etc/xroad/db.properties`.

### 8.2 Send a Request to a Non-operational Service Cluster

Test case for verifying that the value of the operational monitoring data field 'service_security_server_address' in the operational monitoring database is null and that the request is marked as unsuccessful, in case an X-Road request is made to a non-operational service cluster. This test case is a supplement to the automated integration test [test_service_cluster](#test_service_cluster).

**Preconditions:**
* A two-node service cluster is configured in security servers *xtee8.ci.kit* and *xtee10.ci.kit*.
* A security server client of a third security server (*xtee9.ci.kit*) has an access right to the clustered service.

**Main scenario:**
* Stop the proxy in both security servers of the service cluster (`sudo service xroad-proxy stop`).
* Send an X-Road request from the service client in security server *xtee9.ci.kit* to the clustered service. The example request can be found in the source repository of the project at `src/systemtest/op-monitoring/requests/service_cluster.query`.
* Wait for the response - it takes up to 5 minutes before receiving a response stating that any target hosts could not be connected.

**Expected output:**
* There is an operational data record of the request in the operational monitoring database of the service client's security server.
* The value of the field 'succeeded' is false.
* The value of the field 'service_security_server_address' is null.

### 8.3 Run Operational Monitoring Data Cleanup

Test case for verifying that operational monitoring data is cleaned up periodically. The test can be executed in any of the operational monitoring daemon servers.

**Preconditions:**
* In the server where the test is executed, the default value has been set for system parameters *op-monitor.keep-records-for-days* and *op-monitor.clean-interval*.

**Main scenario:**
* Log in to operational monitoring database as user opmonitor, see [logging in to operational monitoring database.](#log_in_db)
* Count the records in the table `operational_data`:
   ```sql
   SELECT COUNT(*) FROM operational_data;
   ```
* The default value of the system parameter *op-monitor.keep-records-for-days* is 7 days. Operational data records that are older than one week will be deleted when data cleanup is run. Insert an operational data record with a timestamp from 2 weeks ago. SQL example:
   ```sql
   INSERT INTO operational_data(
       id, monitoring_data_ts, security_server_internal_ip,
       security_server_type, request_in_ts, response_out_ts, succeeded)
     VALUES (
       1,
       extract(epoch from (
         select date_trunc('day', NOW() - interval '2 weeks'))),
       '0.0.0.0', 'Client', 1, 1, 'f');
   ```
* The default value of the system parameter *op-monitor.clean-interval* is 12 hours. Add the following line to the `[op-monitor]` section of the file `/etc/xroad/conf.d/local.ini` to run data cleanup once a minute:
   ```
   clean-interval="0 0/1 * 1/1 * ? *"
   ```
* Restart the operational monitoring daemon (`sudo service xroad-opmonitor restart`).
* Wait for a minute for the data cleanup to run.

**Expected output:**
* The operational data record with a timestamp from 2 weeks ago has been deleted from the operational monitoring database.
* The number of records in the table `operational_data` is equal to the number of records before adding the record with a timestamp from 2 weeks ago.

### 8.4 Receive Operational Data in Multiple Batches

Test case for verifying that in case the amount of relevant operational data records exceeds the value of the *op-monitor.max-records-in-payload* system parameter, the operational data is returned in correct batches and the value of 'nextRecordsFrom' element in the operational data response is correct. This test case is a supplement to the automated integration test [ test_limited_operational_data_response](#test_limited_operational_data_response).

**Background:**

While composing the operational data response, the number of records allowed in maximum payload plus all records that have the same timestamp as the last included record are included in the response. Therefore more records than specified by the system parameter *op-monitor.max-records-in-payload* can be included in the response in case there is more than one record with the same timestamp as the last included record in the operational monitoring database. The element 'nextRecordsFrom' is present in the operational data response only in case the result of the query includes more records than the defined maximum number of records in the response allows.

**Preconditions:**
* In security server *xtee9.ci.kit* the value of the system parameter *op-monitor.records-available-timestamp-offset-seconds* has been set to 0 to avoid the presence of 'nextRecordsFrom' element in the operational data response due to long offset period.

**Main scenario:**

All test steps are executed in security server *xtee9.ci.kit*.
* The default value of the system parameter *op-monitor.max-records-in-payload* is 10,000. To limit the maximum number of records in payload to 1, add the following line to the `[op-monitor]` section of the file `/etc/xroad/conf.d/local.ini`:
   ```
   max-records-in-payload=1
   ```
* Restart the operational monitoring daemon (`sudo service xroad-opmonitor restart`).
* It is necessary to populate the operational monitoring database with some data suitable for testing. To do that, send the following requests to security server *xtee9.ci.kit*:
  * send an X-Road request;
  * wait for a couple of seconds;
  * send an operational data request;
  * wait for a couple of seconds;
  * send another X-Road request;

 The example requests can be found in the source repository of the project at `src/systemtest/op-monitoring/requests`. Use `simple.query` for an X-Road request and `operational_data.query` for an operational data request.
* Log in to the operational monitoring database (see [logging in to operational monitoring database](#log_in_db)) and view the timestamps of the most recent operational data records. SQL example:
   ```sql
   SELECT monitoring_data_ts, message_id, service_code, security_server_type
   FROM operational_data
   ORDER BY monitoring_data_ts
   DESC LIMIT 5;
   ```
* Make sure that the value of 'monitoring_data_ts' is unique in case of X-Road request records, and equal in case of operational data records for both the client and producer roles. Send the operational data requests to security server *xtee9.ci.kit* with the following 'recordsFrom' and 'recordsTo' values and expecting the following responses.

  1. Fill both 'recordsFrom' and 'recordsTo' value with the 'monitoring_data_ts' value of the first X-Road request in the database.

   **Expected output**: There is one operational data record in the operational data response: the record of the first X-Road request. The element 'nextRecordsFrom' is not present in the response.
  2. Fill both 'recordsFrom' and 'recordsTo' value with the 'monitoring_data_ts' value of the operational data request records in the database.

   **Expected output**: There are 2 operational data records in the operational data response: the client and producer side records of the operational data request. The element 'nextRecordsFrom' is not present in the response.
  3. Fill the 'recordsFrom' value in with the 'monitoring_data_ts' value of the first X-Road request in the database. Fill the 'recordsTo' value in with the 'monitoring_data_ts' value of the operational data request records in the database.

   **Expected output**: There is one operational data record in the operational data response: the record of the first X-Road request. The element 'nextRecordsFrom' is present in the response. The value of the element 'nextRecordsFrom' is (the value of 'monitoring_data_ts' of the first X-Road request + 1).
  4. Fill the 'recordsFrom' value in with the 'monitoring_data_ts' value of the operational data request records in the database. Fill the 'recordsTo' value in with the 'monitoring_data_ts' value of the second X-Road request in the database.

   **Expected output**: There are 2 operational data records in the operational data response: the client and producer side records of the operational data request. The element 'nextRecordsFrom' is present in the response. The value of the element 'nextRecordsFrom' is (the value of 'monitoring_data_ts' of the operational data request records + 1).

### 8.5 Configure an External Monitoring Daemon
Test case for verifying that it is possible to configure a secure connection between the security server and the external operational monitoring daemon.

**Preconditions:**
* The tester has superuser access to a server corresponding to the minimum requirements for an external monitoring daemon (see [[UG-SS]](#UG-SS)) to be used for installing an external operational monitoring daemon.

**Test scenario:**
* Install an external operational monitoring daemon according to the instructions in [[UG-SS]](#UG-SS).
* Configure security server *xtee10.ci.kit* to use the external operational monitoring daemon installed in the previous step over a secure connection. Follow the instructions in [[UG-SS]](#UG-SS).
* Send an X-Road request to a service provider in security server *xtee10.ci.kit*. The example request can be found in the source repository of the project at `src/systemtest/op-monitoring/requests/service_in_ss2.query` (the request endpoint is security server *xtee9.ci.kit*).
* Log in to the operational monitoring database in the external monitoring daemon (see [logging in to operational monitoring database](#log_in_db)) and ascertain that the operational monitoring data of the request sent in the previous step has been saved in the database. SQL example:
  ```sql
  SELECT * FROM operational_data WHERE message_id='abc';
  ```
* Send an operational data request to security server *xtee10.ci.kit*. Fill both 'recordsFrom' and 'recordsTo' value in with the 'monitoring_data_ts' value of the X-Road request that was sent to a service provider in security server xtee10.ci.kit. The example request can be found in the source repository of the project at `src/systemtest/op-monitoring/requests/operational_data_ss2.query`.

  **Expected output:** An operational data response is received. The operational data response contains the record of the X-Road request that was sent to a service provider in security server *xtee10.ci.kit*.
* Send a health data request to security server *xtee10.ci.kit*. The example request can be found in the source repository of the project at `src/systemtest/op-monitoring/requests/health_data_ss2.query`.

  **Expected output:** A health data response is received. The health data response contains the health data about the service that was queried in the first X-Road request as well as the health data about the service 'getSecurityServerOperationalData'.

### 8.6 Use Invalid Certificates for TLS Connection
Test case for verifying that the secure connection between the security server and the external operational monitoring daemon fails in case invalid certificates are configured for the TLS connection.

**Preconditions:**
* A secure connection has been configured between security server *xtee10.ci.kit* and external monitoring daemon *xtee11.ci.kit*. Note that the correct operational monitoring TLS certificate should be kept in security server *xtee10.ci.kit* for use after scenarios 1, 2 and 3 have been carried out.

**Test scenarios:**
1. Configure an invalid operational monitoring daemon TLS certificate in security server *xtee10.ci.kit*.
  * Replace the value of the parameter `tls-certificate` in the `[op-monitor]` section of the file `/etc/xroad/conf.d/local.ini` with a path to an invalid certificate. The example invalid certificate can be found in the source repository of the project at `src/systemtest/op-monitoring/misc/invalid_certificate.crt`.
  * Restart the proxy (`sudo service xroad-proxy restart`).
  * Send a health data request to security server *xtee10.ci.kit*. The example request can be found in the source repository of the project at `src/systemtest/op-monitoring/requests/health_data_ss2.query`.

  **Expected output:**
  * A SOAP fault is received as a health data query response (faultstring: java.security.cert.CertificateException: Operational monitoring daemon certificate not loaded, cannot verify server).
  * After restarting the proxy the following error is logged once in the proxy log in security server *xtee10.ci.kit*:
    * Could not load operational monitoring daemon certificate '&lt;file path&gt;'.
  * The following error messages are logged in the proxy log in security server *xtee10.ci.kit*:
    * Request processing error;
    * Sending operational monitoring data failed;
    * Operational monitoring daemon certificate not loaded, cannot verify server.
  * The following warning is logged in the monitoring daemon log in monitoring daemon *xtee11.ci.kit*:
    * Received fatal alert: certificate_unknown.

2. Configure an incorrect operational monitoring daemon TLS certificate in security server *xtee10.ci.kit*.
  * Generate the monitoring daemon certificate in security server *xtee9.ci.kit* (use the command `generate-opmonitor-certificate`).
  * In security server *xtee10.ci.kit*, replace the value of the parameter `tls-certificate` in the `[op-monitor]` section of the file `/etc/xroad/conf.d/local.ini` with a path to the monitoring daemon certificate of security server *xtee9.ci.kit* generated in the previous step.
  * Restart the proxy (`sudo service xroad-proxy restart`).
  * Send a health data request to security server *xtee10.ci.kit*.

  **Expected output:**
  * A SOAP fault is received as a health data query response (faultstring: java.security.cert.CertificateException: Server TLS certificate does not match expected operational monitoring daemon certificate).
  * The following error messages are logged in the proxy log in security server *xtee10.ci.kit*:
    * Request processing error;
    * Sending operational monitoring data failed;
    * Server TLS certificate does not match expected operational monitoring daemon certificate.
  * The following warning is logged in the monitoring daemon log in monitoring daemon *xtee11.ci.kit*:
    * Received fatal alert: certificate_unknown.

3. Configure a non-existent operational monitoring daemon TLS certificate in security server *xtee10.ci.kit*.
  * In security server *xtee10.ci.kit*, replace the value of the parameter `tls-certificate` in the `[op-monitor]` section of the file `/etc/xroad/conf.d/local.ini` with a path to a non-existent file.
  * Restart the proxy (`sudo service xroad-proxy restart`).
  * Send a health data request to security server *xtee10.ci.kit*.

  **Expected output:**
  * A SOAP fault is received as a health data query response (faultstring: java.security.cert.CertificateException: Operational monitoring daemon certificate not loaded, cannot verify server).
  * After restarting the proxy the following error is logged once in the proxy log in security server *xtee10.ci.kit*:
    * Could not load operational monitoring daemon certificate '&lt;file path&gt;' (No such file or directory)
  * The following error messages are logged in the proxy log in security server *xtee10.ci.kit*:
    * Request processing error;
    * Sending operational monitoring data failed;
    * Operational monitoring daemon certificate not loaded, cannot verify server.
  * The following warning is logged in the monitoring daemon log in monitoring daemon *xtee11.ci.kit*:
    * Received fatal alert: certificate_unknown.

  *After this test, restore the communication between the security server and the operational monitoring daemon (to achieve the precondition of this test case).*
    * Configure the correct operational monitoring daemon TLS certificate in security server *xtee10.ci.kit*
    * Restart the proxy (`sudo service xroad-proxy restart`).

4. Configure an invalid security server TLS certificate in operational monitoring daemon *xtee11.ci.kit*.
  * Replace the value of the parameter `client-tls-certificate` in the `[op-monitor]` section of the file `/etc/xroad/conf.d/local.ini` with a path to an invalid certificate. The example invalid certificate can be found in the source repository of the project at `src/systemtest/op-monitoring/misc/invalid_certificate.crt`.
  * Restart the monitoring daemon (`sudo service xroad-opmonitor restart`).
  * Send a health data request to security server *xtee10.ci.kit*.

  **Expected output:**
  * A SOAP fault is received as a health data query response (faultstring: Remote host closed connection during handshake).
  * The following error messages are logged in the proxy log in security server *xtee10.ci.kit*:
    * Request processing error;
    * Sending operational monitoring data failed;
    * Remote host closed connection during handshake.
  * After restarting the monitoring daemon the following error is logged once in the monitoring daemon log in monitoring daemon *xtee11.ci.kit*:
    * Could not load client certificate '&lt;file path&gt;'

5. Configure an incorrect security server TLS certificate in operational monitoring daemon *xtee11.ci.kit*.
  * In security server *xtee9.ci.kit*, the security server internal certificate can be found in `/etc/xroad/ssl/internal.crt`.
  * In monitoring daemon *xtee11.ci.kit*, replace the value of the parameter `client-tls-certificate` in the `[op-monitor]` section of the file `/etc/xroad/conf.d/local.ini` with the path to the security server certificate of security server *xtee9.ci.kit* refered to in the previous step.
  * Restart the monitoring daemon (`sudo service xroad-opmonitor restart`).
  * Send a health data request to security server *xtee10.ci.kit*.

  **Expected output:**
  * A SOAP fault is received as a health data query response (faultstring: Remote host closed connection during handshake).
  * The following error messages are logged in the proxy log in security server *xtee10.ci.kit*:
    * Request processing error;
    * Sending operational monitoring data failed;
    * Remote host closed connection during handshake.
  * The following warning is logged in the monitoring daemon log in monitoring daemon *xtee11.ci.kit*:
    * General SSLEngine problem.

6. Configure a non-existent security server TLS certificate in operational monitoring daemon *xtee11.ci.kit*.
  *  In monitoring daemon *xtee11.ci.kit*, replace the value of the parameter `client-tls-certificate` in the `[op-monitor]` section of the file `/etc/xroad/conf.d/local.ini` with a path to a non-existent file.
  * Restart the monitoring daemon (`sudo service xroad-opmonitor restart`).
  * Send a health data request to security server *xtee10.ci.kit*.

  **Expected output:**
  * A SOAP fault is received as a health data query response (faultstring: Remote host closed connection during handshake).
  * The following error messages are logged in the proxy log in security server *xtee10.ci.kit*:
    * Request processing error;
    * Sending operational monitoring data failed;
    * Remote host closed connection during handshake.
  * After restarting the monitoring daemon the following error is logged once in the monitoring daemon log in monitoring daemon *xtee11.ci.kit*:
    * Could not load client certificate '&lt;file path&gt;' (No such file or directory)
