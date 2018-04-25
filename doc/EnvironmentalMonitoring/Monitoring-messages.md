# X-Road: Environmental Monitoring Messages

Version: 1.5  
Doc. ID: PR-ENVMONMES

| Date       | Version     | Description                                                | Author          |
|------------|-------------|------------------------------------------------------------|-----------------|
| 15.12.2015 | 1.0         | Initial version                                            | Ilkka Seppälä   |
| 04.01.2017 | 1.1         | Fix documentation links                                    | Ilkka Seppälä   |
| 20.01.2017 | 1.2         | Added license text, table of contents and version history  | Sami Kallio     |
| 23.02.2017 | 1.3         | Added reference to security server targeting extension     | Olli Lindgren   |
| 24.08.2017 | 1.4         | Added outputSpec parameter to getSecurityServerMetrics     | Tomi Tolvanen   |
| 06.03.2018 | 1.5         | Added terms and abbreviations references, numbering and Introduction chapter structure | Tatu Repo |

## Table of Contents

<!-- toc -->

- [License](#license)
- [1 Introduction](#1-introduction)
  * [1.1 Terms and abbreviations](#11-terms-and-abbreviations)
  * [1.2 References](#12-references)
- [2 Fetching security server metrics](#2-fetching-security-server-metrics)
  * [2.1 Request](#21-request)
  * [2.2 Response](#22-response)
  * [2.3 Response Schema](#23-response-schema)

<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/.

## 1 Introduction

This document describes the request and response messages for environmental monitoring. 

### 1.1 Terms and abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\].

### 1.2 References

| Document ID||
| ------------- |-------------|
| <a name="Ref_PR-TARGETSS"></a>\[PR-TARGETSS\] | [Security server targeting extension for the X-Road message protocol](../Protocols/SecurityServerExtension/pr-targetss_security_server_targeting_extension_for_the_x-road_protocol.md)  |
| <a name="Ref_TERMS"></a>\[TA-TERMS\] | [X-Road Terms and Abbreviations](../terms_x-road_docs.md)


## 2 Fetching security server metrics

### 2.1 Request

Fetching security server metrics uses the X-Road protocol. The `getSecurityServerMetrics` request requires a `securityServer` header element as specified by the security server targeting extension for the X-Road message protocol \[[PR-TARGETSS](#Ref_PR-TARGETSS)\] so that the request can be routed to a specific security server.

`Body` element must contain the `getSecurityServerMetrics` element.

An optional `outputSpec` child element can be used to request a subset of the metrics. The `outputSpec` consists of zero or more `outputField` elements referring to the `name` element of a metric in the `metricSet` named _systemMetrics_. Empty or missing `outputSpec` requests all available metrics. 

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
        <xrd:securityServer id:objectType="SERVER">
            <id:xRoadInstance>fdev</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>1710128-9</id:memberCode>
            <id:serverCode>fdev-ss1.i.palveluvayla.com</id:serverCode>
        </xrd:securityServer>

        <xrd:id>ID11234</xrd:id>
        <xrd:protocolVersion>4.0</xrd:protocolVersion>

    </SOAP-ENV:Header>

    <SOAP-ENV:Body>
        <m:getSecurityServerMetrics>
            <m:outputSpec>
                <m:outputField>OperatingSystem</m:outputField>
                <m:outputField>TotalPhysicalMemory</m:outputField>
            </m:outputSpec>
        </m:getSecurityServerMetrics>
    </SOAP-ENV:Body>

</SOAP-ENV:Envelope>
```

### 2.2 Response

The response `Body` contains one `getSecurityServerMetricsResponse` element which contains one `metricSet` as direct child. The name of the top level set is the security server identifier. The set contains a _proxyVersion_ `stringMetric` and a _systemMetrics_ `metricSet`. The _systemMetrics_ set contains the requested metrics.

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
      <xrd:securityServer id:objectType="SERVER">
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
            </m:metricSet>
         </m:metricSet>
      </m:getSecurityServerMetricsResponse>
   </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```

### 2.3 Response Schema

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
    <xs:complexType name="getSecurityServerMetricsType">
        <xs:sequence/>
    </xs:complexType>
    <xs:element name="getSecurityServerMetrics" type="tns:getSecurityServerMetricsType"/>
</schema>
```
