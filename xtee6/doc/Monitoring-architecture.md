# Overview

X-Road monitoring is conceptually split into two parts: _environmental_ and _operational_ monitoring:

* **Environmental monitoring** is the monitoring of the X-Road environment: details of the security servers such as operating system, memory, disk space, CPU load, running processes and installed packages, etc.
* **Operational monitoring** is the monitoring of operational statistics such as which services have been called, how many times, what is the average response time, etc.

This document describes environmental monitoring architecture.

<a name="refsanchor"></a>
## References

| Code||
| ------------- |-------------|
| PR-GCONF      | Cybernetica AS. X-Road: Protocol for Downloading Configuration |
| UC-GCONF      | Cybernetica AS. X-Road: Use Case Model for Global Configuration Distribution|
| PR-MESS | Cybernetica AS.X-Road: Message Protocol v4.0      |

# Components

![monitoring architecture](img/monitoring.png)

## Monitoring metaservice (proxymonitor add-on)

Monitoring metaservice responds to queries for monitoring data from security server's serverproxy interface. This metaservice requests the current monitoring data from local monitoring service, using [Akka](http://akka.io/). Monitoring metaservice translates the monitoring data to a SOAP XML response.

Monitoring service handles authorization of the requests, see [Access control](#acanchor). It reads monitoring configuration from distributed global monitoring configuration (see [UC-GCONF, PR-GCONF](#refsanchor)).

Monitoring metaservice is installed as a proxy add-on, with name `xroad-addon-proxymonitor`. 

## Monitoring service (xroad-monitor)

Monitoring service is responsible for collecting the monitoring data from one security server instance. It distributes the collected data to monitoring clients (normally the local monitoring metaservice) when requested through an Akka interface. 

Monitoring service uses several _sensors_ to collect the data. Sensors and related functionalities are build on top of [Dropwizard Metrics](https://github.com/dropwizard/metrics).

The following sensors produce monitoring data:
- `SystemMetricsSensor`
  - data:
    - CPU load
    - free memory
    - total memory
    - free swap space
    - total swap space
    - open file descriptor count
    - maximum file descriptor count
    - committed virtual memory
  - metrics are collected from [UnixOperatingSystemMXBean](https://docs.oracle.com/javase/8/docs/jre/api/management/extension/com/sun/management/UnixOperatingSystemMXBean.html)
  - data is refreshed every 5 seconds and analyzed in a 60s sliding window (for min/max/average/etc values)
- `DiskSpaceSensor`
  - data: total and free disk space for all filesystem roots
  - data is refreshed once per minute
- `OsInfoLister`
  - data: operating system information from `/proc/version`
  - for example `Linux version 3.13.0-70-generic (buildd@lgw01-34) (gcc version 4.8.2 (Ubuntu 4.8.2-19ubuntu1) ) #113-Ubuntu SMP Mon Nov 16 18:34:13 UTC 2015`
  - data is refreshed once per minute
- `ProcessLister`
  - data: list of running processes from command `ps -aew -o user,pcpu,start_time,pmem,pid,comm`
  - data is refreshed once per minute
- `XroadProcessLister`
  - data: list of running xroad processes from command, showing full command with arguments
  - data is refreshed once per minute
- `PackageLister`
  - data: list of installed packages and their versions
  - data is refreshed once per minute

Monitoring service is installed as a separate package, with name `xroad-monitor`. It runs in a separate process. 

The service also publishes the monitoring data via JMX. Local monitoring agents can use this as an alternative way to fetch monitoring data. With default configuration, JMX access is only allowed from localhost.

![monitoring JMX agent](img/monitoring-jmx.png)

## Central monitoring client

Central monitoring client is a specific security server, which has been granted access to query monitoring data from other security servers. See [Access control](#acanchor). The identity of this security server is configured using central server's admin user interface.

## Central monitoring data collector

Central monitoring data collector is responsible for collecting monitoring data from all the security servers. It does this by executing monitoring requests through the central monitoring client to all known security server instances. Data collector stores the data in some permanent storage, where it can be analyzed.

Data collector has not been implemented yet.

<a name="adminanchor"></a>
## Central server admin user interface

Identity of central monitoring client (if any) is configured using central server's admin user interface. Configuration is done by updating a specific optional configuration file (see [UC-GCONF](#refsanchor), "UC GCONF_05: Upload an Optional Configuration Part File") `monitoring-params.xml`. This configuration file is distributed to all security servers through the global configuration distribution process (see [UC-GCONF](#refsanchor), "UC GCONF_24: Download Configuration from a Configuration Source")

```xml
<tns:conf xmlns:id="http://x-road.eu/xsd/identifiers"
          xmlns:tns="http://x-road.eu/xsd/xroad.xsd"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://x-road.eu/xsd/xroad.xsd">
    <monitoringClient>
        <monitoringClientId id:objectType="SUBSYSTEM">
            <id:xRoadInstance>fdev</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>1710128-9</id:memberCode>
            <id:subsystemCode>LIPPIS</id:subsystemCode>
        </monitoringClientId>
    </monitoringClient>
</tns:conf>
```
One can configure either one member or a member's subsystem to be the central monitoring client. Permission to execute monitoring queries is strictly limited to that single member or subsystem - defining one subsystem to be a monitoring client does **not** grant the corresponding member access to querying monitoring data (and vice versa).  

The optional configuration for monitoring parameters is taken into use by installing package `xroad-centralserver-monitoring`. This package also includes the components that validate the updated xml monitoring configuration.

To disable central monitoring client altogether, update configuration to one which has no client:

```xml
<tns:conf xmlns:id="http://x-road.eu/xsd/identifiers"
          xmlns:tns="http://x-road.eu/xsd/xroad.xsd"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://x-road.eu/xsd/xroad.xsd">
    <monitoringClient>
    </monitoringClient>
</tns:conf>
```

# Monitoring in action

## Pull messaging model

Currently central monitoring data collection is done using _pull_ messaging model. Here, pull means that the central monitoring client sends requests to the individual security servers.

An alternative to this would be model where security servers periodically _push_ (send) the monitoring data to central monitoring client.

To support clustered configurations, monitoring queries use an extended X-Road message protocol.

## Modified X-Road message protocol

Fetching security server metrics uses the X-Road protocol. The original X-Road 6.0 message protocol (described in [PR-MESS](#refsanchor)) had header element `service` to define the recipient of a message. 

```xml
<SOAP-ENV:Envelope 
xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" 
xmlns:xrd="http://x-road.eu/xsd/xroad.xsd" 
xmlns:id="http://x-road.eu/xsd/identifiers" 
xmlns:prod="http://vrk-test.x-road.fi/producer">
   <SOAP-ENV:Header>
      <xrd:protocolVersion>4.0</xrd:protocolVersion>
      <xrd:id>1234</xrd:id>
      <xrd:userId>EE1234567890</xrd:userId>
      <xrd:client id:objectType="MEMBER">
         <id:xRoadInstance>fdev</id:xRoadInstance>
         <id:memberClass>GOV</id:memberClass>
         <id:memberCode>1710128-9</id:memberCode>
      </xrd:client>
      <xrd:service id:objectType="SERVICE">
         <id:xRoadInstance>fdev</id:xRoadInstance>
         <id:memberClass>GOV</id:memberClass>
         <id:memberCode>1710128-9</id:memberCode>
         <id:serviceCode>getRandom</id:serviceCode>
         <id:serviceVersion>v1</id:serviceVersion>
      </xrd:service>
   </SOAP-ENV:Header>
   <SOAP-ENV:Body>
      <prod:getRandom></prod:getRandom>
   </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```

For monitoring queries this is not enough. In a clustered security server configuration, one service can be served from multiple security servers. When X-Road routes the message, it picks one candidate based on which one answers the quickest. When executing monitoring queries, we need to be able to fetch monitoring data frin a specific security server in a cluster. To make this possible, a new element `securityServer` has been added. Using this element, called identifies which security server should respond with the monitoring data (`servercode` = `fdev-ss1.i.palveluvayla.com`). To execute a query, we call service `getSecurityServerMetrics`:

```xml
<SOAP-ENV:Envelope 
	xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" 
	xmlns:id="http://x-road.eu/xsd/identifiers" 
	xmlns:xrd="http://x-road.eu/xsd/xroad.xsd"
	xmlns:m="http://x-road.eu/xsd/monitoring">
    <SOAP-ENV:Header>
        <xrd:client id:objectType="MEMBER">
            <id:xRoadInstance>fdev</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>1710128-9</id:memberCode>
        </xrd:client>
        <xrd:service id:objectType="SERVICE">
            <id:xRoadInstance>fdev</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>1710128-9</id:memberCode>
            <id:serviceCode>getSecurityServerMetrics</id:serviceCode>
        </xrd:service>
        <xrd:securityServer>
            <id:xRoadInstance>fdev</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>1710128-9</id:memberCode>
            <id:serverCode>fdev-ss1.i.palveluvayla.com</id:serverCode>
        </xrd:securityServer>
        <xrd:id>ID11234</xrd:id>
        <xrd:protocolVersion>4.0</xrd:protocolVersion>
    </SOAP-ENV:Header>
    <SOAP-ENV:Body>
        <m:getSecurityServerMetrics/>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope
```

The response looks like:

```xml
<SOAP-ENV:Envelope 
    xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
    xmlns:id="http://x-road.eu/xsd/identifiers"
    xmlns:m="http://x-road.eu/xsd/monitoring"
    xmlns:xrd="http://x-road.eu/xsd/xroad.xsd">
   <SOAP-ENV:Header>
      <xrd:client id:objectType="MEMBER">
         <id:xRoadInstance>fdev</id:xRoadInstance>
         <id:memberClass>GOV</id:memberClass>
         <id:memberCode>1710128-9</id:memberCode>
      </xrd:client>
      <xrd:service id:objectType="SERVICE">
         <id:xRoadInstance>fdev</id:xRoadInstance>
         <id:memberClass>GOV</id:memberClass>
         <id:memberCode>1710128-9</id:memberCode>
         <id:serviceCode>getSecurityServerMetrics</id:serviceCode>
      </xrd:service>
      <xrd:securityServer>
         <id:xRoadInstance>fdev</id:xRoadInstance>
         <id:memberClass>GOV</id:memberClass>
         <id:memberCode>1710128-9</id:memberCode>
         <id:serverCode>fdev-ss1.i.palveluvayla.com</id:serverCode>
      </xrd:securityServer>
      <xrd:id>ID11234</xrd:id>
      <xrd:protocolVersion>4.0</xrd:protocolVersion>
      <xrd:requestHash algorithmId="http://www.w3.org/2001/04/xmlenc#sha512">mChpBRMvFlBBSNKeOxAJQBw4r6XdHZFuH8BOzhjsxjjOdaqXXyPXwnDEdq/NkYfEqbLUTi1h/OHEnX9F5YQ5kQ==</xrd:requestHash>
   </SOAP-ENV:Header>
   <SOAP-ENV:Body>
      <m:getSecurityServerMetricsResponse>
         <m:metricSet>
            <m:name>SERVER:fdev/GOV/1710128-9/fdev-ss1.i.palveluvayla.com</m:name>
            <m:stringMetric>
               <m:name>proxyVersion</m:name>
               <m:value>6.7.7-1.20151201075839gitb72b28e</m:value>
            </m:stringMetric>
            <m:metricSet>
               <m:name>systemMetrics</m:name>
               <m:stringMetric>
                  <m:name>OperatingSystem</m:name>
                  <m:value>Linux version 3.13.0-70-generic</m:value>
               </m:stringMetric>
               <m:numericMetric>
                  <m:name>TotalPhysicalMemory</m:name>
                  <m:value>2097684480</m:value>
               </m:numericMetric>
               <m:numericMetric>
                  <m:name>TotalSwapSpace</m:name>
                  <m:value>2097684480</m:value>
               </m:numericMetric>
            </m:metricSet>
            ...          
         </m:metricSet>
      </m:getSecurityServerMetricsResponse>
   </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```

The schema for the monitoring response is defined in `monitoring.xsd`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<schema xmlns="http://www.w3.org/2001/XMLSchema"
        xmlns:tns="http://x-road.eu/xsd/monitoring" xmlns:xs="http://www.w3.org/2001/XMLSchema"
        targetNamespace="http://x-road.eu/xsd/monitoring"
        elementFormDefault="qualified">
    <xs:complexType name="MetricType" abstract="true">
        <xs:sequence>
            <xs:element name="name" type="xs:string"/>
        </xs:sequence>
    </xs:complexType>
    <xs:complexType name="NumericMetricType">
        <xs:complexContent>
            <xs:extension base="tns:MetricType">
                <xs:sequence>
                    <xs:element name="value" type="xs:decimal"/>
                </xs:sequence>
            </xs:extension>
        </xs:complexContent>
    </xs:complexType>
    <xs:complexType name="StringMetricType">
        <xs:complexContent>
            <xs:extension base="tns:MetricType">
                <xs:sequence>
                    <xs:element name="value" type="xs:string"/>
                </xs:sequence>
            </xs:extension>
        </xs:complexContent>
    </xs:complexType>
    <xs:complexType name="HistogramMetricType">
        <xs:complexContent>
            <xs:extension base="tns:MetricType">
                <xs:sequence>
                    <xs:element name="updated" type="xs:dateTime"/>
                    <xs:element name="min" type="xs:decimal"/>
                    <xs:element name="max" type="xs:decimal"/>
                    <xs:element name="mean" type="xs:decimal"/>
                    <xs:element name="median" type="xs:decimal"/>
                    <xs:element name="stddev" type="xs:decimal"/>
                </xs:sequence>
            </xs:extension>
        </xs:complexContent>
    </xs:complexType>
    <xs:complexType name="MetricSetType">
        <xs:complexContent>
            <xs:extension base="tns:MetricType">
                <xs:sequence>
                    <xs:choice maxOccurs="unbounded">
                        <xs:element name="metricSet" type="tns:MetricSetType"/>
                        <xs:element name="numericMetric" type="tns:NumericMetricType"/>
                        <xs:element name="stringMetric" type="tns:StringMetricType"/>
                        <xs:element name="histogramMetric" type="tns:HistogramMetricType"/>
                    </xs:choice>
                </xs:sequence>
            </xs:extension>
        </xs:complexContent>
    </xs:complexType>
    <xs:element name="getSecurityServerMetricsResponse">
        <xs:complexType>
            <xs:sequence>
                <xs:element name="metricSet" type="tns:MetricSetType"/>
            </xs:sequence>
        </xs:complexType>
    </xs:element>
</schema>
```

## Access control
<a name="acanchor"></a>

Monitoring queries are allowed from
- client that is the _owner_ of the security server
- central monitoring client (if any have been configured)

Central monitoring client is configured using central server admin user interface, see [Admin user interface](#adminanchor).

Attempts to query monitoring data from other clients results in an `AccessDenied` -error.
