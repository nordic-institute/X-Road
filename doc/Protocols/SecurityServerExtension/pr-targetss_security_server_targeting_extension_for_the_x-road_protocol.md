# Security server targeting extension for the X-Road message protocol

Version: 1.0  
Doc. ID: PR-TARGETSS

| Date      | Version  | Description                                                                  | Author             |
|-----------|----------|------------------------------------------------------------------------------|--------------------|
| 1.3.2017 | 1.0       | Initial version                                                              | Olli Lindgren     |


## Table of Contents
<!-- toc -->

- [License](#license)
- [Introduction](#introduction)
- [Format of messages](#format-of-messages)
  * [Schema header](#schema-header)
  * [Added `securityServer` element](#added-securityserver-element)
  * [Message headers](#message-headers)
- [XML Schema for the extension](#xml-schema-for-the-extension)
- [Examples](#examples)
  *  [Request](#request)
  * [Response](#response)
- [References](#references)


<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/.


## Introduction

This specification describes an extension of the X-Road protocol for targeting a message to a specific security server.

The original X-Road message protool version 4.0 \[[PR-MESS](#Ref_PR-MESS)\] has the SOAP header element `service` to define the recipient of a message.
In a clustered security server configuration, one service can be served from multiple security servers. When X-Road routes the message to such a service,
it picks the target security server based on which server establishes a connection the quickest.
There is no guarantee about the actual target server &mdash; it can be any of the clustered servers. There are use cases,
like environmental monitoring \[[ARC-ENVMON](#Ref_ARC-ENVMON)\], where targeting messages to a specific security server is needed.

## Format of messages

This section describes the XML format for expressing the target security server. The data
structures and elements defined in this section are in the namespace `http://x-road.eu/xsd/xroad.xsd`.
This is the same namespace as defined by the X-Road Message Protocol 4.0 \[[PR-MESS](#Ref_PR-MESS)\] Annex B, XML Schema for Messages.

The complete XML Schema for this extension is listed in the section [XML Schema for the extension](#xml-schema-for-the-extension).

### Schema header

The following listing shows the header of the schema definition

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema elementFormDefault="qualified"
        targetNamespace="http://x-road.eu/xsd/xroad.xsd"
        xmlns="http://x-road.eu/xsd/xroad.xsd"
        xmlns:id="http://x-road.eu/xsd/identifiers"
        xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xs:import namespace="http://www.w3.org/XML/1998/namespace"
            schemaLocation="http://www.w3.org/2001/xml.xsd"/>
    <xs:import id="id" namespace="http://x-road.eu/xsd/identifiers"
            schemaLocation="http://x-road.eu/xsd/identifiers.xsd"/>
</xs:schema>

```

### Added `securityServer` element
A new `securityServer` element was added to identify the specific target security server.

```xml
 <xs:element name="securityServer" type="id:XRoadSecurityServerIdentifierType">
        <xs:annotation>
            <xs:documentation>Identifies a specific security server</xs:documentation>
        </xs:annotation>
    </xs:element>
```
  The element is of the type `XRoadSecurityServerIdentifierType`, which is one of the identifiers already defined
  in the X-Road Message Protocol v 4.0 \[[PR-MESS](#Ref_PR-MESS)\] section 2.1. The whole XML schema for the identifier types is defined in
  Annex A of the same document. The relevant part is listed below for convenience.

```xml
<xs:complexType name="XRoadSecurityServerIdentifierType">
    <xs:complexContent>
        <xs:restriction base="XRoadIdentifierType">
            <x:sequence>
                <xs:element ref="xRoadInstance"/>
                <xs:element ref="memberClass"/>
                <xs:element ref="memberCode"/>
                <xs:element ref="serverCode"/>
            </xs:sequence>
            <xs:attribute ref="objectType" use="required" fixed="SERVER"/>
        </xs:restriction>
    </xs:complexContent>
</xs:complexType>
```

### Message headers
 This section describes the additional SOAP headers that are added by this extension.

|Field | Type | Mandatory/Optional | Description |
|-------------|-------------|-------------|-------------|
| securityServer | XRoadSecurityServerIdentifierType | Optional | The security server this message is for |


## XML Schema for the extension
 ```xml
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema elementFormDefault="qualified"
        targetNamespace="http://x-road.eu/xsd/xroad.xsd"
        xmlns="http://x-road.eu/xsd/xroad.xsd"
        xmlns:id="http://x-road.eu/xsd/identifiers"
        xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xs:import namespace="http://www.w3.org/XML/1998/namespace"
            schemaLocation="http://www.w3.org/2001/xml.xsd"/>
    <xs:import id="id" namespace="http://x-road.eu/xsd/identifiers"
            schemaLocation="http://x-road.eu/xsd/identifiers.xsd"/>

    <!-- Header elements -->
    <xs:element name="securityServer" type="id:XRoadSecurityServerIdentifierType">
        <xs:annotation>
            <xs:documentation>Identifies security server</xs:documentation>
        </xs:annotation>
    </xs:element>
</xs:schema>
```

## Examples
Below are examples from a request and response related to the Environmental Monitoring
\[[ARC-ENVMON](#Ref_ARC-ENVMON)\] service `getSecurityServerMetrics` which uses the `securityServer` element protocol extension.

### Request
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
        <m:getSecurityServerMetrics/>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```
### Response
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

## References

| Code||
| ------------- |-------------|
| <a name="Ref_PR-MESS"></a>\[PR-MESS\] | Cybernetica AS.X-Road: Message Protocol v4.0      |
| <a name="Ref_ARC-ENVMON"></a>\[ARC-ENVMON\] | X-Road: Environmental Monitoring Architecture |

