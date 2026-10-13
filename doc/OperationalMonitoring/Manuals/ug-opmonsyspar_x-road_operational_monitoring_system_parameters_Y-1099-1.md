# X-Road: Operational Monitoring System Parameters

# User Guide

Version: 0.8  
Doc ID: UG-OPMONSYSPAR

| Date       | Version | Description                                                                                                             | Author             |
|------------|---------|-------------------------------------------------------------------------------------------------------------------------|--------------------|
|            | 0.2     | Initial version                                                                                                         |                    |
| 23.01.2017 | 0.3     | Added license text, table of contents and version history                                                               | Sami Kallio        |
| 17.03.2017 | 0.4     | Added new parameters *op-monitor-buffer.connection-timeout-seconds* and *op-monitor-service.connection-timeout-seconds* | Kristo Heero       |
| 05.03.2018 | 0.5     | Added reference to terms and abbreviations doc                                                                          | Tatu Repo          | 
| 01.06.2023 | 0.6     | Update references                                                                                                       | Petteri Kivimäki   |
| 10.07.2025 | 0.7     | Added grpc related properties                                                                                           | Mikk-Erik Bachmann |
| 13.10.2026 | 0.8     | Operational monitoring parameters of X-Road 8: store and query over gRPC                                                | Eneli Reimets      |

## Table of Contents

<!-- toc -->

- [License](#license)
- [1 Introduction](#1-introduction)
  * [1.1 Terms and abbreviations](#11-terms-and-abbreviations)
  * [1.2 References](#12-references)
- [2 Operational Monitoring System Parameters](#2-operational-monitoring-system-parameters)

<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/.


## 1 Introduction

This document describes the system parameters of the X-Road operational monitoring system components – the operational monitoring daemon, the operational monitoring buffer and the operational monitoring service. Changing the default values of the system parameters is explained in [Chapter 2](#2-operational-monitoring-system-parameters).

### 1.1 Terms and abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\].

### 1.2 References

<a name="UG-SS"></a>**UG-SS** -- X-Road: Security Server User Guide. Document ID: [UG-SS](../../Manuals/ug-ss_x-road_6_security_server_user_guide.md).  
<a name="CRON"></a>**CRON** -- Quartz Scheduler Cron Trigger Tutorial,  http://www.quartz-scheduler.org/documentation/quartz-2.2.2/tutorials/crontrigger.html  
<a name="Ref_TERMS" class="anchor"></a>**TA-TERMS** -- X-Road Terms and Abbreviations. Document ID: [TA-TERMS](../../terms_x-road_docs.md).

## 2 Operational Monitoring System Parameters

This chapter describes the system parameters used by the X-Road 8 operational monitoring system. The parameters are configuration properties whose names start with `xroad.`. Change a value in the System Parameters view of the Security Server user interface or with the script `/usr/share/xroad/scripts/db_property.sh`. A changed value takes effect when the services that use it are restarted. Managing the system services is explained in \[[UG-SS](#UG-SS)\].

* To change the parameters `xroad.op-monitor.*`, the service *xroad-opmonitor* must be restarted.
* To change the parameters `xroad.common-rpc.channel.op-monitor.*`, the services *xroad-proxy* and *xroad-proxy-ui-api* must be restarted. Both services connect to the operational monitoring daemon.
* To change the parameters `xroad.proxy.addon.op-monitor.*`, the service *xroad-proxy* must be restarted.
* To change the parameter `xroad.common-rpc.use-tls`, all the X-Road services of the Security Server must be restarted.

The security server and the operational monitoring daemon communicate over gRPC on port 2081 by default. Operational data is stored and queried over this connection.

The parameter `xroad.common-rpc.channel.op-monitor.deadline-after` is the time in milliseconds a call of the security server to the operational monitoring daemon may take. The channel waits for the daemon to become ready, so a query to a stopped or unreachable daemon fails only when this time has passed. The parameter replaces the HTTP client timeouts of earlier versions.

The parameter `xroad.op-monitor.max-records-in-payload` must be set with the message size limit of the channel in mind. If a query response is larger than `xroad.common-rpc.channel.op-monitor.max-inbound-message-size`, the query fails with the fault code `service_failed.operational_data_response_too_large`. Raising `max-records-in-payload` can therefore make a query exceed the limit; in that case lower `max-records-in-payload` or raise the limit of the channel.

The parameter `xroad.proxy.addon.op-monitor.buffer.max-message-size` must be lower than `xroad.op-monitor.rpc.max-inbound-message-size` of the operational monitoring daemon. If the limit of the daemon is not set, the gRPC default of 4 MiB applies.

| Server Component   | Parameter                                                   | Default Value                | Explanation |
|--------------------|-------------------------------------------------------------|------------------------------|-------------|
| op-monitor | `xroad.op-monitor.clean-interval` | 0 0 0/12 1/1 \* ? \* | CRON expression \[[CRON](#CRON)\] defining the interval of deleting any operational data records that are older than *xroad.op-monitor.keep-records-for-days* from the operational monitoring database. |
| op-monitor | `xroad.op-monitor.health-statistics-period-seconds` | 600 | The period for gathering health statistics about services in seconds. |
| op-monitor | `xroad.op-monitor.keep-records-for-days` | 7 | Number of days to keep operational data records in the operational monitoring database. If a record is older than this value, the record is deleted from the database. |
| op-monitor | `xroad.op-monitor.max-records-in-payload` | 10000 | Maximum number of operational data records in the operational data response payload. See the message size limit above. |
| op-monitor | `xroad.op-monitor.records-available-timestamp-offset-seconds` | 60 | The offset used to calculate the timestamp to which the operational data records are available in seconds. Only records with earlier timestamp than *'currentSeconds - offset'* are available. |
| op-monitor | `xroad.op-monitor.rpc.listen-address` | 127.0.0.1 | The IP address on which the gRPC server of the operational monitoring daemon listens. In the container deployment the default is 0.0.0.0. |
| op-monitor | `xroad.op-monitor.rpc.port` | 2081 | The TCP port on which the gRPC server of the operational monitoring daemon listens. |
| op-monitor | `xroad.op-monitor.rpc.max-inbound-message-size` | not set (gRPC default, 4 MiB) | Maximum size in bytes of a request the gRPC server of the operational monitoring daemon accepts, for example a batch of stored operational data records. The minimum is 65536. |
| op-monitor channel | `xroad.common-rpc.channel.op-monitor.host` | 127.0.0.1 | The host name or address used to connect to the gRPC server of the operational monitoring daemon. In the container deployment the default is *op-monitor*. |
| op-monitor channel | `xroad.common-rpc.channel.op-monitor.port` | 2081 | The port used to connect to the gRPC server of the operational monitoring daemon. |
| op-monitor channel | `xroad.common-rpc.channel.op-monitor.deadline-after` | 60000 | Milliseconds before a gRPC call to the operational monitoring daemon times out. The channel waits for the daemon to become ready within this time. |
| op-monitor channel | `xroad.common-rpc.channel.op-monitor.max-inbound-message-size` | 16777216 | Maximum size in bytes of a response the channel accepts, for example the response of a monitoring query. The minimum is 4194304 (4 MiB). A query response over the limit fails with an error; lower *xroad.op-monitor.max-records-in-payload* or raise this limit. |
| common | `xroad.common-rpc.use-tls` | true | When enabled, mutual TLS is used for all gRPC communication between the Security Server internal services, including the connection to the operational monitoring daemon. |
| op-monitor-buffer | `xroad.proxy.addon.op-monitor.enabled` | false | Enables the operational monitoring add-on of the proxy. |
| op-monitor-buffer | `xroad.proxy.addon.op-monitor.buffer.max-message-size` | 3145728 | Maximum size in bytes of one store request sent by the operational monitoring buffer to the operational monitoring daemon. Larger batches are split into several requests. The minimum is 65536. |
| op-monitor-buffer | `xroad.proxy.addon.op-monitor.buffer.max-records-in-message` | 100 | Maximum number of operational data records in a message sent by the operational monitoring buffer to the operational monitoring daemon. |
| op-monitor-buffer | `xroad.proxy.addon.op-monitor.buffer.sending-interval-seconds` | 5 | The interval in seconds at which the operational monitoring buffer (re)tries to send records to the operational monitoring daemon. Normally, the buffer triggers the sending mechanism immediately when it receives a new record. In case of heavy load or sending failures the records are accumulating in the buffer and need periodical attention. |
| op-monitor-buffer | `xroad.proxy.addon.op-monitor.buffer.size` | 20000 | Maximum size of operational monitoring buffer. In case buffer size < 1, operational monitoring data is not stored and sent to the operational monitoring daemon. |
