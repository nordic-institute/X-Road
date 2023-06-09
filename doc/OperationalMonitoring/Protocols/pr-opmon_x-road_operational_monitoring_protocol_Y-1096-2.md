# X-Road: Operational Monitoring Protocol <!-- omit in toc -->

**Technical Specification**

Version: 1.2  
Doc. ID: PR-OPMON

| Date       | Version | Description                                                          | Author            |
|------------|---------|----------------------------------------------------------------------|-------------------|
|            | 0.2     | Initial version                                                      |                   |
| 23.01.2017 | 0.3     | Added license text, table of contents and version history            | Sami Kallio       |
| 05.03.2018 | 0.4     | Added terms and abbreviations reference                              | Tatu Repo         |
| 04.12.2018 | 0.5     | More detailed descriptions for *[request/response][In/Out]Ts* fields | Cybernetica AS    |
| 18.02.2019 | 0.6     | Example response updated: added xRequestId                           | Caro Hautamäki    |
| 23.05.2019 | 0.7     | Add info about status_code, request_rest_size, response_rest_size    | Tapio Jaakkola    |
| 12.12.2019 | 1.0     | Update the protocol to the next major version                        | Ilkka Seppälä     |
| 10.05.2023 | 1.1     | Security Categories removed.                                         | Justas Samuolis   |
| 01.06.2023 | 1.2     | Update references                                                    | Petteri Kivimäki  |

## Table of Contents <!-- omit in toc -->

<!-- toc -->

- [License](#license)
- [1 Introduction](#1-introduction)
    - [1.1 Terms and abbreviations](#11-terms-and-abbreviations)
    - [1.2 References](#12-references)
- [2 Retrieving Operational Data of Security Server](#2-retrieving-operational-data-of-security-server)
- [3 Retrieving Health Data of Security Server](#3-retrieving-health-data-of-security-server)
- [Annex A WSDL for Operational Monitoring Messages](#annex-a-wsdl-for-operational-monitoring-messages)
- [Annex B JSON-Schema for Payload of getSecurityServerOperationalData Response](#annex-b-json-schema-for-payload-of-getsecurityserveroperationaldata-response)
- [Annex C Example Messages](#annex-c-example-messages)
  - [C.1 getSecurityServerOperationalData Request](#c1-getsecurityserveroperationaldata-request)
  - [C.2 getSecurityServerOperationalData Response](#c2-getsecurityserveroperationaldata-response)
    - [C.2.1 Example JSON-Payload of getSecurityServerOperationalData Response](#c21-example-json-payload-of-getsecurityserveroperationaldata-response)
  - [C.3 getSecurityServerHealthData Request](#c3-getsecurityserverhealthdata-request)
  - [C.4 getSecurityServerHealthData Response](#c4-getsecurityserverhealthdata-response)

<!-- tocstop -->

# License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/.

# 1 Introduction

This specification describes services that can be used by X-Road participants to gather operational monitoring information of the security servers. The operational monitoring information contains data about request exchange (such as which services have been called, how many times, what was the size of the response, etc.) of the security servers. The X-Road operational monitoring protocol is intended to support external monitoring systems and other software that can monitor service level agreements, make service statistics, etc.

The operational monitoring services are the following:
* *getSecurityServerOperationalData* - downloading operational data of the specified time period of the security server.
* *getSecurityServerHealthData* - downloading health data of the security server.

The operational monitoring services are implemented as standard X-Road services (see \[[PR-MESS](#PR-MESS)\] for detailed description of the protocol) that are offered by the owner of the security servers.

This protocol builds on existing transport and message encoding mechanisms. Therefore, this specification does not cover the technical details and error conditions related to making HTTP(S) requests together with processing MIME-encoded messages. These concerns are discussed in detail in their respective standards.

The low-level technical details of the operational monitoring services are specified using the WSDL \[[WSDL](#WSDL)\] syntax. See \[[Annex A](#AnnexA)\] for operational monitoring services WSDL file.

Chapters 2 and 3 together with annexes \[[Annex A](#AnnexA)\] and \[[Annex B](AnnexB)\] contain normative information. All the other sections are informative in nature. All the references are normative.

This specification does not include option for partially implementing the protocol – the conformant implementation must implement the entire specification.

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in this document (in uppercase, as shown) are to be interpreted as described in \[[RFC2119](#RFC2119)\].

### 1.1 Terms and abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\].

### 1.2 References

<a name="PR-MESS"></a>**PR-MESS** -- X-Road: Message Transport Protocol v4.0. Document ID: [PR-MESS](../../Protocols/pr-mess_x-road_message_protocol.md).  
<a name="WSDL"></a>**WSDL** -- Web Services Description Language (WSDL) 1.1. World Wide Web Consortium. 15 March 2001, https://www.w3.org/TR/2001/NOTE-wsdl-20010315  
<a name="SWAREF"></a>**SWAREF** -- Attachments Profile Version 1.0, http://www.ws-i.org/Profiles/AttachmentsProfile-1.0-2004-08-24.html  
<a name="RFC1952"></a>**RFC1952** -- GZIP file format specification version 4.3, https://tools.ietf.org/html/rfc1952  
<a name="RFC2119"></a>**RFC2119** -- Key words for use in RFCs to Indicate Requirement Levels. Request for Comments 2119, Internet Engineering Task Force, March 1997, https://www.ietf.org/rfc/rfc2119.txt  
<a name="Ref_TERMS" class="anchor"></a>**TA-TERMS** -- X-Road Terms and Abbreviations. Document ID: [TA-TERMS](../../terms_x-road_docs.md).

# 2 Retrieving Operational Data of Security Server

Security server clients can retrieve operational data of the specified time period of the security server. Method is invoked as regular X-Road service.

The *service* SOAP header MUST contain the identifier of the target service provider (owner of the security server) and the value of the *serviceCode* element MUST be *"getSecurityServerOperationalData"*. Additionally *securityServer* SOAP header SHOULD be used to identify the security server that is the target of the request. This is needed to uniquely determine the target security server in a clustered security server configuration. The SOAP header *securityServer* MUST be used in case the sender of the request is the owner of target security server. This header is used to perform correct authentication of the sender.  

The body of the request MUST contain an XML element *getSecurityServerOperationalData* which contains the following XML elements.
* *searchCriteria* (mandatory) -- Determines the search criteria of the requested monitoring data records. This element contains the following XML elements.
 * *recordsFrom* (mandatory) -- Unix timestamp in seconds to determine the beginning of the time period of the monitoring data records. The beginning timestamp MUST be less than the system value of *current time - configured offset seconds* (all of the operational data before that system timestamp SHOULD be committed. By default 60 seconds are used for the offset).
 * *recordsTo* (mandatory) -- Unix timestamp in seconds to determine the end (inclusively) of the time period of the monitoring data records. If the end timestamp is bigger or equal to the system value of *current time - configured offset seconds* then *recordsTo* value is shifted to the value of *current time - configured offset seconds - 1* (it is allowed to subtract a bigger time buffer to ensure that all the operational data of the specified time period are committed. By default 60 seconds are used for the offset).  
 * *client* (optional) -- Determines the client identifier of the service provider in the monitoring data records.  
* *outputSpec* (optional) -- A sequence of optional *outputField* elements that determines the set of the requested operational data record fields in the response payload. If omitted or empty sequence, all record fields MUST be included into the response payload. The possible output field values are the following:

 * *monitoringDataTs*
 * *securityServerInternalIp*
 * *securityServerType*
 * *requestInTs*
 * *requestOutTs*
 * *responseInTs*
 * *responseOutTs*
 * *clientXRoadInstance*
 * *clientMemberClass*
 * *clientMemberCode*
 * *clientSubsystemCode*
 * *serviceXRoadInstance*
 * *serviceMemberClass*
 * *serviceMemberCode*
 * *serviceSubsystemCode*
 * *serviceCode*
 * *serviceVersion*
 * *representedPartyClass*
 * *representedPartyCode*
 * *messageId*
 * *messageUserId*
 * *messageIssue*
 * *messageProtocolVersion*
 * *clientSecurityServerAddress*
 * *serviceSecurityServerAddress*
 * *requestSize*
 * *requestMimeSize*
 * *requestAttachmentCount*
 * *responseSize*
 * *responseMimeSize*
 * *responseAttachmentCount*
 * *succeeded*
 * *serviceType*
 * *faultCode*
 * *faultString*
 * *statusCode*
 

The fields are described in the JSON-schema of the response payload \[[Annex B](#AnnexB)\].

The XML schema fragment of the operational data request body is shown below. For clarity, documentation in the schema fragment is omitted.

```xml
<xs:complexType name="GetSecurityServerOperationalDataType">
  <xs:sequence>
    <xs:element name="searchCriteria" type="SearchCriteriaType" />
    <xs:element name="outputSpec" type="OutputSpecType" minOccurs="0" />
  </xs:sequence>
</xs:complexType>
<xs:complexType name="SearchCriteriaType">
  <xs:sequence>
    <xs:element name="recordsFrom" type="xs:long" />
    <xs:element name="recordsTo" type="xs:long" />
    <xs:element name="client" type="id:XRoadClientIdentifierType" minOccurs="0" />
  </xs:sequence>
</xs:complexType>
<xs:complexType name="OutputSpecType">
  <xs:sequence>
    <xs:element name="outputField" type="xs:string"
        minOccurs="0" maxOccurs="unbounded" />
  </xs:sequence>
</xs:complexType>
```

The example request message is presented in \[[Annex C.1](#AnnexC.1)\].

The response MUST be MIME multipart message with attachment using swaRef \[[SWAREF](#SWAREF)\]. The response MUST contain the following MIME parts.

1. X-Road SOAP response message. The message MUST contain the regular X-Road headers and the body MUST contain the following elements.
 * *recordsCount* (mandatory) -- Number of records in the payload.
 * *records* (mandatory) -- The reference (CID URI) to the attachment (MIME part) containing the operational data records.
 * *nextRecordsFrom* (optional) -- This element MUST be included in case operational data records do not fit into the response (size limitation) and/or in case the *recordsTo* timestamp in the search criteria was actually shifted earlier. The value MUST be the proper Unix timestamp in seconds for the search criteria element *recordsFrom* of the next sequential query.

 The content type of this part MUST be *text/xml*.

2. Operational data (payload). This MIME part MUST contain queried operational data records in JSON format and compressed (GZIP \[[RFC1952](#RFC1952)\]) . The content type of this part MUST be *application/gzip*. The JSON-Schema for payload is described in \[[Annex B](#AnnexB)\].

The XML schema fragment of the operational data response body is shown below. For clarity, documentation in the schema fragment is omitted.

```xml
<xs:complexType name="GetSecurityServerOperationalDataResponseType">
  <xs:sequence>
    <xs:element name="recordsCount" type="xs:int" />
    <xs:element name="records" type="ref:swaRef" />
    <xs:element name="nextRecordsFrom" type="xs:long" minOccurs="0" />
  </xs:sequence>
</xs:complexType>
```

The example response message is presented in \[[Annex C.2](#AnnexC.2)\].

# 3 Retrieving Health Data of Security Server

Security server clients can retrieve health data of the specified security server. Method is invoked as regular X-Road service.

The *service* SOAP header MUST contain the identifier of the target service provider (owner of the security server) and the value of the *serviceCode* element MUST be *"getSecurityServerHealthData"*. Additionally *securityServer* SOAP header SHOULD contain the identifier of the security server retrieving data from. The last one determines the security server uniquely in a clustered security server configuration.

The body of the request MUST contain an XML element *getSecurityServerHealthData*.
This element MAY contain XML element *filterCriteria* to determine a client (service provider). In this case filtering health data by a client MUST be performed.

The XML schema fragment of the health data request body is shown below. For clarity, documentation in the schema fragment is omitted.

```xml
<xs:complexType name="GetSecurityServerHealthDataType">
  <xs:sequence>
    <xs:element name="filterCriteria" type="FilterCriteriaType" minOccurs="0" />
  </xs:sequence>
</xs:complexType>
<xs:complexType name="FilterCriteriaType">
  <xs:sequence>
  <xs:element name="client" type="id:XRoadClientIdentifierType" minOccurs="0" />
  </xs:sequence>
</xs:complexType>    
```

The example request message is presented in \[[Annex C.3](#AnnexC.3)\].

The response message MUST contain health data of the queried security server:
 * *monitoringStartupTimestamp* -- The Unix timestamp in milliseconds when the monitoring system was started.
 * *statisticsPeriodSeconds* -- Duration of the statistics period in seconds.
 * *servicesEvents* -- Health data of all (filtered) services of the security server.

  The XML element *servicesEvents* MUST contain list of items (*serviceEvents*) representing service statistics, where item contains following elements.
  * *service* (mandatory) -- The service identifier.
  * *lastSuccessfulRequestTimestamp* (optional) -- The timestamp of the last successful request (Unix timestamp in milliseconds).
  * *lastUnsuccessfulRequestTimestamp* (optional)-- The timestamp of the last unsuccessful request (Unix timestamp in milliseconds).
  * *serviceType* (optional) -- The type of the service.
  * *lastPeriodStatistics* (optional) -- The statistics of the requests occurred during the last period containing the following elements.
    * *successfulRequestCount* (mandatory) -- The number of successful requests occurred during the last period.
    * *unsuccessfulRequestCount* (mandatory) -- The number of unsuccessful requests occurred during the last period.
    * *requestMinDuration* (optional) -- The minimum duration of the request in milliseconds.
    * *requestAverageDuration* (optional) -- The average duration of the request in milliseconds.
    * *requestMaxDuration* (optional) -- The maximum duration of the request in milliseconds.
    * *requestDurationStdDev* (optional) -- The standard deviation of the duration of the requests.
    * *requestMinSize* (optional) -- The minimum message size of the request in bytes.
    * *requestAverageSize* (optional) -- The average message size of the request in bytes.
    * *requestMaxSize* (optional) -- The maximum message size of the request in bytes.
    * *requestSizeStdDev* (optional) -- The standard deviation of the message size of the request.
    * *responseMinSize* (optional) -- The minimum message size of the response in bytes.
    * *responseAverageSize* (optional) -- The average message size of the response in bytes.
    * *responseMaxSize* (optional) -- The maximum message size of the response in bytes.
    * *responseSizeStdDev* (optional) -- The standard deviation of the message size of the response.       

The XML schema fragment of the health data response body is shown below. For clarity, documentation in the schema fragment is omitted.

```xml
<xs:complexType name="GetSecurityServerHealthDataResponseType">
  <xs:sequence>
    <xs:element name="monitoringStartupTimestamp" type="xs:long" />
    <xs:element name="statisticsPeriodSeconds" type="xs:int" />
    <xs:element name="servicesEvents" type="ServicesEventsType" />
  </xs:sequence>
</xs:complexType>
<xs:complexType name="ServicesEventsType">
  <xs:sequence>
    <xs:element name="serviceEvents" type="ServiceEventsType" minOccurs="0"
        maxOccurs="unbounded" />
  </xs:sequence>
</xs:complexType>
<xs:complexType name="ServiceEventsType">
  <xs:sequence>
    <xs:element name="service" type="id:XRoadServiceIdentifierType" />
    <xs:element name="lastSuccessfulRequestTimestamp" type="xs:long"
        minOccurs="0" />
    <xs:element name="lastUnsuccessfulRequestTimestamp" type="xs:long"
        minOccurs="0" />
    <xs:element name="serviceType" type="xs:string" minOccurs="0" />
    <xs:element name="lastPeriodStatistics" type="LastPeriodStatisticsType" />
  </xs:sequence>
</xs:complexType>
<xs:complexType name="LastPeriodStatisticsType">
  <xs:sequence>
    <xs:element name="successfulRequestCount" type="xs:int" />
    <xs:element name="unsuccessfulRequestCount" type="xs:int" />
    <xs:element name="requestMinDuration" type="xs:long" minOccurs="0" />
    <xs:element name="requestAverageDuration" type="xs:double" minOccurs="0" />
    <xs:element name="requestMaxDuration" type="xs:long" minOccurs="0" />
    <xs:element name="requestDurationStdDev" type="xs:double" minOccurs="0" />
    <xs:element name="requestMinSize" type="xs:long" minOccurs="0" />
    <xs:element name="requestAverageSize" type="xs:double" minOccurs="0" />
    <xs:element name="requestMaxSize" type="xs:long" minOccurs="0" />
    <xs:element name="requestSizeStdDev" type="xs:double" minOccurs="0" />
    <xs:element name="responseMinSize" type="xs:long" minOccurs="0" />
    <xs:element name="responseAverageSize" type="xs:double" minOccurs="0" />
    <xs:element name="responseMaxSize" type="xs:long" minOccurs="0" />
    <xs:element name="responseSizeStdDev" type="xs:double" minOccurs="0" />
  </xs:sequence>
</xs:complexType>
```

The example response message is presented in \[[Annex C.4](#AnnexC.4)\].

<a name="AnnexA"/></a>
# Annex A WSDL for Operational Monitoring Messages

The XML-schema for operational monitoring messages is located in the file *src/op-monitor-daemon/src/main/resources/op-monitoring.xsd* of the X-Road source code.

The WSDL is located in the file *src/op-monitor-daemon/src/main/resources/op-monitoring.wsdl* of the X-Road source code.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<wsdl:definitions targetNamespace="http://op-monitor.x-road.eu/"
        xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/"
        xmlns:opm="http://x-road.eu/xsd/op-monitoring.xsd"
        xmlns:xrd="http://x-road.eu/xsd/xroad.xsd"
        xmlns:id="http://x-road.eu/xsd/identifiers"
        xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
        xmlns:mime="http://schemas.xmlsoap.org/wsdl/mime/"
        xmlns:tns="http://op-monitor.x-road.eu/">
    <wsdl:types>
        <xs:schema elementFormDefault="qualified"
                targetNamespace="http://x-road.eu/xsd/identifiers"
                xmlns="http://x-road.eu/xsd/identifiers"
                xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <xs:complexType name="XRoadIdentifierType">
                <xs:annotation>
                    <xs:documentation>Globally unique identifier in the X-Road
                        system. Identifier consists of object type specifier and
                        list of hierarchical codes (starting with code that
                        identifiers the X-Road instance).
                    </xs:documentation>
                </xs:annotation>
                <xs:sequence>
                    <xs:element minOccurs="0" ref="xRoadInstance"/>
                    <xs:element minOccurs="0" ref="memberClass"/>
                    <xs:element minOccurs="0" ref="memberCode"/>
                    <xs:element minOccurs="0" ref="subsystemCode"/>
                    <xs:element minOccurs="0" ref="groupCode"/>
                    <xs:element minOccurs="0" ref="serviceCode"/>
                    <xs:element minOccurs="0" ref="serviceVersion"/>
                    <xs:element minOccurs="0" ref="serverCode"/>
                </xs:sequence>
                <xs:attribute ref="objectType" use="required"/>
            </xs:complexType>
            <xs:simpleType name="XRoadObjectType">
                <xs:annotation>
                    <xs:documentation>Enumeration for X-Road identifier types.
                    </xs:documentation>
                </xs:annotation>
                <xs:restriction base="xs:string">
                    <xs:enumeration value="MEMBER"/>
                    <xs:enumeration value="SUBSYSTEM"/>
                    <xs:enumeration value="SERVER"/>
                    <xs:enumeration value="GLOBALGROUP"/>
                    <xs:enumeration value="LOCALGROUP"/>
                    <xs:enumeration value="SERVICE"/>
                </xs:restriction>
            </xs:simpleType>
            <xs:element name="xRoadInstance" type="xs:string">
                <xs:annotation>
                    <xs:documentation>Identifies the X-Road instance. This field
                        is applicable to all identifier types.
                    </xs:documentation>
                </xs:annotation>
            </xs:element>
            <xs:element name="memberClass" type="xs:string">
                <xs:annotation>
                    <xs:documentation>Type of the member (company, government
                        institution, private person, etc.)
                    </xs:documentation>
                </xs:annotation>
            </xs:element>
            <xs:element name="memberCode" type="xs:string">
                <xs:annotation>
                    <xs:documentation>Code that uniquely identifies a member of
                        given member type.
                    </xs:documentation>
                </xs:annotation>
            </xs:element>
            <xs:element name="subsystemCode" type="xs:string">
                <xs:annotation>
                    <xs:documentation>Code that uniquely identifies a subsystem
                        of given X-Road member.
                    </xs:documentation>
                </xs:annotation>
            </xs:element>
            <xs:element name="groupCode" type="xs:string">
                <xs:annotation>
                    <xs:documentation>Code that uniquely identifies a global
                        group in
                        given X-Road instance.
                    </xs:documentation>
                </xs:annotation>
            </xs:element>
            <xs:element name="serviceCode" type="xs:string">
                <xs:annotation>
                    <xs:documentation>Code that uniquely identifies a service
                        offered by given X-Road member or subsystem.
                    </xs:documentation>
                </xs:annotation>
            </xs:element>
            <xs:element name="serviceVersion" type="xs:string">
                <xs:annotation>
                    <xs:documentation>Version of the service.</xs:documentation>
                </xs:annotation>
            </xs:element>
            <xs:element name="serverCode" type="xs:string">
                <xs:annotation>
                    <xs:documentation>Code that uniquely identifies security
                        server offered by a given X-Road member or subsystem.
                    </xs:documentation>
                </xs:annotation>
            </xs:element>
            <xs:attribute name="objectType" type="XRoadObjectType"/>
            <xs:complexType name="XRoadClientIdentifierType">
                <xs:complexContent>
                    <xs:restriction base="XRoadIdentifierType">
                        <xs:sequence>
                            <xs:element ref="xRoadInstance"/>
                            <xs:element ref="memberClass"/>
                            <xs:element ref="memberCode"/>
                            <xs:element minOccurs="0" ref="subsystemCode"/>
                        </xs:sequence>
                        <xs:attribute ref="objectType" use="required"/>
                    </xs:restriction>
                </xs:complexContent>
            </xs:complexType>
            <xs:complexType name="XRoadServiceIdentifierType">
                <xs:complexContent>
                    <xs:restriction base="XRoadIdentifierType">
                        <xs:sequence>
                            <xs:element ref="xRoadInstance"/>
                            <xs:element ref="memberClass"/>
                            <xs:element ref="memberCode"/>
                            <xs:element minOccurs="0" ref="subsystemCode"/>
                            <xs:element ref="serviceCode"/>
                            <xs:element minOccurs="0" ref="serviceVersion"/>
                        </xs:sequence>
                        <xs:attribute ref="objectType" use="required"
                                fixed="SERVICE"/>
                    </xs:restriction>
                </xs:complexContent>
            </xs:complexType>
            <xs:complexType name="XRoadSecurityServerIdentifierType">
                <xs:complexContent>
                    <xs:restriction base="XRoadIdentifierType">
                        <xs:sequence>
                            <xs:element ref="xRoadInstance"/>
                            <xs:element ref="memberClass"/>
                            <xs:element ref="memberCode"/>
                            <xs:element ref="serverCode"/>
                        </xs:sequence>
                        <xs:attribute ref="objectType" use="required"
                                fixed="SERVER"/>
                    </xs:restriction>
                </xs:complexContent>
            </xs:complexType>
            <xs:complexType name="XRoadGlobalGroupIdentifierType">
                <xs:complexContent>
                    <xs:restriction base="XRoadIdentifierType">
                        <xs:sequence>
                            <xs:element ref="xRoadInstance"/>
                            <xs:element ref="groupCode"/>
                        </xs:sequence>
                        <xs:attribute ref="objectType" use="required"
                                fixed="GLOBALGROUP"/>
                    </xs:restriction>
                </xs:complexContent>
            </xs:complexType>
            <xs:complexType name="XRoadLocalGroupIdentifierType">
                <xs:complexContent>
                    <xs:restriction base="XRoadIdentifierType">
                        <xs:sequence>
                            <xs:element ref="groupCode"/>
                        </xs:sequence>
                        <xs:attribute ref="objectType" use="required"
                                fixed="LOCALGROUP"/>
                    </xs:restriction>
                </xs:complexContent>
            </xs:complexType>
        </xs:schema>
        <xs:schema elementFormDefault="qualified"
                targetNamespace="http://x-road.eu/xsd/xroad.xsd"
                xmlns="http://x-road.eu/xsd/xroad.xsd"
                xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <xs:element name="version" type="xs:string"/>

            <!-- Header elements -->
            <xs:element name="client" type="id:XRoadClientIdentifierType"/>
            <xs:element name="service" type="id:XRoadServiceIdentifierType"/>
            <xs:element name="securityServer"
                    type="id:XRoadSecurityServerIdentifierType"/>
            <xs:element name="userId" type="xs:string"/>
            <xs:element fixed="4.0" name="protocolVersion" type="xs:string"/>
            <xs:element name="id" type="xs:string"/>

            <!-- Elements describing other elements and operations-->
            <xs:element name="title">
                <xs:annotation>
                    <xs:documentation>Title</xs:documentation>
                </xs:annotation>
                <xs:complexType>
                    <xs:simpleContent>
                        <xs:extension base="xs:string">
                            <xs:attribute default="en" ref="xml:lang"/>
                        </xs:extension>
                    </xs:simpleContent>
                </xs:complexType>
            </xs:element>
            <xs:element name="notes">
                <xs:annotation>
                    <xs:documentation>Notes for user</xs:documentation>
                </xs:annotation>
                <xs:complexType>
                    <xs:simpleContent>
                        <xs:extension base="xs:string">
                            <xs:attribute default="en" ref="xml:lang"/>
                        </xs:extension>
                    </xs:simpleContent>
                </xs:complexType>
            </xs:element>
        </xs:schema>
        <xs:schema elementFormDefault="qualified"
                targetNamespace="http://x-road.eu/xsd/op-monitoring.xsd"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns="http://x-road.eu/xsd/op-monitoring.xsd"
                xmlns:id="http://x-road.eu/xsd/identifiers"
                xmlns:ref="http://ws-i.org/profiles/basic/1.1/xsd">
            <xs:import namespace="http://ws-i.org/profiles/basic/1.1/xsd"
                    schemaLocation="http://ws-i.org/profiles/basic/1.1/swaref.xsd"
                    id="ref"/>
            <xs:complexType name="GetSecurityServerOperationalDataType">
                <xs:annotation>
                    <xs:documentation>Security server operational data request
                    </xs:documentation>
                </xs:annotation>
                <xs:sequence>
                    <xs:element name="searchCriteria" type="SearchCriteriaType">
                        <xs:annotation>
                            <xs:documentation>Search criteria</xs:documentation>
                        </xs:annotation>
                    </xs:element>
                    <xs:element name="outputSpec" type="OutputSpecType"
                            minOccurs="0">
                        <xs:annotation>
                            <xs:documentation>The set of the requested
                                operational data fields
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                </xs:sequence>
            </xs:complexType>
            <xs:complexType name="SearchCriteriaType">
                <xs:sequence>
                    <xs:element name="recordsFrom" type="xs:long">
                        <xs:annotation>
                            <xs:documentation>The beginning of the time interval
                                of requested operational data (Unix timestamp in
                                seconds)
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                    <xs:element name="recordsTo" type="xs:long">
                        <xs:annotation>
                            <xs:documentation>The end of the time interval of
                                requested operational data (Unix timestamp in
                                seconds)
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                    <xs:element name="client"
                            type="id:XRoadClientIdentifierType"
                            minOccurs="0">
                        <xs:annotation>
                            <xs:documentation>Client identifier of data exchange
                                partner to use for filtering out records
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                </xs:sequence>
            </xs:complexType>
            <xs:complexType name="OutputSpecType">
                <xs:sequence>
                    <xs:element name="outputField" type="xs:string"
                            minOccurs="0" maxOccurs="unbounded">
                        <xs:annotation>
                            <xs:documentation>Name of the operational data
                                field
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                </xs:sequence>
            </xs:complexType>
            <xs:complexType name="GetSecurityServerOperationalDataResponseType">
                <xs:annotation>
                    <xs:documentation>Security server operational data
                        response
                    </xs:documentation>
                </xs:annotation>
                <xs:sequence>
                    <xs:element name="recordsCount" type="xs:int">
                        <xs:annotation>
                            <xs:documentation>The number of records included in
                                the response
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                    <xs:element name="records" type="ref:swaRef">
                        <xs:annotation>
                            <xs:documentation>The reference to the attachment
                                containing the records
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                    <xs:element name="nextRecordsFrom" type="xs:long"
                            minOccurs="0">
                        <xs:annotation>
                            <xs:documentation>Unix timestamp in seconds to use
                                for field recordsFrom of the next query. This
                                element is present in case the size of the
                                response has been limited or the timestamp of
                                the field recordsTo was in the future.
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                </xs:sequence>
            </xs:complexType>
            <xs:complexType name="FilterCriteriaType">
                <xs:sequence>
                    <xs:element name="client"
                            type="id:XRoadClientIdentifierType"
                            minOccurs="0">
                        <xs:annotation>
                            <xs:documentation>Client identifier of data exchange
                                partner to use for filtering out services
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                </xs:sequence>
            </xs:complexType>
            <xs:complexType name="GetSecurityServerHealthDataType">
                <xs:annotation>
                    <xs:documentation>Security server health data request
                    </xs:documentation>
                </xs:annotation>
                <xs:sequence>
                    <xs:element name="filterCriteria" type="FilterCriteriaType"
                            minOccurs="0">
                        <xs:annotation>
                            <xs:documentation>Filter criteria</xs:documentation>
                        </xs:annotation>
                    </xs:element>
                </xs:sequence>
            </xs:complexType>
            <xs:complexType name="GetSecurityServerHealthDataResponseType">
                <xs:annotation>
                    <xs:documentation>Security server health data response
                    </xs:documentation>
                </xs:annotation>
                <xs:sequence>
                    <xs:element name="monitoringStartupTimestamp"
                            type="xs:long">
                        <xs:annotation>
                            <xs:documentation>The Unix timestamp in milliseconds
                                when the operational monitoring daemon was
                                started
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                    <xs:element name="statisticsPeriodSeconds" type="xs:int">
                        <xs:annotation>
                            <xs:documentation>Duration of the statistics period
                                in seconds
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                    <xs:element name="servicesEvents" type="ServicesEventsType">
                        <xs:annotation>
                            <xs:documentation>Health data of all services
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                </xs:sequence>
            </xs:complexType>
            <xs:complexType name="ServicesEventsType">
                <xs:sequence>
                    <xs:element name="serviceEvents" type="ServiceEventsType"
                            minOccurs="0" maxOccurs="unbounded">
                        <xs:annotation>
                            <xs:documentation>Health data of one service
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                </xs:sequence>
            </xs:complexType>
            <xs:complexType name="ServiceEventsType">
                <xs:sequence>
                    <xs:element name="service"
                            type="id:XRoadServiceIdentifierType">
                        <xs:annotation>
                            <xs:documentation>The service identifier
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                    <xs:element name="lastSuccessfulRequestTimestamp"
                            type="xs:long" minOccurs="0">
                        <xs:annotation>
                            <xs:documentation>The timestamp of the last
                                successful request (Unix timestamp in
                                milliseconds)
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                    <xs:element name="lastUnsuccessfulRequestTimestamp"
                            type="xs:long"
                            minOccurs="0">
                        <xs:annotation>
                            <xs:documentation>The timestamp of the last
                                unsuccessful request (Unix timestamp in
                                milliseconds)
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                    <xs:element name="serviceType" type="xs:string" minOccurs="0">
                        <xs:annotation>
                            <xs:documentation>Type of the service</xs:documentation>
                        </xs:annotation>
                    </xs:element>
                    <xs:element name="lastPeriodStatistics"
                            type="LastPeriodStatisticsType">
                        <xs:annotation>
                            <xs:documentation>The statistics of the requests
                                occurred during the last period
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                </xs:sequence>
            </xs:complexType>
            <xs:complexType name="LastPeriodStatisticsType">
                <xs:sequence>
                    <xs:element name="successfulRequestCount" type="xs:int">
                        <xs:annotation>
                            <xs:documentation>The number of successful requests
                                occurred during the last period
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                    <xs:element name="unsuccessfulRequestCount" type="xs:int">
                        <xs:annotation>
                            <xs:documentation>The number of unsuccessful
                                requests occurred during the last period
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                    <xs:element name="requestMinDuration" type="xs:long"
                            minOccurs="0">
                        <xs:annotation>
                            <xs:documentation>The minimum duration of the
                                request in milliseconds
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                    <xs:element name="requestAverageDuration" type="xs:double"
                            minOccurs="0">
                        <xs:annotation>
                            <xs:documentation>The average duration of the
                                request in milliseconds
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                    <xs:element name="requestMaxDuration" type="xs:long"
                            minOccurs="0">
                        <xs:annotation>
                            <xs:documentation>The maximum duration of the
                                request in milliseconds
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                    <xs:element name="requestDurationStdDev" type="xs:double"
                            minOccurs="0">
                        <xs:annotation>
                            <xs:documentation>The standard deviation of the
                                duration of the requests
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                    <xs:element name="requestMinSize" type="xs:long"
                            minOccurs="0">
                        <xs:annotation>
                            <xs:documentation>The minimum message size of
                                the request in bytes
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                    <xs:element name="requestAverageSize" type="xs:double"
                            minOccurs="0">
                        <xs:annotation>
                            <xs:documentation>The average message size of
                                the request in bytes
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                    <xs:element name="requestMaxSize" type="xs:long"
                            minOccurs="0">
                        <xs:annotation>
                            <xs:documentation>The maximum message size of
                                the request in bytes
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                    <xs:element name="requestSizeStdDev" type="xs:double"
                            minOccurs="0">
                        <xs:annotation>
                            <xs:documentation>The standard deviation of the
                                message size of the requests
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                    <xs:element name="responseMinSize" type="xs:long"
                            minOccurs="0">
                        <xs:annotation>
                            <xs:documentation>The minimum message size of
                                the response in bytes
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                    <xs:element name="responseAverageSize" type="xs:double"
                            minOccurs="0">
                        <xs:annotation>
                            <xs:documentation>The average message size of
                                the response in bytes
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                    <xs:element name="responseMaxSize" type="xs:long"
                            minOccurs="0">
                        <xs:annotation>
                            <xs:documentation>The maximum message size of
                                the response in bytes
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                    <xs:element name="responseSizeStdDev" type="xs:double"
                            minOccurs="0">
                        <xs:annotation>
                            <xs:documentation>The standard deviation of the
                                message size of the responses
                            </xs:documentation>
                        </xs:annotation>
                    </xs:element>
                </xs:sequence>
            </xs:complexType>
            <xs:element name="getSecurityServerOperationalData"
                    type="GetSecurityServerOperationalDataType"/>
            <xs:element name="getSecurityServerOperationalDataResponse"
                    type="GetSecurityServerOperationalDataResponseType"/>
            <xs:element name="getSecurityServerHealthData"
                    type="GetSecurityServerHealthDataType"/>
            <xs:element name="getSecurityServerHealthDataResponse"
                    type="GetSecurityServerHealthDataResponseType"/>
        </xs:schema>
    </wsdl:types>

    <wsdl:message name="getSecurityServerOperationalDataResponse">
        <wsdl:part name="getSecurityServerOperationalDataResponse"
                element="opm:getSecurityServerOperationalDataResponse"/>
    </wsdl:message>

    <wsdl:message name="getSecurityServerOperationalData">
        <wsdl:part name="getSecurityServerOperationalData"
                element="opm:getSecurityServerOperationalData"/>
    </wsdl:message>

    <wsdl:message name="getSecurityServerHealthDataResponse">
        <wsdl:part name="getSecurityServerHealthDataResponse"
                element="opm:getSecurityServerHealthDataResponse"/>
    </wsdl:message>

    <wsdl:message name="getSecurityServerHealthData">
        <wsdl:part name="getSecurityServerHealthData"
                element="opm:getSecurityServerHealthData"/>
    </wsdl:message>

    <wsdl:message name="requestheader">
        <wsdl:part name="client" element="xrd:client"/>
        <wsdl:part name="service" element="xrd:service"/>
        <wsdl:part name="securityServer" element="xrd:securityServer"/>
        <wsdl:part name="id" element="xrd:id"/>
        <wsdl:part name="protocolVersion" element="xrd:protocolVersion"/>
    </wsdl:message>

    <wsdl:portType name="opmServicePortType">
        <wsdl:operation name="getSecurityServerHealthData">
            <wsdl:documentation>
                <xrd:title>Security server health data</xrd:title>
            </wsdl:documentation>
            <wsdl:input name="getSecurityServerHealthData"
                    message="tns:getSecurityServerHealthData"/>
            <wsdl:output name="getSecurityServerHealthDataResponse"
                    message="tns:getSecurityServerHealthDataResponse"/>
        </wsdl:operation>
        <wsdl:operation name="getSecurityServerOperationalData">
            <wsdl:documentation>
                <xrd:title>Security server operational data</xrd:title>
            </wsdl:documentation>
            <wsdl:input name="getSecurityServerOperationalData"
                    message="tns:getSecurityServerOperationalData"/>
            <wsdl:output name="getSecurityServerOperationalDataResponse"
                    message="tns:getSecurityServerOperationalDataResponse"/>
        </wsdl:operation>
    </wsdl:portType>

    <wsdl:binding name="opmSoap11Binding" type="tns:opmServicePortType">
        <soap:binding style="document"
                transport="http://schemas.xmlsoap.org/soap/http"/>
        <wsdl:operation name="getSecurityServerOperationalData">
            <soap:operation soapAction=""/>
            <wsdl:input name="getSecurityServerOperationalData">
                <soap:body use="literal"/>
                <soap:header message="tns:requestheader" part="client" use="literal"/>
                <soap:header message="tns:requestheader" part="service" use="literal"/>
                <soap:header message="tns:requestheader" part="securityServer" use="literal"/>
                <soap:header message="tns:requestheader" part="id" use="literal"/>
                <soap:header message="tns:requestheader" part="protocolVersion" use="literal"/>
            </wsdl:input>
            <wsdl:output name="getSecurityServerOperationalDataResponse">
                <soap:header message="tns:requestheader" part="client" use="literal"/>
                <soap:header message="tns:requestheader" part="service" use="literal"/>
                <soap:header message="tns:requestheader" part="securityServer" use="literal"/>
                <soap:header message="tns:requestheader" part="id" use="literal"/>
                <soap:header message="tns:requestheader" part="protocolVersion" use="literal"/>
                <mime:multipartRelated>
                    <mime:part>
                        <soap:body use="literal"/>
                    </mime:part>
                </mime:multipartRelated>
            </wsdl:output>
        </wsdl:operation>
        <wsdl:operation name="getSecurityServerHealthData">
            <soap:operation soapAction=""/>
            <wsdl:input name="getSecurityServerHealthData">
                <soap:body use="literal"/>
                <soap:header message="tns:requestheader" part="client" use="literal"/>
                <soap:header message="tns:requestheader" part="service" use="literal"/>
                <soap:header message="tns:requestheader" part="securityServer" use="literal"/>
                <soap:header message="tns:requestheader" part="id" use="literal"/>
                <soap:header message="tns:requestheader" part="protocolVersion" use="literal"/>
            </wsdl:input>
            <wsdl:output name="getSecurityServerHealthDataResponse">
                <soap:body use="literal"/>
                <soap:header message="tns:requestheader" part="client" use="literal"/>
                <soap:header message="tns:requestheader" part="service" use="literal"/>
                <soap:header message="tns:requestheader" part="securityServer" use="literal"/>
                <soap:header message="tns:requestheader" part="id" use="literal"/>
                <soap:header message="tns:requestheader" part="protocolVersion" use="literal"/>
            </wsdl:output>
        </wsdl:operation>
    </wsdl:binding>

    <wsdl:service name="opmService">
        <wsdl:port name="opmServiceSoap11Port" binding="tns:opmSoap11Binding">
            <soap:address location="https://SECURITYSERVER/"/>
        </wsdl:port>
    </wsdl:service>
</wsdl:definitions>
```

<a name="AnnexB"/></a>
# Annex B JSON-Schema for Payload of getSecurityServerOperationalData Response

The schema is located in the file *src/op-monitor-daemon/src/main/resources/query_operational_data_response_payload_schema.yaml* of the X-Road source code.

```yaml
title: Query Operational Data Response Payload Schema
type: object
properties:
  records:
    description: Operational Data Records
    type: array
    items:
      type: object
      properties:
        monitoringDataTs:
          description: The Unix timestamp in seconds when the record was received by the monitoring daemon
          type: integer
          minimum: 0
        securityServerInternalIp:
          description: Internal IP address of the security server
          type: string
          format: ipv4
          maxLength: 255
        securityServerType:
          description: Type of the security server
          type: string
          enum:
          - Client
          - Producer
        requestInTs:
          description: 'In the client''s security server: the Unix timestamp in milliseconds when the request was received by the client''s security server. In the service provider''s security server: the Unix timestamp in milliseconds when the request was received by the service provider''s security server. In both cases, the timestamp is taken just before received payload byte array is decoded and processed'
          type: integer
          minimum: 0
        requestOutTs:
          description: 'In the client''s security server: the Unix timestamp in milliseconds when the request was sent out from the client''s security server to the client''s information system. In the service provider''s security server: the Unix timestamp in milliseconds when the request was sent out from the service provider''s security server. In both cases, the timestamp is taken just before payload byte array is sent out with HTTP POST request'
          type: integer
          minimum: 0
        responseInTs:
          description: 'In the client''s security server: the Unix timestamp in milliseconds when the response was received by the client''s security server. In the service provider''s security server: the Unix timestamp in milliseconds when the response was received by the service provider''s security server. In both cases, the timestamp is taken just before received payload byte array is decoded and processed.'
          type: integer
          minimum: 0
        responseOutTs:
          description: 'In the client''s security server: the Unix timestamp in milliseconds when the response was sent out from the client''s security server to the client''s information system. In the service provider''s security server: the Unix timestamp in milliseconds when the response was sent out from the service provider''s security server. In both cases, the timestamp is taken just before payload byte array is sent out with HTTP response'
          type: integer
          minimum: 0
        clientXRoadInstance:
          description: Instance identifier of the instance used by the client
          type: string
          maxLength: 255
        clientMemberClass:
          description: Member class of the X-Road member (client)
          type: string
          maxLength: 255
        clientMemberCode:
          description: Member code of the X-Road member (client)
          type: string
          maxLength: 255
        clientSubsystemCode:
          description: Subsystem code of the X-Road member (client)
          type: string
          maxLength: 255
        serviceXRoadInstance:
          description: Instance identifier of the instance used by the service provider
          type: string
          maxLength: 255
        serviceMemberClass:
          description: Member class of the X-Road member (service provider)
          type: string
          maxLength: 255
        serviceMemberCode:
          description: Member code of the X-Road member (service provider)
          type: string
          maxLength: 255
        serviceSubsystemCode:
          description: Subsystem code of the X-Road member (service provider)
          type: string
          maxLength: 255
        serviceCode:
          description: Code of the service
          type: string
          maxLength: 255
        serviceVersion:
          description: Version of the service
          type: string
          maxLength: 255
        representedPartyClass:
          description: Class of the represented party
          type: string
          maxLength: 255
        representedPartyCode:
          description: Code of the represented party
          type: string
          maxLength: 255
        messageId:
          description: Unique identifier of the message
          type: string
          maxLength: 255
        messageUserId:
          description: Personal code of the client that initiated the request
          type: string
          maxLength: 255
        messageIssue:
          description: Client's internal identifier of a file or document related to the service
          type: string
          maxLength: 255
        messageProtocolVersion:
          description: X-Road message protocol version
          type: string
          maxLength: 255
        clientSecurityServerAddress:
          description: External address of client's security server (IP or name) defined in global configuration
          type: string
          maxLength: 255
        serviceSecurityServerAddress:
          description: External address of service provider's security server (IP or name) defined in global configuration
          type: string
          maxLength: 255
        requestMimeSize:
          description: Size of the MIME-container of the request (bytes)
          type: integer
          minimum: 0
        requestAttachmentCount:
          description: Number of attachments of the request
          type: integer
          minimum: 0
        responseMimeSize:
          description: Size of the MIME-container of the response (bytes)
          type: integer
          minimum: 0
        responseAttachmentCount:
          description: Number of attachments of the response
          type: integer
          minimum: 0
        succeeded:
          description: True, if request mediation succeeded, false otherwise.
          type: boolean
        faultCode:
          description: fault code in case error received
          type: string
          maxLength: 255
        faultString:
          description: fault reason in case error received
          type: string
          maxLength: 2048
        requestSize:
          description: Size of the request (bytes)
          type: integer
          minimum: 0
        responseSize:
          description: Size of the response (bytes)
          type: integer
          minimum: 0
        statusCode:
          description: HTTP status code for the rest response
          type: integer
          minimum: 0
        serviceType:
            description: Type of the service WSDL, REST or OPENAPI3
            type: string
            minimum: 0
required:
- records
```

<a name="AnnexC"/></a>
# Annex C Example Messages

<a name="AnnexC.1"/></a>
## C.1 getSecurityServerOperationalData Request

```xml
<?xml version="1.0" encoding="utf-8"?>
<SOAP-ENV:Envelope
    xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
    xmlns:xroad="http://x-road.eu/xsd/xroad.xsd"
    xmlns:om="http://x-road.eu/xsd/op-monitoring.xsd"
    xmlns:id="http://x-road.eu/xsd/identifiers">
  <SOAP-ENV:Header>
    <xroad:client id:objectType="SUBSYSTEM">
      <id:xRoadInstance>EE</id:xRoadInstance>
      <id:memberClass>GOV</id:memberClass>
      <id:memberCode>00000001</id:memberCode>
      <id:subsystemCode>System1</id:subsystemCode>
    </xroad:client>
    <xroad:service id:objectType="SERVICE">
      <id:xRoadInstance>EE</id:xRoadInstance>
      <id:memberClass>GOV</id:memberClass>
      <id:memberCode>00000001</id:memberCode>
      <id:serviceCode>getSecurityServerOperationalData</id:serviceCode>
    </xroad:service>
    <xroad:securityServer id:objectType="SERVER">
      <id:xRoadInstance>EE</id:xRoadInstance>
      <id:memberClass>GOV</id:memberClass>
      <id:memberCode>00000001</id:memberCode>
      <id:serverCode>00000001_1</id:serverCode>
    </xroad:securityServer>
    <xroad:id>1KNtf07U6qIyOcJnkirRaE0hRe4bM7WF</xroad:id>
    <xroad:protocolVersion>4.0</xroad:protocolVersion>
  </SOAP-ENV:Header>
  <SOAP-ENV:Body>
    <om:getSecurityServerOperationalData>
      <om:searchCriteria>
        <om:recordsFrom>1480512828</om:recordsFrom>
        <om:recordsTo>1480512832</om:recordsTo>
      </om:searchCriteria>
    </om:getSecurityServerOperationalData>
  </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```

<a name="AnnexC.2"/></a>
## C.2 getSecurityServerOperationalData Response

```xml
Content-Type: multipart/related; type="text/xml"; charset=UTF-8;
boundary=xroadfngEfgBlxyLszDaqXiFfDxVzvvlbhU
Content-Length: 7298

--xroadfngEfgBlxyLszDaqXiFfDxVzvvlbhU
content-type:text/xml

<?xml version="1.0" encoding="utf-8"?>
<SOAP-ENV:Envelope
    xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
    xmlns:xroad="http://x-road.eu/xsd/xroad.xsd"
    xmlns:om="http://x-road.eu/xsd/op-monitoring.xsd"
    xmlns:id="http://x-road.eu/xsd/identifiers">
  <SOAP-ENV:Header>
    <xroad:client id:objectType="SUBSYSTEM">
      <id:xRoadInstance>EE</id:xRoadInstance>
      <id:memberClass>GOV</id:memberClass>
      <id:memberCode>00000001</id:memberCode>
      <id:subsystemCode>System1</id:subsystemCode>
    </xroad:client>
    <xroad:service id:objectType="SERVICE">
      <id:xRoadInstance>EE</id:xRoadInstance>
      <id:memberClass>GOV</id:memberClass>
      <id:memberCode>00000001</id:memberCode>
      <id:serviceCode>getSecurityServerOperationalData</id:serviceCode>
    </xroad:service>
    <xroad:securityServer id:objectType="SERVER">
      <id:xRoadInstance>EE</id:xRoadInstance>
      <id:memberClass>GOV</id:memberClass>
      <id:memberCode>00000001</id:memberCode>
      <id:serverCode>00000001_1</id:serverCode>
    </xroad:securityServer>
    <xroad:id>1KNtf07U6qIyOcJnkirRaE0hRe4bM7WF</xroad:id>
    <xroad:requestHash algorithmId="http://www.w3.org/2001/04/xmlenc#sha512">
      r+GNfQVRJ82RMpaRMO/K/2z97zEr1jiSL4m7clAEogiZiaSTnylksQZZc/rBs8NVEde...
    </xroad:requestHash>
    <xroad:protocolVersion>4.0</xroad:protocolVersion>
  </SOAP-ENV:Header>
  <SOAP-ENV:Body>
    <om:getSecurityServerOperationalDataResponse>
      <om:recordsCount>122</om:recordsCount>
      <om:records>cid:operational-monitoring-data.json.gz</om:records>
    </om:getSecurityServerOperationalDataResponse>
  </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
--xroadfngEfgBlxyLszDaqXiFfDxVzvvlbhU
content-type:application/gzip
content-transfer-encoding: binary
content-id: <operational-monitoring-data.json.gz>

(binary data)
--xroadfngEfgBlxyLszDaqXiFfDxVzvvlbhU--
```

### C.2.1 Example JSON-Payload of getSecurityServerOperationalData Response

```json
{
  "records": [
    {
      "clientMemberClass": "GOV",
      "clientMemberCode": "00000001",
      "clientSecurityServerAddress": "ss1.ci.kit",
      "clientSubsystemCode": "subsystem1",
      "clientXRoadInstance": "EE",
      "messageId": "1TzYPstxXyYPtNsos4TNEAPykJh50aJz",
      "messageIssue": "453465",
      "messageProtocolVersion": "4.0",
      "messageUserId": "EE37701010101",
      "monitoringDataTs": 1477633845,
      "representedPartyClass": "COM",
      "representedPartyCode": "MEMBER123",
      "requestAttachmentCount": 0,
      "requestInTs": 1477633844973,
      "requestOutTs": 1477633844986,
      "requestSize": 1629,
      "responseAttachmentCount": 0,
      "responseInTs": 1477633845222,
      "responseOutTs": 1477633845243,
      "responseSize": 1518,
      "securityServerInternalIp": "192.168.1.1",
      "securityServerType": "Client",
      "serviceCode": "xroadGetRandom",
      "serviceType": "WSDL",
      "serviceMemberClass": "GOV",
      "serviceMemberCode": "00000000",
      "serviceSecurityServerAddress": "ss0.ci.kit",
      "serviceSubsystemCode": "subsystem1",
      "serviceVersion": "v1",
      "serviceXRoadInstance": "EE",
      "succeeded": true,
      "xRequestId": "fd83f20e-bc19-4eb4-9602-e37f94c09fbe"
    }
  ]
}
```
<a name="AnnexC.3"/></a>
## C.3 getSecurityServerHealthData Request

```xml
<?xml version="1.0" encoding="utf-8"?>
<SOAP-ENV:Envelope
    xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
    xmlns:xroad="http://x-road.eu/xsd/xroad.xsd"
    xmlns:om="http://x-road.eu/xsd/op-monitoring.xsd"
    xmlns:id="http://x-road.eu/xsd/identifiers">
  <SOAP-ENV:Header>
    <xroad:client id:objectType="SUBSYSTEM">
      <id:xRoadInstance>EE</id:xRoadInstance>
      <id:memberClass>GOV</id:memberClass>
      <id:memberCode>00000000</id:memberCode>
      <id:subsystemCode>System1</id:subsystemCode>
    </xroad:client>
    <xroad:service id:objectType="SERVICE">
      <id:xRoadInstance>EE</id:xRoadInstance>
      <id:memberClass>GOV</id:memberClass>
      <id:memberCode>00000001</id:memberCode>
      <id:serviceCode>getSecurityServerHealthData</id:serviceCode>
    </xroad:service>
    <xroad:securityServer id:objectType="SERVER">
      <id:xRoadInstance>EE</id:xRoadInstance>
      <id:memberClass>GOV</id:memberClass>
      <id:memberCode>00000001</id:memberCode>
      <id:serverCode>00000001_1</id:serverCode>
    </xroad:securityServer>
    <xroad:id>0PebOv6afaFEMVqPcwwtzIZCuRiRBu6T</xroad:id>
    <xroad:protocolVersion>4.0</xroad:protocolVersion>
  </SOAP-ENV:Header>
  <SOAP-ENV:Body>
    <om:getSecurityServerHealthData>
      <om:filterCriteria>
        <om:client id:objectType="SUBSYSTEM">
          <id:xRoadInstance>EE</id:xRoadInstance>
          <id:memberClass>GOV</id:memberClass>
          <id:memberCode>00000001</id:memberCode>
          <id:subsystemCode>System2</id:subsystemCode>
        </om:client>
      </om:filterCriteria>
    </om:getSecurityServerHealthData>
  </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```

<a name="AnnexC.3"/></a>
## C.4 getSecurityServerHealthData Response

```xml
<?xml version="1.0" encoding="utf-8"?>
<SOAP-ENV:Envelope
    xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
    xmlns:xroad="http://x-road.eu/xsd/xroad.xsd"
    xmlns:om="http://x-road.eu/xsd/op-monitoring.xsd"
    xmlns:id="http://x-road.eu/xsd/identifiers">
  <SOAP-ENV:Header>
    <xroad:client id:objectType="SUBSYSTEM">
      <id:xRoadInstance>EE</id:xRoadInstance>
      <id:memberClass>GOV</id:memberClass>
      <id:memberCode>00000000</id:memberCode>
      <id:subsystemCode>System1</id:subsystemCode>
    </xroad:client>
    <xroad:service id:objectType="SERVICE">
      <id:xRoadInstance>EE</id:xRoadInstance>
      <id:memberClass>GOV</id:memberClass>
      <id:memberCode>00000001</id:memberCode>
      <id:serviceCode>getSecurityServerHealthData</id:serviceCode>
    </xroad:service>
    <xroad:securityServer id:objectType="SERVER">
      <id:xRoadInstance>EE</id:xRoadInstance>
      <id:memberClass>GOV</id:memberClass>
      <id:memberCode>00000001</id:memberCode>
      <id:serverCode>00000001_1</id:serverCode>
    </xroad:securityServer>
    <xroad:id>0PebOv6afaFEMVqPcwwtzIZCuRiRBu6T</xroad:id>
    <xroad:requestHash algorithmId="http://www.w3.org/2001/04/xmlenc#sha512">
      QzekAiZVOaz3p1IGCrWkjc3bGRPGg9XN3SWEsF5onsTdzZ5w+chgOSnCJhT9sH+4Jhh...
    </xroad:requestHash>
    <xroad:protocolVersion>4.0</xroad:protocolVersion>
  </SOAP-ENV:Header>
  <SOAP-ENV:Body>
    <om:getSecurityServerHealthDataResponse>
      <om:monitoringStartupTimestamp>1480512900441
          </om:monitoringStartupTimestamp>
      <om:statisticsPeriodSeconds>600</om:statisticsPeriodSeconds>
      <om:servicesEvents>
        <om:serviceEvents>
          <om:service id:objectType="SERVICE">
            <id:xRoadInstance>EE</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>00000001</id:memberCode>
            <id:subsystemCode>System2</id:subsystemCode>
            <id:serviceCode>xroadGetRandom</id:serviceCode>
            <id:serviceVersion>v1</id:serviceVersion>
          </om:service>
          <om:lastSuccessfulRequestTimestamp>1480512901824
              </om:lastSuccessfulRequestTimestamp>
          <om:serviceType>WSDL</om:serviceType>
          <om:lastPeriodStatistics>
            <om:successfulRequestCount>1</om:successfulRequestCount>
            <om:unsuccessfulRequestCount>0</om:unsuccessfulRequestCount>
            <om:requestMinDuration>42</om:requestMinDuration>
            <om:requestAverageDuration>42.0</om:requestAverageDuration>
            <om:requestMaxDuration>42</om:requestMaxDuration>
            <om:requestDurationStdDev>0.0</om:requestDurationStdDev>
            <om:requestMinSize>1629</om:requestMinSize>
            <om:requestAverageSize>1629.0</om:requestAverageSize>
            <om:requestMaxSize>1629</om:requestMaxSize>
            <om:requestSizeStdDev>0.0</om:requestSizeStdDev>
            <om:responseMinSize>1519</om:responseMinSize>
            <om:responseAverageSize>1519.0</om:responseAverageSize>
            <om:responseMaxSize>1519</om:responseMaxSize>
            <om:responseSizeStdDev>0.0</om:responseSizeStdDev>
          </om:lastPeriodStatistics>
        </om:serviceEvents>
      </om:servicesEvents>
    </om:getSecurityServerHealthDataResponse>
  </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```
