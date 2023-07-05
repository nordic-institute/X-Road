# X-Road: Operational Monitoring System Parameters

# User Guide

Version: 0.6  
Doc ID: UG-OPMONSYSPAR

| Date       | Version | Description                                                                                                             | Author           |
|------------|---------|-------------------------------------------------------------------------------------------------------------------------|------------------|
|            | 0.2     | Initial version                                                                                                         |                  |
| 23.01.2017 | 0.3     | Added license text, table of contents and version history                                                               | Sami Kallio      |
| 17.03.2017 | 0.4     | Added new parameters *op-monitor-buffer.connection-timeout-seconds* and *op-monitor-service.connection-timeout-seconds* | Kristo Heero     |
| 05.03.2018 | 0.5     | Added reference to terms and abbreviations doc                                                                          | Tatu Repo        | 
| 01.06.2023 | 0.6     | Update references                                                                                                       | Petteri Kivimäki |

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

This document describes the system parameters of the X-Road operational monitoring system components – the operational monitoring daemon, the operational monitoring buffer and the operational monitoring service. Changing the default values of the system parameters is explained in \[[UG-SYSPAR](#UG-SYSPAR)\].

### 1.1 Terms and abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\].

### 1.2 References

<a name="UG-SYSPAR"></a>**UG-SYSPAR** -- X-Road: System Parameters. Document ID: [UG-SYSPAR](../../Manuals/ug-syspar_x-road_v6_system_parameters.md).  
<a name="UG-SS"></a>**UG-SS** -- X-Road: Security Server User Guide. Document ID: [UG-SS](../../Manuals/ug-ss_x-road_6_security_server_user_guide.md).  
<a name="CRON"></a>**CRON** -- Quartz Scheduler Cron Trigger Tutorial,  http://www.quartz-scheduler.org/documentation/quartz-2.2.x/tutorials/crontrigger.html  
<a name="Ref_TERMS" class="anchor"></a>**TA-TERMS** -- X-Road Terms and Abbreviations. Document ID: [TA-TERMS](../../terms_x-road_docs.md).

## 2 Operational Monitoring System Parameters

This chapter describes the system parameters used by the X-Road operational monitoring system. Changing the parameter values in the configuration file requires restarting of the system services. Managing the system services is explained in \[[UG-SS](#UG-SS)\].

* To change the parameter values for the server component *op-monitor*, the service *xroad-opmonitor* must be restarted after changing all parameters except *op-monitor.tls-certificate*.
* The service *xroad-proxy* must be restarted after changing the parameters:  
  * *op-monitor.host*  
  * *op-monitor.port*
  * *op-monitor.scheme*
  * *op-monitor.tls-certificate*.  

 When changing the previous parameter values of an external monitoring daemon server, only the service *xroad-opmonitor* must be restarted.
* To change the parameter values for the server components *op-monitor-buffer* and *op-monitor-service*, the service *xroad-proxy* must be restarted.

Server Component  | Parameter                 | Default Value        | Explanation
----------------- | ------------------------- | -------------------- | ------------------
op-monitor        | clean-interval            | 0 0 0/12 1/1 \* ? \* | CRON expression \[[CRON](#CRON)\] defining the interval of deleting any operational data records that are older than *op-monitor.keep-records-for-days* from the operational monitoring database.
op-monitor        | client-tls-certificate    | /etc/xroad/ssl/internal.crt | Absolute filename of the TLS certificate (security server internal certificate) used by the HTTP client sending requests to the operational monitoring daemon. Configured in monitoring daemon server in case an external monitoring daemon is used.
op-monitor        | health-statistics-period-seconds | 600           | The period for gathering health statistics about services in seconds.
op-monitor        | host                      | localhost            | The host address on which the operational monitoring daemon listens.
op-monitor        | keep-records-for-days     | 7                    | Number of days to keep operational data records in the operational monitoring database. If a record is older than this value, the record is deleted from the database.
op-monitor        | max-records-in-payload    | 10000                | Maximum number of operational data records in the operational data response payload.
op-monitor        | port                      | 2080                 | TCP port on which the operational monitoring daemon listens.
op-monitor        | records-available-timestamp-offset-seconds | 60  | The offset used to calculate the timestamp to which the operational data records are available in seconds. Only records with earlier timestamp than *'currentSeconds - offset'* are available.
op-monitor        | scheme                    | http                 | The URI scheme name of the operational monitoring daemon. Possible values are *http* and *https*.
op-monitor        | tls-certificate           |/etc/xroad/ssl/opmonitor.crt | Absolute filename of the operational monitoring daemon TLS certificate. Configured in security server in case an external monitoring daemon is used.
op-monitor-buffer | connection-timeout-seconds| 30                   | HTTP client connection timeout in seconds.
op-monitor-buffer | max-records-in-message    | 100                  | Maximum number of operational data records in a message sent by the operational monitoring buffer to the operational monitoring daemon.
op-monitor-buffer | sending-interval-seconds  | 5                    | The interval in seconds at which the operational monitoring buffer (re)tries to send records to the operational monitoring daemon. Normally, the buffer triggers the sending mechanism immediately when it receives a new record. In case of heavy load or sending failures the records are accumulating in the buffer and need periodical attention.
op-monitor-buffer | socket-timeout-seconds    | 60                   | The socket timeout (*SO_TIMEOUT*) of sending operational monitoring records from the operational monitoring buffer to the operational monitoring daemon in seconds.
op-monitor-buffer | size                      | 20000                | Maximum size of operational monitoring buffer. In case buffer size < 1, operational monitoring data is not stored and sent to the operational monitoring daemon.
op-monitor-service| connection-timeout-seconds| 30                   | HTTP client connection timeout in seconds.
op-monitor-service| socket-timeout-seconds    | 60                   | The socket timeout (*SO_TIMEOUT*) of sending the operational data request to the operational monitoring daemon in seconds.
