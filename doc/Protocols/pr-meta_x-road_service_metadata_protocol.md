![](img/eu_regional_development_fund_horizontal_div_15.png "European Union | European Regional Development Fund | Investing in your future")

---


# X-Road: Service Metadata Protocol v2.1
**Technical Specification**

Version: 2.1.6
23.08.2017
Doc. ID: PR-META

---


## Version history

 Date       | Version | Description                                                     | Author
 ---------- | ------- | --------------------------------------------------------------- | --------------------
 04.08.2015 | 0.8   | Initial version                                                   | Siim Annuk
 09.09.2015 | 1.0   | Editorial changes made                                            | Imbi Nõgisto
 15.09.2015 | 1.1   | Made minor fixes to schemas                                       | Margus Freudenthal
 16.09.2015 | 2.0   | Final version                                                     | Imbi Nõgisto
 12.10.2015 | 2.1   | Updated identifier names and WSDL examples                        | Ilja Kromonov
 23.08.2017 | 2.1.6   | Converted to Markdown                                           | Janne Mattila

## Table of Contents

<!-- toc -->

- [License](#license)
- [1 Introduction](#1-introduction)
  * [1.1 References](#11-references)
- [2 Retrieving List of Service Providers](#2-retrieving-list-of-service-providers)
- [3 Retrieving List of Central Services](#3-retrieving-list-of-central-services)
- [4 Retrieving List of Services](#4-retrieving-list-of-services)
- [5 Retrieving WSDL of a Service](#5-retrieving-wsdl-of-a-service)
- [Annex A XML Schema for Messages](#annex-a-xml-schema-for-messages)
- [Annex B listMethods and allowedMethods WSDL](#annex-b-listmethods-and-allowedmethods-wsdl)
- [Annex C Example Messages](#annex-c-example-messages)
<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/

## 1 Introduction

This specification describes methods that can be used by X-Road participants to discover what services are available to them and download the WSDL files describing these services. The X-Road service metadata protocol is intended to support portals and other software that can discover the available services and then automatically generate user interfaces based on their descriptions. In order to accomplish this, the portal can use the following steps.

1. Download a list of X-Road members and subsystems (see Section [2](#2-retrieving-list-of-service-providers)). This results in a list of (potential) service provides who can be further be queried. Alternatively, the portal can download a list of central services (see Section [3](#3-retrieving-list-of-central-services)) defined in the X-Road central server.

2. Connect to the service provider and acquire a list of services offered by this provider (see Section [4](#4-retrieving-list-of-services)). This service has two forms: listMethods returns a list of services provided by a given service provider, allowedMethods constrains the returned list by only including services that are allowed for the client.

3. Download the description of the service in WSDL format (see Section [5](#5-retrieving-wsdl-of-a-service)).

This specification is based on the X-Road protocol \[[PR-MESS](#Ref_PR-MESS)\]. The X-Road protocol specification also defines important concepts used in this text (for example, central service and X-Road identifier). Because this protocol uses HTTP and X-Road protocol as transport mechanisms, the details of message transport and error conditions are not described in this specification.
Chapters [2](#2-retrieving-list-of-service-providers), [3](#3-retrieving-list-of-central-services), [4](#4-retrieving-list-of-services) and [5](#5-retrieving-wsdl-of-a-service) together with annexes Annex [Annex A](#annex-a-xml-schema-for-messages) and [B](#annex-b-listmethods-and-allowedmethods-wsdl) contain normative information. All the other sections are informative in nature. All the references are normative.
This specification does not include option for partially implementing the protocol – the conformant implementation must implement the entire specification.
The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in this document (in uppercase, as shown) are to be interpreted as described in \[[RFC2119](#Ref_RFC2119)\].

## 1.1 References

1. <a name="Ref_PR-MESS" class="anchor"></a>\[PR-MESS\] Cybernetica AS. X-Road: Message Protocol v4.0.
2. <a name="Ref_RFC2119" class="anchor"></a>\[RFC2119\] Key words for use in RFCs to Indicate Requirement Levels, Internet Engineering Task Force, 1997.
                                                        

## 2 Retrieving List of Service Providers

Security server clients can retrieve a list of all the potential service providers (i.e., members and subsystems) of an X-Road instance. This can be accomplished by making a HTTP GET request to the security server. The request URL is `http://SECURITYSERVER/listClients` or `https://SECURITYSERVER/listClients` depending on whether the HTTPS protocol is configured for interaction between the security server and the information system. When making the request, the address `SECURITYSERVER` must be replaced with the actual address of the security server.
In addition, it is possible to retrieve a list of clients in other, federated X-Road instances by adding the following HTTP parameter:

* `xRoadInstance` – code that identifies the X-Road instance.

Thus, in order to retrieve a list of clients defined in the X-Road instance AA, the request URL is `http://SECURITYSERVER/listClients?xRoadInstance=AA`.
Security server MUST respond with content-type text/xml and the response MUST contain the `clientList` XML element defined below 
(full XML schema appears in Annex [Annex A](#annex-a-xml-schema-for-messages)). 
Annex [C.1](#c1-listclients-response) contains an example response message.

```xml
    <xs:element name="clientList" type="ClientListType"/>
    <xs:complexType name="ClientListType">
        <xs:sequence>
            <xs:element maxOccurs="unbounded" minOccurs="0"
                name="member" type="ClientType"/>
        </xs:sequence>
    </xs:complexType>
    <xs:complexType name="ClientType">
        <xs:sequence>
            <xs:element name="id" type="id:XRoadClientIdentifierType"/>             
            <xs:element name="name" type="xs:string" minOccurs="0"/>
        </xs:sequence>
    </xs:complexType>
```

The `XRoadClientIdentifierType` represents a globally unique identifier of an X-Road client. 
The client identifier has a hierarchical structure consisting of X-Road instance, member class, member and (optionally) subsystem codes. See specification\[[PR-MESS](#Ref_PR-MESS)\] for explanation and specification of identifiers.

## 3 Retrieving List of Central Services

Security server clients can retrieve a list of all central services defined in an X-Road instance. This can be accomplished by making a HTTP GET request to the security server. 
The request URL is `http://SECURITYSERVER/listCentralServices` or `https://SECURITYSERVER/listCentralServices` depending on whether the HTTPS protocol is configured for interaction between the security server and the information system. 
When making the request, the address `SECURITYSERVER` must be replaced with the actual address of the security server.
In addition, it is possible to retrieve a list of security servers in other, federated X-Road instances by adding the following HTTP parameter:

* `xRoadInstance` – code that identifies the X-Road instance.

Thus, in order to retrieve a list of central services defined in X-Road instance AA, the 
request URL is `http://SECURITYSERVER/listCentralServices?xRoadInstance=AA`.
Security server MUST respond with content-type `text/xml` and the response MUST contain the 
`centralServiceList` XML element defined below 
(full XML schema appears in Annex [Annex A](#annex-a-xml-schema-for-messages))). 
Annex [C.2](#c2-listcentralservices-response) contains an example response message.
```xml
    <xs:element name="centralServiceList"
        type="CentralServiceListType"/>
    <xs:complexType name="CentralServiceListType">
        <xs:sequence>
            <xs:element maxOccurs="unbounded" minOccurs="0"
                name="centralService"
                type="id:XRoadCentralServiceIdentifierType"/>
        </xs:sequence>
    </xs:complexType>
```

The `XRoadCentralServiceIdentifierType` represents a globally unique identifier of a central service consisting of a unique code identifying the X-Road instance and a code for the central service (unique within the given instance). See specification \[[PR-MESS](#Ref_PR-MESS)\] for explanation and specification of identifiers.

## 4 Retrieving List of Services

X-Road provides two methods for getting the list of services offered by an X-Road client:

* `listMethods` lists all services offered by a service provider; and

* `allowedMethods` lists all services offered by a service provider that the caller has permission to invoke.

Both methods are invoked as regular X-Road services (see specification \[[PR-MESS](#Ref_PR-MESS)\] for details on the X-Road protocol). 
The service SOAP header MUST contain the identifier of the target service provider and the value of the serviceCode element MUST be either `listMethods` or `allowedMethods`. 
The body of the request MUST contain an appropriately named empty XML element (either `listMethods` or `allowedMethods`). Annexes C.3 and C.5 contain example request messages for services, respectively.
The body of the response message MUST contain a list of services provided by the service provider (in case of listMethods) or open to the given client (in case of allowedMethods). The response SHALL NOT contain names of the metainfo services. The following snippet contains XML schema of the response body. 
Annexes [C.4](#c4-listmethods-response) and [C.6](#c6-allowedmethods-response) contain example request messages for listMethods and allowedMethods services, respectively.
```xml
    <xs:element name="listMethodsResponse" type="MethodListType"/>
    <xs:element name="allowedMethodsResponse" type="MethodListType"/>

    <xs:complexType name="MethodListType">
        <xs:sequence>
            <xs:element maxOccurs="unbounded" minOccurs="0"
                name="service" type="id:XRoadServiceIdentifierType"/>
        </xs:sequence>
    </xs:complexType>
```
    
## 5 Retrieving WSDL of a Service   

Service clients are able to download WSDL files that contain the definition of a given service. 
This can be accomplished by making HTTP GET request to the client's security server. 
The URL of the request is either `http://SECURITYSERVER/wsdl` or `https://SECURITYSERVER/wsdl` depending on whether the HTTPS protocol is configured for interaction between the security server and the information system. 
When making the request, the address `SECURITYSERVER` must be replaced with the actual address of the security server. The client MUST specify the identifier of the service using the following parameters:

* `xRoadInstance` – code that identifies the X-Road instance;

* `memberClass` – code that identifies the member class;

* `memberCode` – code that identifies the X-Road member;

* `subsystemCode` (optional) – code that identifies a subsystem of the given member (if the service is provided by a subsystem);

* `serviceCode` – identifies the specific service;

* `version` (optional) – version of the service. 

Therefore, an example HTTP request would be:
`http://SECURITYSERVER/wsdl?xRoadInstance=Inst1&memberClass=MemberClass1&memberCode=ProviderId&serviceCode=service1`

All the special symbols (such as spaces, question marks etc.) MUST be escaped.
WSDL files for central services are accessed in a similar manner, in this case the query parameters MUST be:

* `xRoadInstance` – code that identifies the X-Road instance;

* `serviceCode` – code that identifies the central service.

## Annex A XML Schema for Messages

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
        elementFormDefault="qualified"
        targetNamespace="http://x-road.eu/xsd/xroad.xsd"
        xmlns="http://x-road.eu/xsd/xroad.xsd"
        xmlns:id="http://x-road.eu/xsd/identifiers">
    <xs:import schemaLocation="http://x-road.eu/xsd/identifiers.xsd"
            namespace="http://x-road.eu/xsd/identifiers"/>
    <xs:complexType name="ClientListType">
        <xs:sequence>
            <xs:element maxOccurs="unbounded" minOccurs="0"
                    name="member" type="ClientType"/>
        </xs:sequence>
    </xs:complexType>
    <xs:complexType name="CentralServiceListType">
        <xs:sequence>
            <xs:element maxOccurs="unbounded" minOccurs="0"
                    name="centralService"
                    type="id:XRoadCentralServiceIdentifierType"/>
        </xs:sequence>
    </xs:complexType>
    <xs:complexType name="ClientType">
        <xs:sequence>
            <xs:element name="id" type="id:XRoadClientIdentifierType"/>             
            <xs:element name="name" type="xs:string" minOccurs="0"/>
        </xs:sequence>
    </xs:complexType>
    <xs:element name="clientList" type="ClientListType"/>
    <xs:element name="centralServiceList" type="CentralServiceListType"/>
</xs:schema>
```

## Annex B listMethods and allowedMethods WSDL

```xml
<?xml version="1.0" encoding="UTF-8"?>
<wsdl:definitions targetNamespace="http://metadata.x-road.eu/"
        xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/" 
        xmlns:meta="http://metadata.x-road.eu/"
        xmlns:xrd="http://x-road.eu/xsd/xroad.xsd" 
        xmlns:id="http://x-road.eu/xsd/identifiers"
        xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/">
    <wsdl:types>
        <xs:schema targetNamespace="http://metadata.x-road.eu/"
                xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <xs:import namespace="http://x-road.eu/xsd/xroad.xsd"
                    schemaLocation="http://x-road.eu/xsd/xroad.xsd"/>
            <xs:import namespace="http://x-road.eu/xsd/identifiers"
                    schemaLocation="http://x-road.eu/xsd/identifiers.xsd"/>
            <xs:element name="listMethods">
                <xs:complexType>
                    <xs:sequence />
                </xs:complexType>
            </xs:element>
            <xs:element name="allowedMethods">
                <xs:complexType>
                    <xs:sequence />
                </xs:complexType>
            </xs:element>
            <xs:element name="listMethodsResponse">
                <xs:complexType>
                    <xs:sequence>
                        <xs:element maxOccurs="unbounded" minOccurs="0"
                                name="serviceCode"
                                type="id:XRoadServiceIdentifierType" />
                    </xs:sequence>
                </xs:complexType>
            </xs:element>
            <xs:element name="allowedMethodsResponse">
                <xs:complexType>
                    <xs:sequence>
                        <xs:element maxOccurs="unbounded" minOccurs="0"
                                name="serviceCode"
                                type="id:XRoadServiceIdentifierType" />
                    </xs:sequence>
                </xs:complexType>
            </xs:element>
        </xs:schema>
    </wsdl:types>

    <wsdl:message name="listMethodsResponse">
        <wsdl:part name="listMethodsResponse" 
                element="meta:listMethodsResponse" />
    </wsdl:message>

    <wsdl:message name="listMethods">
        <wsdl:part name="listMethods" element="meta:listMethods" />
    </wsdl:message>

    <wsdl:message name="allowedMethodsResponse">
        <wsdl:part name="allowedMethodsResponse" element="meta:allowedMethodsResponse" />
    </wsdl:message>

    <wsdl:message name="allowedMethods">
        <wsdl:part name="allowedMethods" element="meta:allowedMethods" />
    </wsdl:message>

    <wsdl:message name="requestheader">
        <wsdl:part name="client" element="xrd:client" />
        <wsdl:part name="service" element="xrd:service" />
        <wsdl:part name="userId" element="xrd:userId" />
        <wsdl:part name="id" element="xrd:id" />
        <wsdl:part name="protocolVersion" element="xrd:protocolVersion" />
    </wsdl:message>

    <wsdl:portType name="metaServicesPort">
        <wsdl:operation name="allowedMethods">
            <wsdl:documentation>
                <xrd:title>allowedMethods</xrd:title>
            </wsdl:documentation>
            <wsdl:input name="allowedMethods" message="meta:allowedMethods" />
            <wsdl:output name="allowedMethodsResponse" 
                    message="meta:allowedMethodsResponse" />
        </wsdl:operation>
        <wsdl:operation name="listMethods">
            <wsdl:documentation>
                <xrd:title>listMethods</xrd:title>
            </wsdl:documentation>
            <wsdl:input name="listMethods" message="meta:listMethods" />
            <wsdl:output name="listMethodsResponse" 
                    message="meta:listMethodsResponse" />
        </wsdl:operation>
    </wsdl:portType>

    <wsdl:binding name="metaServicesPortSoap11" type="meta:metaServicesPort">
        <soap:binding style="document"
            transport="http://schemas.xmlsoap.org/soap/http" />
        <wsdl:operation name="allowedMethods">
            <soap:operation soapAction="" />
            <wsdl:input name="allowedMethods">
                <soap:body use="literal" />
                <soap:header message="meta:requestheader" part="client"
                        use="literal" />
                <soap:header message="meta:requestheader" part="service"
                        use="literal" />
                <soap:header message="meta:requestheader" part="userId"
                        use="literal" />
                <soap:header message="meta:requestheader" part="id"
                        use="literal" />
                <soap:header message="meta:requestheader" part="protocolVersion" 
                        use="literal" />
            </wsdl:input>
            <wsdl:output name="allowedMethodsResponse">
                <soap:body use="literal" />
                <soap:header message="meta:requestheader" part="client"
                    use="literal" />
                <soap:header message="meta:requestheader" part="service"
                    use="literal" />
                <soap:header message="meta:requestheader" part="userId"
                    use="literal" />
                <soap:header message="meta:requestheader" part="id" 
                    use="literal" />
                <soap:header message="meta:requestheader" part="protocolVersion" 
                        use="literal" />
            </wsdl:output>
        </wsdl:operation>        
        <wsdl:operation name="listMethods">
            <soap:operation soapAction="" />
            <wsdl:input name="listMethods">
                <soap:body use="literal" />
                <soap:header message="meta:requestheader" part="client"
                        use="literal" />
                <soap:header message="meta:requestheader" part="service"
                        use="literal" />
                <soap:header message="meta:requestheader" part="userId"
                        use="literal" />
                <soap:header message="meta:requestheader" part="id" 
                        use="literal" />
                <soap:header message="meta:requestheader" part="protocolVersion" 
                        use="literal" />
            </wsdl:input>
            <wsdl:output name="listMethodsResponse">
                <soap:body use="literal" />
                <soap:header message="meta:requestheader" part="client"
                        use="literal" />
                <soap:header message="meta:requestheader" part="service"
                        use="literal" />
                <soap:header message="meta:requestheader" part="userId"
                        use="literal" />
                <soap:header message="meta:requestheader" part="id" 
                        use="literal" />
                <soap:header message="meta:requestheader" part="protocolVersion" 
                        use="literal" />
            </wsdl:output>
        </wsdl:operation>
    </wsdl:binding>

    <wsdl:service name="producerPortService">
        <wsdl:port name="metaServicesPortSoap11" 
                binding="meta:metaServicesPortSoap11">
            <soap:address location="https://SECURITYSERVER/" />
        </wsdl:port>
    </wsdl:service>
</wsdl:definitions>        
```

## Annex C Example Messages

### C.1 listClients Response

```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:clientList
        xmlns:ns1="http://x-road.eu/xsd/identifiers"
        xmlns:ns2="http://x-road.eu/xsd/xroad.xsd">
    <ns2:member>
        <ns2:id ns1:objectType="MEMBER">
            <ns1:xRoadInstance>AA</ns1:xRoadInstance>
            <ns1:memberClass>GOV</ns1:memberClass>
            <ns1:memberCode>TS1OWNER</ns1:memberCode>
        </ns2:id>
        <ns2:name>TS1 Owner</ns2:name>
    </ns2:member>
    <ns2:member>
        <ns2:id ns1:objectType="MEMBER">
            <ns1:xRoadInstance>AA</ns1:xRoadInstance>
            <ns1:memberClass>GOV</ns1:memberClass>
            <ns1:memberCode>TS2OWNER</ns1:memberCode>
        </ns2:id>
        <ns2:name>TS2 Owner</ns2:name>
    </ns2:member>
    <ns2:member>
        <ns2:id ns1:objectType="MEMBER">
            <ns1:xRoadInstance>AA</ns1:xRoadInstance>
            <ns1:memberClass>ENT</ns1:memberClass>
            <ns1:memberCode>CLIENT1</ns1:memberCode>
        </ns2:id>
        <ns2:name>Client One</ns2:name>
    </ns2:member>
    <ns2:member>
        <ns2:id ns1:objectType="SUBSYSTEM">
            <ns1:xRoadInstance>AA</ns1:xRoadInstance>
            <ns1:memberClass>ENT</ns1:memberClass>
            <ns1:memberCode>CLIENT1</ns1:memberCode>
            <ns1:subsystemCode>sub</ns1:subsystemCode>
        </ns2:id>
        <ns2:name>Client One</ns2:name>
    </ns2:member>
</ns2:clientList>
```

### C.2 listCentralServices Response

```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<ns2:centralServiceList
        xmlns:ns1="http://x-road.eu/xsd/identifiers"
        xmlns:ns2="http://x-road.eu/xsd/xroad.xsd">
    <ns2:centralService ns1:objectType="CENTRALSERVICE">
        <ns1:xRoadInstance>AA</ns1:xRoadInstance>
        <ns1:serviceCode>random</ns1:serviceCode>
    </ns2:centralService>
</ns2:centralServiceList>
```
### C.3 listMethods Request
```xml
<?xml version="1.0" encoding="utf-8"?>
<SOAP-ENV:Envelope
        xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
        xmlns:xroad="http://x-road.eu/xsd/xroad.xsd"
        xmlns:id="http://x-road.eu/xsd/identifiers">
    <SOAP-ENV:Header>
        <xroad:client id:objectType="MEMBER">
            <id:xRoadInstance>Inst1</id:xRoadInstance>
            <id:memberClass>MemberClass1</id:memberClass>
            <id:memberCode>ClientId</id:memberCode>
        </xroad:client>
        <xroad:service id:objectType="SERVICE">
            <id:xRoadInstance>Inst1</id:xRoadInstance>
            <id:memberClass>MemberClass1</id:memberClass>
            <id:memberCode>ProviderId</id:memberCode>
            <id:serviceCode>listMethods</id:serviceCode>
        </xroad:service>
        <xroad:id>411d6755661409fed365ad8135f8210be07613da</xroad:id>
        <xroad:protocolVersion>4.0</xroad:protocolVersion>
    </SOAP-ENV:Header>
    <SOAP-ENV:Body>
        <xroad:listMethods/>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```

### C.4 listMethods Response
```xml
<?xml version="1.0" encoding="utf-8"?>
<SOAP-ENV:Envelope
        xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
        xmlns:xroad="http://x-road.eu/xsd/xroad.xsd"
        xmlns:id="http://x-road.eu/xsd/identifiers">
    <SOAP-ENV:Header>
        <xroad:client id:objectType="MEMBER">
            <id:xRoadInstance>Inst1</id:xRoadInstance>
            <id:memberClass>MemberClass1</id:memberClass>
            <id:memberCode>ClientId</id:memberCode>
        </xroad:client>
        <xroad:service id:objectType="SERVICE">
            <id:xRoadInstance>Inst1</id:xRoadInstance>
            <id:memberClass>MemberClass1</id:memberClass>
            <id:memberCode>ProviderId</id:memberCode>
            <id:serviceCode>listMethods</id:serviceCode>
        </xroad:service>
        <xroad:id>411d6755661409fed365ad8135f8210be07613da</xroad:id>
        <xroad:protocolVersion>4.0</xroad:protocolVersion>
        <xroad:requestHash algorithmId="http://www.w3.org/2001/04/xmlenc#sha512">
            Zvs1uF2GW3zdma1r9K9keOGhNPOjCr3TEZNpxfpRCtsq
            qy3ljiLorMZ3e5iNZtX6Ek60xtV12Gue8Mme1ryZmQ==
        </xroad:requestHash>
    </SOAP-ENV:Header>
    </SOAP-ENV:Header>
    <SOAP-ENV:Body>
        <xroad:listMethodsResponse>
            <xroad:service id:objectType="SERVICE">
                <id:xRoadInstance>Inst1</id:xRoadInstance>
                <id:memberClass>MemberClass1</id:memberClass>
                <id:memberCode>ProviderId</id:memberCode>
                <id:serviceCode>allowedService</id:serviceCode>
                <id:serviceVersion>v1</id:serviceVersion>
            </xroad:service>
            <xroad:service id:objectType="SERVICE">
                <id:xRoadInstance>Inst1</id:xRoadInstance>
                <id:memberClass>MemberClass1</id:memberClass>
                <id:memberCode>ProviderId</id:memberCode>
                <id:serviceCode>disallowedService</id:serviceCode>
                <id:serviceVersion>v1</id:serviceVersion>
            </xroad:service>
        </xroad:listMethodsResponse>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```

### C.5 allowedMethods Request
```xml
<?xml version="1.0" encoding="utf-8"?>
<SOAP-ENV:Envelope
        xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
        xmlns:xroad="http://x-road.eu/xsd/xroad.xsd"
        xmlns:id="http://x-road.eu/xsd/identifiers">
    <SOAP-ENV:Header>
        <xroad:client id:objectType="MEMBER">
            <id:xRoadInstance>Inst1</id:xRoadInstance>
            <id:memberClass>MemberClass1</id:memberClass>
            <id:memberCode>ClientId</id:memberCode>
        </xroad:client>
        <xroad:service id:objectType="SERVICE">
            <id:xRoadInstance>Inst1</id:xRoadInstance>
            <id:memberClass>MemberClass1</id:memberClass>
            <id:memberCode>ProviderId</id:memberCode>
            <id:serviceCode>allowedMethods</id:serviceCode>
        </xroad:service>
        <xroad:id>411d6755661409fed365ad8135f8210be07613da</xroad:id>
        <xroad:protocolVersion>4.0</xroad:protocolVersion>
    </SOAP-ENV:Header>
    <SOAP-ENV:Body>
        <xroad:allowedMethods/>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```

### C.6 allowedMethods Response
```xml
<?xml version="1.0" encoding="utf-8"?>
<SOAP-ENV:Envelope
        xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
        xmlns:xroad="http://x-road.eu/xsd/xroad.xsd"
        xmlns:id="http://x-road.eu/xsd/identifiers">
    <SOAP-ENV:Header>
        <xroad:client id:objectType="MEMBER">
            <id:xRoadInstance>Inst1</id:xRoadInstance>
            <id:memberClass>MemberClass1</id:memberClass>
            <id:memberCode>ClientId</id:memberCode>
        </xroad:client>
        <xroad:service id:objectType="SERVICE">
            <id:xRoadInstance>Inst1</id:xRoadInstance>
            <id:memberClass>MemberClass1</id:memberClass>
            <id:memberCode>ProviderId</id:memberCode>
            <id:serviceCode>allowedMethods</id:serviceCode>
        </xroad:service>
        <xroad:id>411d6755661409fed365ad8135f8210be07613da</xroad:id>
        <xroad:protocolVersion>4.0</xroad:protocolVersion>
        <xroad:requestHash algorithmId="http://www.w3.org/2001/04/xmlenc#sha512">
            TpY0dNunEru79Sp4mhqOirAiEWOhPXLOY5jDUib5HmF/
            3c5ayq2q44+0XJd49LsthLUq+2kI/Kp4/1ESuwr6Nw==
        </xroad:requestHash>
    </SOAP-ENV:Header>
    <SOAP-ENV:Body>
        <xroad:allowedMethodsResponse>
            <xroad:service id:objectType="SERVICE">     
                <id:xRoadInstance>Inst1</id:xRoadInstance>
                <id:memberClass>MemberClass1</id:memberClass>
                <id:memberCode>ProviderId</id:memberCode>
                <id:serviceCode>allowedService</id:serviceCode>
                <id:serviceVersion>v1</id:serviceVersion>
            </xroad:service>
        </xroad:allowedMethodsResponse>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>                       
```
