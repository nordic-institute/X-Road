# X-Road: Operational Monitoring JMX Protocol <!-- omit in toc -->

**Technical Specification**

Version: 1.2  
Doc. ID: PR-OPMONJMX

| Date       | Version | Description                                                         | Author           |
|------------|---------|---------------------------------------------------------------------|------------------|
|            | 0.2     | Initial version                                                     |                  |
| 23.01.2017 | 0.3     | Added license text, table of contents and version history           | Sami Kallio      |
| 05.03.2018 | 0.4     | Added terms and abbreviations reference and moved terms to term doc | Tatu Repo        |
| 12.12.2019 | 1.0     | Add description of serviceType gauges                               | Ilkka Seppälä    |
| 25.06.2020 | 1.1     | Add note about JMX being disabled by default                        | Petteri Kivimäki |
| 01.06.2023 | 1.2     | Update references                                                   | Petteri Kivimäki |

## Table of Contents <!-- omit in toc -->

<!-- toc -->

- [License](#license)
- [1 Introduction](#1-introduction)
  - [1.1 Terms and Abbreviations](#11-terms-and-abbreviations)
  - [1.2 References](#12-references)
- [2 Encoding X-Road Service Identifiers in Object Names](#2-encoding-x-road-service-identifiers-in-object-names)
- [3 Objects, Attributes and Operations Exposed over JMXMP](#3-objects-attributes-and-operations-exposed-over-jmxmp)
  - [3.1 Gauge Metrics](#31-gauge-metrics)
  - [3.2 Counter Metrics](#32-counter-metrics)
  - [3.3 Histogram Metrics](#33-histogram-metrics)

<!-- tocstop -->

# License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/.

# 1 Introduction

This document specifies the format and protocol for exchanging health data of X-Road security servers that the X-Road operational monitoring daemon makes available for applications implementing the Java Management Extensions (JMX) using the JMX Messaging Protocol (JMXMP).

The Java Management Extensions define and architecture, the design patterns, the APIs, and the services for application and network management and monitoring in the Java programming language \[[JMX](#Ref_JMX)\].

The JMX Messaging Protocol (JMXMP) connector is a configuration of the generic connector where the transport protocol is based on TCP and the object wrapping is native Java serialization \[[JMXMP](#Ref_JMXMP)\].

In this document, the standard Managed Beans (`MBeans`) exposed by the operational monitoring daemon, their attributes, value types etc, are documented. The underlying protocol, error handling, authentication and encryption used in data exchange over JMXMP, are not in the scope of this document. For such details, \[[JMX](#Ref_JMX)\] and \[[JMXMP](#Ref_JMXMP)\] should be consulted.

All the sections of this specification contain normative information. All the references are normative.

This specification does not include option for partially implementing the protocol – the conformant implementation must implement the entire specification.

**By default, operational monitoring JMX interface is disabled. Enabling the JMX interface is explained in \[[ARC-OPMOND](#Ref_ARC-OPMOND)\].**

## 1.1 Terms and Abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\].

## 1.2 References

<a name="Ref_PR-MESS"></a>**PR-MESS** -- X-Road: Message Protocol v4.0. Document ID: [PR-MESS](../../Protocols/pr-mess_x-road_message_protocol.md).  
<a name="Ref_JMX"></a>**JMX** -- Java Management Extensions (JMX) Specification, version 1.4, http://download.oracle.com/otn-pub/jcp/jmx_remote-1_4-mrel2-eval-spec/jsr160-jmx-1_4-mrel4-spec-FINAL-v1_0.pdf  
<a name="Ref_JMXMP"></a>**JMXMP** -- Using JMX Connectors to Manage Resources Remotely, http://docs.oracle.com/javase/8/docs/technotes/guides/jmx/overview/connectors.html  
<a name="Ref_METRICS"></a>**METRICS** -- GitHub - dropwizard/metrics: Capturing JVM- and application-level metrics. So you know what's going on, https://github.com/dropwizard/metrics  
<a name="Ref_ZABBIX"></a>**ZABBIX** -- Zabbix Documentation 3.0 - JMX monitoring, https://www.zabbix.com/documentation/3.0/manual/config/items/itemtypes/jmx_monitoring  
<a name="Ref_TERMS"></a>**TA-TERMS** -- X-Road Terms and Abbreviations. Document ID: [TA-TERMS](../../terms_x-road_docs.md).<br />
<a name="Ref_ARC-OPMOND"></a>**ARC-OPMOND** -- X-Road: Operational Monitoring Daemon Architecture. Document ID: [ARC-OPMOND](../Architecture/arc-opmond_x-road_operational_monitoring_daemon_architecture_Y-1096-1.md).

<a name="section_2"></a>
# 2 Encoding X-Road Service Identifiers in Object Names

In the object names of exposed MBeans, X-Road service identifiers are encoded according to the following rules:

* The full string form of the identifier of the service is used, as defined in \[[PR-MESS](#Ref_PR-MESS)\].
* If no subsystem is associated with the service, two forward slashes (`//`) are used in succession, for example: `EE/GOV/00000001//getSecurityServerOperationalData`. This is to enable extraction of the parts of the identifier from the string form.
* Also for being able to extract the parts of the identifier, the forward slashes that are found in the parts of the identifiers, are escaped with the escape sequence `&#47;`.
* Because the JMXMP protocol imposes the XML character set on the names of objects, the following characters are escaped using XML escape sequences: `"` (`&quot;`), `'` (`&apos;`), `<` (`&lt;`), `>` (`&gt;`), `&` (`&amp;`).
* In order to provide compatibility with the Zabbix monitoring system \[[ZABBIX](#Ref_ZABBIX)\], the following characters are escaped in addition: `.` (`&#46;`), `\` (`&#92;`), the space (`&#32;`), `,` (`&#44;`), `[` (`&#91;`), `]` (`&#93;`).

# 3 Objects, Attributes and Operations Exposed over JMXMP

The `MBean` objects exposed by the operational monitoring daemon over JMXMP have been implemented using the types of metrics available in the `com.codahale.metrics` library \[[METRICS](#Ref_METRICS)\], version 3.0. All the metric classes implement the `com.codahale.metrics.Metric` interface. More precisely, the following classes are used for making health data available:

* `com.codahale.metrics.Gauge`  
* `com.codahale.metrics.Counter`  
* `com.codahale.metrics.Histogram`  

For each `MBean` object there is an associated `MBeanInfo` object available that describes the management interface exposed by the object: the name of the class, the name of the object and its description for consumers of the data over JMXMP.

## 3.1 Gauge Metrics

The value of the `ClassName` attribute of the `MBeanInfo` object for gauges is `com.codahale.metrics.JmxReporter$JmxGauge`.

All gauge metrics expose the `Value` attribute and the `objectName()`operation over JMXMP.

The operational monitoring daemon provides the following general gauges:
* `metrics:name=monitoringStartupTimestamp` -- timestamp of the most recent start event of the operational monitoring daemon
* `metrics:name=statisticsPeriodSeconds` -- the configured period of gathering and making available health data. All the statistical data provided, are calculated over this period.

For each service mediated during the configured statistics period, the following gauges are provided:
* `metrics:name=lastSuccessfulRequestTimestamp(<service ID>)`    
* `metrics:name=lastUnsuccessfulRequestTimestamp(<service ID>)`  
* `metrics:name=serviceType(<service ID>)`

where `<service ID>` will be replaced by the full ID of the service encoded as described in \[[Section 2](#section_2)\].

## 3.2 Counter Metrics

The value of the `ClassName` attribute of the `MBeanInfo` object for counters is `com.codahale.metrics.JmxReporter$JmxCounter`.

All counter metrics expose the `Count` attribute and the `objectName()`operation over JMXMP.

For each service mediated during the configured statistics period, the following counters are provided:
* `metrics:name=successfulRequestCount(<service ID>)`  
* `metrics:name=unsuccessfulRequestCount(<service ID>)`  

where `<service ID>` will be replaced by the full ID of the service encoded as described in \[[Section 2](#section_2)\.

## 3.3 Histogram Metrics

The value of the `ClassName` attribute of the `MBeanInfo` object for histograms is `com.codahale.metrics.JmxReporter$JmxHistogram`.

All the histogram metrics expose the following attributes:
* `50thPercentile`  
* `75thPercentile`  
* `95thPercentile`  
* `98thPercentile`  
* `99thPercentile`  
* `999thPercentile`  
* `Count`  
* `Max`  
* `Min`  
* `Mean`  
* `StdDev`  

The operations exposed by histogram metrics are `objectName()` and `values()`.

For each service mediated during the configured statistics period, the following histograms are provided:

* `metrics:name=requestDuration(<service ID>)`  
* `metrics:name=requestSoapSize(<service ID>)`  
* `metrics:name=responseSoapSize(<service ID>)`  

where `<service ID>` will be replaced by the full ID of the service encoded as described in \[[Section 2](#section_2)\].
