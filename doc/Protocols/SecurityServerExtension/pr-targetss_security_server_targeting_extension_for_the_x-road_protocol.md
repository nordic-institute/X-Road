# Security Server Targeting Extension for the X-Road Message Protocol

Version: 1.2  
Doc. ID: PR-TARGETSS

| Date       | Version | Description                         | Author             |
|------------|---------|-------------------------------------|--------------------|
| 02.03.2017 | 1.0     | Initial version                     | Olli Lindgren      |
| 06.03.2018 | 1.1     | Added terms doc reference and link  | Tatu Repo          |
| 17.06.2022 | 1.2     | Update document title               | Petteri Kivimäki   | 

## Table of Contents
<!-- toc -->

- [License](#license)
- [1 Introduction](#1-introduction)
  * [1.1 Terms and abbreviations](#11-terms-and-abbreviations)
  * [1.2 References](#12-references)
- [2 Format of messages](#2-format-of-messages)
  * [2.1 Schema header](#21-schema-header)
  * [2.2 Added `securityServer` element](#22-added-securityserver-element)
  * [2.3 Message headers](#23-message-headers)
- [3 XML Schema for the extension](#3-xml-schema-for-the-extension)
- [4 Examples](#4-examples)
  * [4.1 Request](#41-request)
  * [4.2 Response](#42-response)

<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/.


## 1 Introduction

This specification describes an extension of the X-Road protocol for targeting a message to a specific security server.

The original X-Road message protocol version 4.0 \[[PR-MESS](#Ref_PR-MESS)\] has the SOAP header element `service` to define the recipient of a message.
In a clustered security server configuration, one service can be served from multiple security servers. When X-Road routes the message to such a service,
it picks the target security server based on which server establishes a connection the quickest.
There is no guarantee about the actual target server &mdash; it can be any of the clustered servers. There are use cases,
like environmental monitoring \[[ARC-ENVMON](#Ref_ARC-ENVMON)\], where targeting messages to a specific security server is needed.
Using the `securityServer` element makes this possible.

### 1.1 Terms and abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\] 

### 1.2 References

| Document ID||
| ------------- |-------------|
| <a name="Ref_PR-MESS"></a>\[PR-MESS\] | [X-Road: Message Protocol v4.0](../pr-mess_x-road_message_protocol.md)
| <a name="Ref_ARC-ENVMON"></a>\[ARC-ENVMON\] | [X-Road: Environmental Monitoring Architecture](../../EnvironmentalMonitoring/Monitoring-architecture.md)
| <a name="Ref_TERMS"></a>\[TA-TERMS\] | [X-Road Terms and Abbreviations](../../terms_x-road_docs.md)

## 2 Format of messages

This section describes the XML format for expressing the target security server. The data
structures and elements defined in this section are in the namespace `http://x-road.eu/xsd/xroad.xsd`. This is the same
namespace as defined by the X-Road Message Protocol 4.0 \[[PR-MESS](#Ref_PR-MESS)\] Annex B, XML Schema for Messages. The
schema file can be found at [`http://x-road.eu/xsd/xroad-securityserver.xsd`](http://x-road.eu/xsd/xroad-securityserver.xsd).

Note that at the moment, there is no unifying schema that would combine the message protocol and this extension under
the same namespace. That means there is no single schema that would validate an X-Road message with this extension in use.
It should be possible to validate the messages using a validator that accepts multiple schemas from the same namespace.

In addition, this extension is a candidate for inclusion in the next version of the X-Road message protocol and would then
be part of the actual [`http://x-road.eu/xsd/xroad.xsd`](http://x-road.eu/xsd/xroad.xsd) schema as well as the namespace.

The XML Schema for this extension is listed in the section [XML Schema for the extension](#xml-schema-for-the-extension).


### 2.1 Schema header

The following listing shows the header of the schema definition

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema elementFormDefault="qualified"
        targetNamespace="http://x-road.eu/xsd/xroad.xsd"
        xmlns="http://x-road.eu/xsd/xroad.xsd"
        xmlns:id="http://x-road.eu/xsd/identifiers"
        xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xs:import namespace="http://www.w3.org/XML/1998/namespace"
            schemaLocation="http://www.w3.org/2009/01/xml.xsd"/>
    <xs:import id="id" namespace="http://x-road.eu/xsd/identifiers"
            schemaLocation="http://x-road.eu/xsd/identifiers.xsd"/>
</xs:schema>

```

### 2.2 Added `securityServer` element
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

### 2.3 Message headers
 This section describes the additional SOAP headers that are added by this extension.

|Field | Type | Mandatory/Optional | Description |
|-------------|-------------|-------------|-------------|
| securityServer | XRoadSecurityServerIdentifierType | Optional | The security server this message is for |


## 3 XML Schema for the extension
 ```xml
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema elementFormDefault="qualified"
        targetNamespace="http://x-road.eu/xsd/xroad.xsd"
        xmlns="http://x-road.eu/xsd/xroad.xsd"
        xmlns:id="http://x-road.eu/xsd/identifiers"
        xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xs:import namespace="http://www.w3.org/XML/1998/namespace"
            schemaLocation="http://www.w3.org/2009/01/xml.xsd"/>
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

## 4 Examples
Below are examples from a request and response related to the Environmental Monitoring
\[[ARC-ENVMON](#Ref_ARC-ENVMON)\] service `getSecurityServerMetrics` which uses the `securityServer` element protocol extension.

### 4.1 Request
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
### 4.2 Response
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


