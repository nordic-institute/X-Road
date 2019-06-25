![](img/eu_regional_development_fund_horizontal_div_15.png "European Union | European Regional Development Fund | Investing in your future")

---

# X-Road: Service Metadata Protocol
**Technical Specification**

Version: 2.7  
Doc. ID: PR-META

---

## Version history

 Date       | Version | Description                                                     | Author
 ---------- | ------- | --------------------------------------------------------------- | --------------------
 04.08.2015 | 0.8     | Initial version                                                 | Siim Annuk
 09.09.2015 | 1.0     | Editorial changes made                                          | Imbi Nõgisto
 15.09.2015 | 1.1     | Made minor fixes to schemas                                     | Margus Freudenthal
 16.09.2015 | 2.0     | Final version                                                   | Imbi Nõgisto
 12.10.2015 | 2.1     | Updated identifier names and WSDL examples                      | Ilja Kromonov
 23.08.2017 | 2.1.6   | Converted to Markdown and added endpoint address replacement    | Janne Mattila
 02.01.2018 | 2.2     | Update getWsdl metaservice description                          | Ilkka Seppälä
 04.01.2018 | 2.3     | Updated descriptions and subsystem requirements for meta-services | Tatu Repo
 30.01.2018 | 2.4     | Updated metaservices wsdl                                       | Jarkko Hyöty
 06.03.2018 | 2.5     | Added terms section, terms doc reference and link               | Tatu Repo
 15.10.2018 | 2.6     | Update Annex B                                                  | Petteri Kivimäki
 05.06.2019 | 2.7     | Add JSON response for listClients metadata API                  | Jarkko Hyöty

## Table of Contents

<!-- toc -->
<!-- vim-markdown-toc GFM -->

* [License](#license)
* [1 Introduction](#1-introduction)
  * [1.1 Terms and abbreviations](#11-terms-and-abbreviations)
  * [1.2 References](#12-references)
* [2 Retrieving List of Service Providers](#2-retrieving-list-of-service-providers)
* [3 Retrieving List of Central Services](#3-retrieving-list-of-central-services)
* [4 Retrieving List of Services](#4-retrieving-list-of-services)
* [5 Retrieving the WSDL of a Service](#5-retrieving-the-wsdl-of-a-service)
    * [X-Road protocol POST-request](#x-road-protocol-post-request)
    * [HTTP GET-request](#http-get-request)
    * [WSDL-information modifications](#wsdl-information-modifications)
* [Annex A XML Schema for Messages](#annex-a-xml-schema-for-messages)
* [Annex B listMethods, allowedMethods, and getWsdl service descriptions](#annex-b-listmethods-allowedmethods-and-getwsdl-service-descriptions)
  * [WSDL](#wsdl)
  * [OpenAPI definition](#openapi-definition)
* [Annex C Example Messages](#annex-c-example-messages)
  * [C.1 listClients Response](#c1-listclients-response)
    * [XML Response](#xml-response)
    * [JSON Response](#json-response)
  * [C.2 listCentralServices Response](#c2-listcentralservices-response)
  * [C.3 listMethods Request](#c3-listmethods-request)
  * [C.4 listMethods Response](#c4-listmethods-response)
  * [C.5 allowedMethods Request](#c5-allowedmethods-request)
  * [C.6 allowedMethods Response](#c6-allowedmethods-response)
  * [C.7 getWsdl Request](#c7-getwsdl-request)
  * [C.8 getWsdl Response](#c8-getwsdl-response)
  * [C.9 getWsdl Response attachment](#c9-getwsdl-response-attachment)

<!-- vim-markdown-toc -->
<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/

## 1 Introduction

This specification describes methods that can be used by X-Road participants to discover what services are available to them and download the WSDL files describing these services. The X-Road service metadata protocol is intended to support portals and other software that can discover the available services and then automatically generate user interfaces based on their descriptions. In order to accomplish this, the portal can use the following steps.

1. Download a list of X-Road members and subsystems (see Section [2](#2-retrieving-list-of-service-providers)). This results in a list of (potential) service provides who can be further be queried. Alternatively, the portal can download a list of central services (see Section [3](#3-retrieving-list-of-central-services)) defined in the X-Road central server.

2. Connect to the service provider and acquire a list of services offered by this provider (see Section [4](#4-retrieving-list-of-services)). This service has two forms: `listMethods` returns a list of services provided by a given service provider, `allowedMethods` constrains the returned list by only including services that are allowed for the client.

3. Download the description of the service in WSDL format (see Section [5](#5-retrieving-the-wsdl-of-a-service)).

This specification is based on the X-Road protocol \[[PR-MESS](#Ref_PR-MESS)\]. The X-Road protocol specification also defines important concepts used in this text (for example, central service and X-Road identifier). Because this protocol uses HTTP and X-Road protocol as transport mechanisms, the details of message transport and error conditions are not described in this specification.

Chapters [2](#2-retrieving-list-of-service-providers), [3](#3-retrieving-list-of-central-services), [4](#4-retrieving-list-of-services) and [5](#5-retrieving-the-wsdl-of-a-service) together with annexes [A](#annex-a-xml-schema-for-messages) and [B](#annex-b-listmethods-allowedmethods-and-getwsdl-service-descriptions) contain normative information. All the other sections are informative in nature. All the references are normative.

This specification does not include option for partially implementing the protocol – the conformant implementation must implement the entire specification.

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in this document (in uppercase, as shown) are to be interpreted as described in \[[RFC2119](#Ref_RFC2119)\].

### 1.1 Terms and abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\].

### 1.2 References

1. <a name="Ref_PR-MESS" class="anchor"></a>\[PR-MESS\] Cybernetica AS. X-Road: Message Protocol v4.0. Document ID:
[PR-MESS](pr-mess_x-road_message_protocol.md).
2. <a name="Ref_RFC2119" class="anchor"></a>\[RFC2119\] Key words for use in RFCs to Indicate Requirement Levels, Internet Engineering Task Force, 1997,
[https://www.ietf.org/rfc/rfc2119.txt](https://www.ietf.org/rfc/rfc2119.txt)
3. <a name="Ref_UG-SYSPAR" class="anchor"></a>\[UG-SYSPAR\] X-Road: System Parameters User Guide. Document ID:
[UG-SYSPAAR](../Manuals/ug-syspar_x-road_v6_system_parameters.md).
4. <a id="Ref_TERMS" class="anchor"></a>\[TA-TERMS\] X-Road Terms and Abbreviations. Document ID: [TA-TERMS](../terms_x-road_docs.md).

## 2 Retrieving List of Service Providers

Security server clients can retrieve a list of all the potential service providers (i.e., members and subsystems) of an X-Road instance. This can be accomplished by making a HTTP GET request to the security server. The request URL is `http://SECURITYSERVER/listClients` or `https://SECURITYSERVER/listClients` depending on whether the HTTPS protocol is configured for interaction between the security server and the information system. When making the request, the address `SECURITYSERVER` must be replaced with the actual address of the security server.
In addition, it is possible to retrieve a list of clients in other, federated X-Road instances by adding the following HTTP parameter:

* `xRoadInstance` – code that identifies the X-Road instance.

Thus, in order to retrieve a list of clients defined in the X-Road instance `AA`, the request URL is `http://SECURITYSERVER/listClients?xRoadInstance=AA`.

It is possible to control the response content type using HTTP `Accept` header. If the header value is `application/json`, the security server must produce an application/json response, as defined in Annex B, [OpenAPI definition](#openapi-definition). Otherwise, security server MUST respond with content-type `text/xml` and the response MUST contain the `clientList` XML element defined in Annex [A](#annex-a-xml-schema-for-messages)).

Annex [C.1](#c1-listclients-response) contains an example XML and JSON response messages

The X-Road client identifier has a hierarchical structure consisting of X-Road instance, member class, member and (optionally) subsystem codes. See specification \[[PR-MESS](#Ref_PR-MESS)\] for explanation and specification of identifiers.

## 3 Retrieving List of Central Services

Security server clients can retrieve a list of all central services defined in an X-Road instance. This can be accomplished by making a HTTP GET request to the security server.
The request URL is `http://SECURITYSERVER/listCentralServices` or `https://SECURITYSERVER/listCentralServices` depending on whether the HTTPS protocol is configured for interaction between the security server and the information system.
When making the request, the address `SECURITYSERVER` must be replaced with the actual address of the security server.

In addition, it is possible to retrieve a list of security servers in other, federated X-Road instances by adding the following HTTP parameter:

* `xRoadInstance` – code that identifies the X-Road instance.

Thus, in order to retrieve a list of central services defined in X-Road instance `AA`, the
request URL is `http://SECURITYSERVER/listCentralServices?xRoadInstance=AA`.

Security server MUST respond with content-type `text/xml` and the response MUST contain the
`centralServiceList` XML element defined below
(full XML schema appears in Annex [A](#annex-a-xml-schema-for-messages)).
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
The body of the request MUST contain an appropriately named empty XML element (either `listMethods` or `allowedMethods`).
Annexes [C.3](#c3-listmethods-request) and [C.5](#c5-allowedmethods-request) contain example request messages for services, respectively.

The body of the response message MUST contain a list of services provided by the service provider (in case of listMethods) or open to the given client (in case of allowedMethods). The response SHALL NOT contain names of the metainfo services. The following snippet contains XML schema of the response body.
Annexes [C.4](#c4-listmethods-response) and [C.6](#c6-allowedmethods-response) contain example response messages for listMethods and allowedMethods services, respectively.
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

## 5 Retrieving the WSDL of a Service

Service clients are able to download WSDL-files that contain the definition of a given service by using the `getWsdl` meta-service. This can be accomplished by either sending the client security server an X-Road protocol POST-request or a parametrized HTTP GET-request.

#### X-Road protocol POST-request

  * the standard method for retrieving the WSDL
  * uses the connection type settings of the client subsystem
  * WSDL is retrieved as a SOAP-attachment

An example of a `getWsdl` X-Road protocol POST-request to the client security server is documented in annex [C.7](#c7-getwsdl-request) and the corresponding response in annexes [C.8](#c8-getwsdl-response) and [C.9](#c9-getwsdl-response-attachment).

#### HTTP GET-request

  * a convenience method for retrieving the WSDL that will be phased out in future releases
  * disabled by default in new `6.17.x` installations, availability is configured by the `allow-get-wsdl-request` system parameter \[[UG-SYSPAR](#Ref_UG-SYSPAR)\]
  * uses the connection type settings of the owner member on the client security server
  * WSDL is retrieved in the response body.

The URL for the HTTP GET-request is either `http://SECURITYSERVER/wsdl` or `https://SECURITYSERVER/wsdl` depending on the connection type settings for the client owner member. When making the request, the address `SECURITYSERVER` must be replaced with the actual address of the client security server. The client MUST specify the identifier of the service using the following HTTP-parameters:

* `xRoadInstance` – code that identifies the X-Road instance;

* `memberClass` – code that identifies the member class;

* `memberCode` – code that identifies the X-Road member;

* `subsystemCode` – (optional) code that identifies a subsystem of the given member;

* `serviceCode` – identifies the specific service;

* `version` – version of the service.

Therefore, an example HTTP GET-request URL would be:
`http://SECURITYSERVER/wsdl?xRoadInstance=Inst1&memberClass=MemberClass1&memberCode=ProviderId&subsystemCode=Subsystem1&serviceCode=service1&version=v1`

All the special symbols (such as spaces, question marks etc.) in X-Road element names MUST be escaped.

WSDL files for central services are accessed in a similar manner, in this case the query parameters MUST be:

* `xRoadInstance` – code that identifies the X-Road instance;

* `serviceCode` – code that identifies the central service.

The resulting HTTP GET-request URL for a central service WSDL would be:
`http://SECURITYSERVER/wsdl?xRoadInstance=Inst1&serviceCode=centralservice1`


#### WSDL-information modifications

Security server MUST replace endpoint location with value `http://example.org/xroad-endpoint`.
This is done for security reasons, to hide the endpoint addresses which often point
to information systems which should be hidden from the clients, and be accessed only through
the provider security server.

For example service definition

```xml
    <wsdl:service name="testService">
        <wsdl:port binding="tns:testServiceBinding" name="testServicePort">
            <soap:address location="http://some-server.company.com:8080/testService/Endpoint"/>
        </wsdl:port>
    </wsdl:service>
```

becomes

```xml
    <wsdl:service name="testService">
        <wsdl:port binding="tns:testServiceBinding" name="testServicePort">
            <soap:address location="http://example.org/xroad-endpoint"/>
        </wsdl:port>
    </wsdl:service>
```

when retrieved through the meta-service.

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

## Annex B listMethods, allowedMethods, and getWsdl service descriptions

### WSDL
```xml
<?xml version="1.0" encoding="UTF-8"?>
<wsdl:definitions targetNamespace="http://x-road.eu/xsd/xroad.xsd"
    xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/"
    xmlns:xrd="http://x-road.eu/xsd/xroad.xsd"
    xmlns:id="http://x-road.eu/xsd/identifiers"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
    xmlns:mime="http://schemas.xmlsoap.org/wsdl/mime/">
    <wsdl:types>
        <xs:schema targetNamespace="http://x-road.eu/xsd/xroad.xsd" elementFormDefault="qualified">
            <xs:include schemaLocation="http://x-road.eu/xsd/xroad.xsd" />
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
                        <xs:element maxOccurs="unbounded" minOccurs="0" name="service"
                            type="id:XRoadServiceIdentifierType" />
                    </xs:sequence>
                </xs:complexType>
            </xs:element>
            <xs:element name="allowedMethodsResponse">
                <xs:complexType>
                    <xs:sequence>
                        <xs:element maxOccurs="unbounded" minOccurs="0" name="service"
                            type="id:XRoadServiceIdentifierType" />
                    </xs:sequence>
                </xs:complexType>
            </xs:element>
            <xs:element name="getWsdl">
                <xs:complexType>
                    <xs:sequence>
                        <xs:element name="serviceCode" type="xs:string"/>
                        <xs:element name="serviceVersion" type="xs:string" minOccurs="0"/>
                    </xs:sequence>
                </xs:complexType>
            </xs:element>
            <xs:element name="getWsdlResponse">
                <xs:complexType>
                    <xs:sequence>
                        <xs:element name="serviceCode" type="xs:string"/>
                        <xs:element name="serviceVersion" type="xs:string" minOccurs="0"/>
                    </xs:sequence>
                </xs:complexType>
            </xs:element>
        </xs:schema>
    </wsdl:types>

    <wsdl:message name="listMethods">
            <wsdl:part name="listMethods" element="xrd:listMethods"/>

            <wsdl:part name="client" element="xrd:client"/>
            <wsdl:part name="service" element="xrd:service"/>
            <wsdl:part name="userId" element="xrd:userId"/>
            <wsdl:part name="id" element="xrd:id"/>
            <wsdl:part name="protocolVersion" element="xrd:protocolVersion"/>
     </wsdl:message>

    <wsdl:message name="listMethodsResponse">
        <wsdl:part name="listMethodsResponse" element="xrd:listMethodsResponse"/>

        <wsdl:part name="client" element="xrd:client"/>
        <wsdl:part name="service" element="xrd:service"/>
        <wsdl:part name="userId" element="xrd:userId"/>
        <wsdl:part name="id" element="xrd:id"/>
        <wsdl:part name="protocolVersion" element="xrd:protocolVersion"/>
    </wsdl:message>

    <wsdl:message name="allowedMethods">
        <wsdl:part name="allowedMethods" element="xrd:allowedMethods"/>

        <wsdl:part name="client" element="xrd:client"/>
        <wsdl:part name="service" element="xrd:service"/>
        <wsdl:part name="userId" element="xrd:userId"/>
        <wsdl:part name="id" element="xrd:id"/>
        <wsdl:part name="protocolVersion" element="xrd:protocolVersion"/>
    </wsdl:message>

    <wsdl:message name="allowedMethodsResponse">
        <wsdl:part name="allowedMethodsResponse" element="xrd:allowedMethodsResponse"/>

        <wsdl:part name="client" element="xrd:client"/>
        <wsdl:part name="service" element="xrd:service"/>
        <wsdl:part name="userId" element="xrd:userId"/>
        <wsdl:part name="id" element="xrd:id"/>
        <wsdl:part name="protocolVersion" element="xrd:protocolVersion"/>
    </wsdl:message>

    <wsdl:message name="getWsdl">
        <wsdl:part name="getWsdl" element="xrd:getWsdl"/>

        <wsdl:part name="client" element="xrd:client"/>
        <wsdl:part name="service" element="xrd:service"/>
        <wsdl:part name="userId" element="xrd:userId"/>
        <wsdl:part name="id" element="xrd:id"/>
        <wsdl:part name="protocolVersion" element="xrd:protocolVersion"/>
    </wsdl:message>

    <wsdl:message name="getWsdlResponse">
        <wsdl:part name="getWsdlResponse" element="xrd:getWsdlResponse"/>
        <!-- the wsdl is returned as an attachment -->
        <wsdl:part name="wsdl" type="xs:base64Binary"/>

        <wsdl:part name="client" element="xrd:client"/>
        <wsdl:part name="service" element="xrd:service"/>
        <wsdl:part name="userId" element="xrd:userId"/>
        <wsdl:part name="id" element="xrd:id"/>
        <wsdl:part name="protocolVersion" element="xrd:protocolVersion"/>
    </wsdl:message>

    <wsdl:portType name="metaServicesPort">
        <wsdl:operation name="allowedMethods">
            <wsdl:documentation>
                <xrd:title>allowedMethods</xrd:title>
            </wsdl:documentation>
            <wsdl:input name="allowedMethods" message="xrd:allowedMethods"/>
            <wsdl:output name="allowedMethodsResponse" message="xrd:allowedMethodsResponse"/>
        </wsdl:operation>
        <wsdl:operation name="listMethods">
            <wsdl:documentation>
                <xrd:title>listMethods</xrd:title>
            </wsdl:documentation>
            <wsdl:input name="listMethods" message="xrd:listMethods"/>
            <wsdl:output name="listMethodsResponse" message="xrd:listMethodsResponse"/>
        </wsdl:operation>
        <wsdl:operation name="getWsdl">
            <wsdl:input message="xrd:getWsdl" name="getWsdl"/>
            <wsdl:output message="xrd:getWsdlResponse" name="getWsdlResponse"/>
        </wsdl:operation>
    </wsdl:portType>

    <wsdl:binding name="metaServicesPortSoap11" type="xrd:metaServicesPort">
        <soap:binding style="document"
                      transport="http://schemas.xmlsoap.org/soap/http"/>
        <wsdl:operation name="allowedMethods">
            <soap:operation soapAction=""/>
            <wsdl:input name="allowedMethods">
                <soap:body parts="allowedMethods" use="literal"/>
                <soap:header message="xrd:allowedMethods" part="client" use="literal"/>
                <soap:header message="xrd:allowedMethods" part="service" use="literal"/>
                <soap:header message="xrd:allowedMethods" part="userId" use="literal"/>
                <soap:header message="xrd:allowedMethods" part="id" use="literal"/>
                <soap:header message="xrd:allowedMethods" part="protocolVersion" use="literal"/>
            </wsdl:input>
            <wsdl:output name="allowedMethodsResponse">
                <soap:body parts="allowedMethodsResponse" use="literal"/>
                <soap:header message="xrd:allowedMethodsResponse" part="client" use="literal"/>
                <soap:header message="xrd:allowedMethodsResponse" part="service" use="literal"/>
                <soap:header message="xrd:allowedMethodsResponse" part="userId" use="literal"/>
                <soap:header message="xrd:allowedMethodsResponse" part="id" use="literal"/>
                <soap:header message="xrd:allowedMethodsResponse" part="protocolVersion" use="literal"/>
            </wsdl:output>
        </wsdl:operation>
        <wsdl:operation name="listMethods">
            <soap:operation soapAction=""/>
            <wsdl:input name="listMethods">
                <soap:body parts="listMethods" use="literal"/>
                <soap:header message="xrd:listMethods" part="client" use="literal"/>
                <soap:header message="xrd:listMethods" part="service" use="literal"/>
                <soap:header message="xrd:listMethods" part="userId" use="literal"/>
                <soap:header message="xrd:listMethods" part="id" use="literal"/>
                <soap:header message="xrd:listMethods" part="protocolVersion" use="literal"/>
            </wsdl:input>
            <wsdl:output name="listMethodsResponse">
                <soap:body parts="listMethodsResponse" use="literal"/>
                <soap:header message="xrd:listMethodsResponse" part="client" use="literal"/>
                <soap:header message="xrd:listMethodsResponse" part="service" use="literal"/>
                <soap:header message="xrd:listMethodsResponse" part="userId" use="literal"/>
                <soap:header message="xrd:listMethodsResponse" part="id" use="literal"/>
                <soap:header message="xrd:listMethodsResponse" part="protocolVersion" use="literal"/>
            </wsdl:output>
        </wsdl:operation>
        <wsdl:operation name="getWsdl">
            <soap:operation soapAction=""/>
            <wsdl:input name="getWsdl">
                <soap:body parts="getWsdl" use="literal"/>
                <soap:header message="xrd:getWsdl" part="client" use="literal"/>
                <soap:header message="xrd:getWsdl" part="service" use="literal"/>
                <soap:header message="xrd:getWsdl" part="userId" use="literal"/>
                <soap:header message="xrd:getWsdl" part="id" use="literal"/>
                <soap:header message="xrd:getWsdl" part="protocolVersion" use="literal"/>
            </wsdl:input>
            <wsdl:output name="getWsdlResponse">
                <mime:multipartRelated>
                    <mime:part>
                        <soap:body parts="getWsdlResponse" use="literal"/>
                        <soap:header message="xrd:getWsdlResponse" part="client" use="literal"/>
                        <soap:header message="xrd:getWsdlResponse" part="service" use="literal"/>
                        <soap:header message="xrd:getWsdlResponse" part="userId" use="literal"/>
                        <soap:header message="xrd:getWsdlResponse" part="id" use="literal"/>
                        <soap:header message="xrd:getWsdlResponse" part="protocolVersion" use="literal"/>
                    </mime:part>
                    <mime:part>
                        <mime:content part="wsdl" type="text/xml"/>
                    </mime:part>
                </mime:multipartRelated>
            </wsdl:output>
        </wsdl:operation>
    </wsdl:binding>

    <wsdl:service name="producerPortService">
        <wsdl:port name="metaServicesPortSoap11"
            binding="xrd:metaServicesPortSoap11">
            <soap:address location="https://SECURITYSERVER/" />
        </wsdl:port>
    </wsdl:service>
</wsdl:definitions>
```

### OpenAPI definition

```yaml
openapi: 3.0.0
info:
  title: X-Road Service Metadata API
  version: '2.7'
servers:
  - url: 'https://{securityserver}/'
paths:
  /listClients:
    get:
      tags:
        - metaservices
      summary: List clients defined in the X-Road instance
      operationId: listClients
      parameters:
        - name: xRoadInstance
          in: query
          schema:
            type: string
      responses:
        '200':
          description: List of clients
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/clientList'
  /listCentralServices:
    get:
      tags:
        - metaservices
      summary: List central services defined in the X-Road instance
      operationId: listCentralServices
      parameters:
        - name: xRoadInstance
          in: query
          schema:
            type: string
      responses:
        '200':
          description: List of central services
components:
  schemas:
    clientList:
      type: object
      properties:
        member:
          type: array
          items:
            $ref: '#/components/schemas/xroadIdentifier'
    xroadIdentifier:
      type: object
      properties:
        name:
          type: string
        id:
          type: object
          properties:
            object_type:
              type: string
              enum:
                - MEMBER
                - SUBSYSTEM
                - SERVER
                - GLOBALGROUP
                - LOCALGROUP
                - SERVICE
                - CENTRALSERVICE
            xroad_instance:
              type: string
            member_class:
              type: string
            member_code:
              type: string
            subsystem_code:
              type: string
```

## Annex C Example Messages

### C.1 listClients Response

#### XML Response
`curl http://SECURITYSERVER/listClients`

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

#### JSON Response
`curl -H "Accept: application/json" http://SECURITYSERVER/listClients`

```json
{
  "member": [
    {
      "id": {
        "member_class": "GOV",
        "member_code": "TS1OWNER",
        "object_type": "MEMBER",
        "xroad_instance": "AA"
      },
      "name": "TS1 Owner"
    },
    {
      "id": {
        "member_class": "GOV",
        "member_code": "TS2OWNER",
        "object_type": "MEMBER",
        "xroad_instance": "AA"
      },
      "name": "TS2 Owner"
    },
    {
      "id": {
        "member_class": "ENT",
        "member_code": "CLIENT1",
        "object_type": "MEMBER",
        "xroad_instance": "AA"
      },
      "name": "Client One"
    },
    {
      "id": {
        "member_class": "ENT",
        "member_code": "CLIENT1",
        "subsystem_code": "sub",
        "object_type": "SUBSYSTEM",
        "xroad_instance": "AA"
      },
      "name": "Client One"
    }
  ]
}
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
            <id:subsystemCode>Subsystem1</id:subsystemCode>
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
            <id:subsystemCode>Subsystem1</id:subsystemCode>
            <id:serviceCode>listMethods</id:serviceCode>
        </xroad:service>
        <xroad:id>411d6755661409fed365ad8135f8210be07613da</xroad:id>
        <xroad:protocolVersion>4.0</xroad:protocolVersion>
        <xroad:requestHash algorithmId="http://www.w3.org/2001/04/xmlenc#sha512">
            Zvs1uF2GW3zdma1r9K9keOGhNPOjCr3TEZNpxfpRCtsq
            qy3ljiLorMZ3e5iNZtX6Ek60xtV12Gue8Mme1ryZmQ==
        </xroad:requestHash>
    </SOAP-ENV:Header>
    <SOAP-ENV:Body>
        <xroad:listMethodsResponse>
            <xroad:service id:objectType="SERVICE">
                <id:xRoadInstance>Inst1</id:xRoadInstance>
                <id:memberClass>MemberClass1</id:memberClass>
                <id:memberCode>ProviderId</id:memberCode>
                <id:subsystemCode>Subsystem1</id:subsystemCode>
                <id:serviceCode>allowedService</id:serviceCode>
                <id:serviceVersion>v1</id:serviceVersion>
            </xroad:service>
            <xroad:service id:objectType="SERVICE">
                <id:xRoadInstance>Inst1</id:xRoadInstance>
                <id:memberClass>MemberClass1</id:memberClass>
                <id:memberCode>ProviderId</id:memberCode>
                <id:subsystemCode>Subsystem1</id:subsystemCode>
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
            <id:subsystemCode>Subsystem1</id:subsystemCode>
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
            <id:subsystemCode>Subsystem1</id:subsystemCode>
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
                <id:subsystemCode>Subsystem1</id:subsystemCode>
                <id:serviceCode>allowedService</id:serviceCode>
                <id:serviceVersion>v1</id:serviceVersion>
            </xroad:service>
        </xroad:allowedMethodsResponse>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```

### C.7 getWsdl Request
```xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xro="http://x-road.eu/xsd/xroad.xsd"
                  xmlns:iden="http://x-road.eu/xsd/identifiers">
    <soapenv:Header>
        <xro:protocolVersion>4.x</xro:protocolVersion>
        <xro:issue>123</xro:issue>
        <xro:id>123</xro:id>
        <xro:userId>123</xro:userId>
        <xro:service iden:objectType="SERVICE">
            <iden:xRoadInstance>FI</iden:xRoadInstance>
            <iden:memberClass>COM</iden:memberClass>
            <iden:memberCode>111</iden:memberCode>
            <iden:subsystemCode>SUB</iden:subsystemCode>
            <iden:serviceCode>getWsdl</iden:serviceCode>
            <iden:serviceVersion>v1</iden:serviceVersion>
        </xro:service>
        <xro:client iden:objectType="SUBSYSTEM">
            <iden:xRoadInstance>FI</iden:xRoadInstance>
            <iden:memberClass>COM</iden:memberClass>
            <iden:memberCode>111</iden:memberCode>
            <iden:subsystemCode>SUB</iden:subsystemCode>
        </xro:client>
    </soapenv:Header>
    <soapenv:Body>
        <xro:getWsdl>
            <xro:serviceCode>getRandom</xro:serviceCode>
            <xro:serviceVersion>v1</xro:serviceVersion>
        </xro:getWsdl>
    </soapenv:Body>
</soapenv:Envelope>
```

### C.8 getWsdl Response
```xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                  xmlns:iden="http://x-road.eu/xsd/identifiers"
                  xmlns:xro="http://x-road.eu/xsd/xroad.xsd">
    <soapenv:Header>
        <xro:protocolVersion>4.x</xro:protocolVersion>
        <xro:issue>123</xro:issue>
        <xro:id>123</xro:id>
        <xro:requestHash algorithmId="http://www.w3.org/2001/04/xmlenc#sha512">
            BPiSSkxGzJC4piyVjkTRfNRROHI/hQJc1rALJsPAvghMUM0keBXV6QKVIUJPUjDydw2+wadRUkM6MS8vO3Y88w==
        </xro:requestHash>
        <xro:userId>123</xro:userId>
        <xro:service iden:objectType="SERVICE">
            <iden:xRoadInstance>FI</iden:xRoadInstance>
            <iden:memberClass>COM</iden:memberClass>
            <iden:memberCode>111</iden:memberCode>
            <iden:subsystemCode>SUB</iden:subsystemCode>
            <iden:serviceCode>getWsdl</iden:serviceCode>
            <iden:serviceVersion>v1</iden:serviceVersion>
        </xro:service>
        <xro:client iden:objectType="SUBSYSTEM">
            <iden:xRoadInstance>FI</iden:xRoadInstance>
            <iden:memberClass>COM</iden:memberClass>
            <iden:memberCode>111</iden:memberCode>
            <iden:subsystemCode>SUB</iden:subsystemCode>
        </xro:client>
    </soapenv:Header>
    <soapenv:Body>
        <xro:getWsdlResponse>
            <xro:serviceCode>getRandom</xro:serviceCode>
            <xro:serviceVersion>v1</xro:serviceVersion>
        </xro:getWsdlResponse>
    </soapenv:Body>
</soapenv:Envelope>
```

### C.9 getWsdl Response attachment

```xml
<wsdl:definitions name="testService" targetNamespace="http://test.x-road.fi/producer">
    <wsdl:types>
        <xsd:schema elementFormDefault="qualified"
                    targetNamespace="http://test.x-road.fi/producer"><!-- Import X-Road schema -->
            <xsd:import id="xrd" namespace="http://x-road.eu/xsd/xroad.xsd"
                        schemaLocation="http://x-road.eu/xsd/xroad.xsd"/>
            <xsd:element name="getRandom" nillable="true">
                <xsd:complexType/>
            </xsd:element>
            <xsd:element name="getRandomResponse">
                <xsd:complexType>
                    <xsd:sequence>
                        <xsd:element name="request">
                        </xsd:element>
                        <xsd:element name="response">
                            <xsd:complexType>
                                <xsd:sequence>
                                    <xsd:element name="data" type="xsd:string">
                                        <xsd:annotation>
                                            <xsd:documentation>
                                                Service response
                                            </xsd:documentation>
                                        </xsd:annotation>
                                    </xsd:element>
                                </xsd:sequence>
                            </xsd:complexType>
                        </xsd:element>
                    </xsd:sequence>
                </xsd:complexType>
            </xsd:element>
            <xsd:element name="helloService">
                <xsd:complexType>
                    <xsd:sequence>
                        <xsd:element name="request">
                            <xsd:complexType>
                                <xsd:sequence>
                                    <xsd:element name="name" type="xsd:string">
                                        <xsd:annotation>
                                            <xsd:documentation>
                                                Name
                                            </xsd:documentation>
                                        </xsd:annotation>
                                    </xsd:element>
                                </xsd:sequence>
                            </xsd:complexType>
                        </xsd:element>
                    </xsd:sequence>systemCode>
                </xsd:complexType>
            </xsd:element>
            <xsd:element name="helloServiceResponse">
                <xsd:complexType>
                    <xsd:sequence>
                        <xsd:element name="request">
                            <xsd:complexType>
                                <xsd:sequence>
                                    <xsd:element name="name" nillable="true" type="xsd:string"/>
                                </xsd:sequence>
                            </xsd:complexType>
                        </xsd:element>
                        <xsd:element name="response">
                            <xsd:complexType>
                                <xsd:sequence>
                                    <xsd:element name="message" type="xsd:string">
                                        <xsd:annotation>
                                            <xsd:documentation>
                                                Service response
                                            </xsd:documentation>
                                        </xsd:annotation>
                                    </xsd:element>
                                </xsd:sequence>
                            </xsd:complexType>
                        </xsd:element>
                    </xsd:sequence>
                </xsd:complexType>
            </xsd:element>
        </xsd:schema>
    </wsdl:types>
    <wsdl:message name="requestheader">
        <wsdl:part name="client" element="xrd:client"/>
        <wsdl:part name="service" element="xrd:service"/>
        <wsdl:part name="userId" element="xrd:userId"/>
        <wsdl:part name="id" element="xrd:id"/>
        <wsdl:part name="issue" element="xrd:issue"/>
        <wsdl:part name="protocolVersion" element="xrd:protocolVersion"/>
    </wsdl:message>
    <wsdl:message name="getRandom">
        <wsdl:part name="body" element="tns:getRandom"/>
    </wsdl:message>
    <wsdl:message name="getRandomResponse">
        <wsdl:part name="body" element="tns:getRandomResponse"/>
    </wsdl:message>
    <wsdl:message name="helloService">
        <wsdl:part name="body" element="tns:helloService"/>
    </wsdl:message>
    <wsdl:message name="helloServiceResponse">
        <wsdl:part name="body" element="tns:helloServiceResponse"/>
    </wsdl:message>
    <wsdl:portType name="testServicePortType">
        <wsdl:operation name="getRandom">
            <wsdl:input message="tns:getRandom"/>
            <wsdl:output message="tns:getRandomResponse"/>
        </wsdl:operation>
        <wsdl:operation name="helloService">
            <wsdl:input message="tns:helloService"/>
            <wsdl:output message="tns:helloServiceResponse"/>
        </wsdl:operation>
    </wsdl:portType>
    <wsdl:binding name="testServiceBinding" type="tns:testServicePortType">
        <soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http"/>
        <wsdl:operation name="getRandom">
            <soap:operation soapAction="" style="document"/>
            <id:version>v1</id:version>
            <wsdl:input>
                <soap:body parts="body" use="literal"/>
                <soap:header message="tns:requestheader" part="client" use="literal"/>
                <soap:header message="tns:requestheader" part="service" use="literal"/>
                <soap:header message="tns:requestheader" part="userId" use="literal"/>
                <soap:header message="tns:requestheader" part="id" use="literal"/>
                <soap:header message="tns:requestheader" part="issue" use="literal"/>
                <soap:header message="tns:requestheader" part="protocolVersion" use="literal"/>
            </wsdl:input>
            <wsdl:output>
                <soap:body parts="body" use="literal"/>
                <soap:header message="tns:requestheader" part="client" use="literal"/>
                <soap:header message="tns:requestheader" part="service" use="literal"/>
                <soap:header message="tns:requestheader" part="userId" use="literal"/>
                <soap:header message="tns:requestheader" part="id" use="literal"/>
                <soap:header message="tns:requestheader" part="issue" use="literal"/>
                <soap:header message="tns:requestheader" part="protocolVersion" use="literal"/>
            </wsdl:output>
        </wsdl:operation>
        <wsdl:operation name="helloService">
            <soap:operation soapAction="" style="document"/>
            <id:version>v1</id:version>
            <wsdl:input>
                <soap:body parts="body" use="literal"/>
                <soap:header message="tns:requestheader" part="client" use="literal"/>
                <soap:header message="tns:requestheader" part="service" use="literal"/>
                <soap:header message="tns:requestheader" part="userId" use="literal"/>
                <soap:header message="tns:requestheader" part="id" use="literal"/>
                <soap:header message="tns:requestheader" part="issue" use="literal"/>
                <soap:header message="tns:requestheader" part="protocolVersion" use="literal"/>
            </wsdl:input>
            <wsdl:output>
                <soap:body parts="body" use="literal"/>
                <soap:header message="tns:requestheader" part="client" use="literal"/>
                <soap:header message="tns:requestheader" part="service" use="literal"/>
                <soap:header message="tns:requestheader" part="userId" use="literal"/>
                <soap:header message="tns:requestheader" part="id" use="literal"/>
                <soap:header message="tns:requestheader" part="issue" use="literal"/>
                <soap:header message="tns:requestheader" part="protocolVersion" use="literal"/>
            </wsdl:output>
        </wsdl:operation>
    </wsdl:binding>
    <wsdl:service name="testService">
        <wsdl:port binding="tns:testServiceBinding" name="testServicePort">
            <soap:address location="http://example.org/xroad-endpoint"/>
        </wsdl:port>
    </wsdl:service>
</wsdl:definitions>
```
