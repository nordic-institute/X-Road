# Third Party Representation Extension for the X-Road Message Protocol

Version: 1.1  
Doc. ID: PR-THIRDPARTY

## Version history

| Date       | Version | Description                                                               | Author           |
|------------|---------|---------------------------------------------------------------------------|------------------|
|            | 1.0.1   | Initial version                                                           |                  |
| 11.06.2018 | 1.0.2   | Converted to MD and updated references                                    | Jürgen Šuvalov   |
| 17.06.2022 | 1.1     | Copy from `ria-ee/X-Road-EE-docs` to `nordic-institute/X-Road` repository | Petteri Kivimäki |

## Table of Contents

<!-- toc -->

- [X-Road: Third Party Representation Extension](#x-road--third-party-representation-extension)
    - [Version history](#version-history)
    - [Table of Contents](#table-of-contents)
    - [License](#license)
    - [1 Introduction](#1-introduction)
      - [1.1 Terms and abbreviations](#11-terms-and-abbreviations)
      - [1.2 References](#12-references)
    - [2 Format of Messages](#2-format-of-messages)
      - [2.1 Schema Header](#21-schema-header)
      - [2.2 Represented Parties](#22-represented-parties)
      - [2.3 Message Headers](#23-message-headers)
    - [Annex A XML Schema for Representation](#annex-a-xml-schema-for-representation)
    - [Annex B Example WSDL](#annex-b-example-wsdl)
    - [Annex C Example Messages](#annex-c-example-messages)
        - [C.1 Example Request](#c1-example-request)
        - [C.1 Example Response](#c1-example-response)
<!-- tocstop -->

## License

This work is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/.

## 1 Introduction

This specification describes an extension to the X-Road Message Protocol 4.0 \[[PR-MESS](#Ref_PR-MESS)\].

The purpose of this extension is to allow sending of additional information to the X-Road service providers in case when service client represents third party while issuing a query. The query is initiated by a third party and the results are also forwarded to that third party, but the request itself is signed by service client.

The described scenario can be used by MISP and other portals that offer X-Road services to various types of institutions. These institutions may not be X-Road members and even may not be eligible for becoming ones, but the service agreements between service providers and service clients may allow service clients to forward data received from X-Road services to these institutions.

### 1.1 Terms and abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\]

### 1.2 References

| Document ID||
| ------------- |-------------|
| <a name="Ref_PR-MESS"></a>\[PR-MESS\] | [X-Road: Message Protocol v4.0](../pr-mess_x-road_message_protocol.md)
| <a name="Ref_TERMS"></a>\[TA-TERMS\] | [X-Road Terms and Abbreviations](../../terms_x-road_docs.md)

## 2 Format of Messages

This section describes XML-based data formats for expressing the represented parties. The data structures and elements defined in this section are in the namespace `http://x-road.eu/xsd/representation.xsd`. The schema file can be found at [`http://x-road.eu/xsd/representation.xsd`](http://x-road.eu/xsd/representation.xsd). The XML Schema for this extension is also listed in the section [Annex A XML Schema for Representation](#annex-a-xml-schema-for-representation).

### 2.1 Schema Header

The following listing shows the header of the schema definition.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
    elementFormDefault="qualified"
    targetNamespace="http://x-road.eu/xsd/representation.xsd"
    xmlns="http://x-road.eu/xsd/representation.xsd">

</xs:schema>
```

### 2.2 Represented Parties

The `XRoadRepresentedPartyType` complex type is used to describe represented parties. It consists of two elements: `partyClass` and `partyCode`. The `partyCode` element is mandatory and the `partyClass` element is optional.

```xml
<xs:complexType name="XRoadRepresentedPartyType">
    <xs:sequence>
        <xs:element minOccurs="0" ref="partyClass"/>
        <xs:element minOccurs="1" ref="partyCode"/>
    </xs:sequence>
</xs:complexType>
```

Next, the elements used in the `XRoadRepresentedPartyType` are defined. Element `partyClass` is similar to the element `memberClass` described in the X-Road Message Protocol 4.0 \[[PR-MESS](#Ref_PR-MESS)\], but can additionally identify institutions that can not become members of X-Road.

Element `partyCode` is used to uniquely identify represented parties.

```xml
<xs:element name="partyClass" type="xs:string"/>
<xs:element name="partyCode" type="xs:string"/>
```

Finally, the `representedParty` element is defined.

```xml
<xs:element name="representedParty" type="XRoadRepresentedPartyType"/>
```

### 2.3 Message Headers

This section describes the additional SOAP headers that are added by this extension. The header fields are described in [Table 1](#Ref_Supported_header_fields).

<a name="Ref_Supported_header_fields" class="anchor"></a>
Table 1. Supported header fields

Field           | Type                                      | Mandatory /Optional | Description
---------------- | ----------------------------------------- | ----------- | --------------------------------------------------------
representedParty| XRoadRepresentedPartyType                 | O           | Identifies a party that is being represented in a service request


## Annex A XML Schema for Representation

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" elementFormDefault="qualified" targetNamespace="http://x-road.eu/xsd/representation.xsd" 
    xmlns="http://x-road.eu/xsd/representation.xsd">
    <xs:element name="partyClass" type="xs:string">
        <xs:annotation>
            <xs:documentation>Class of the represented party.</xs:documentation>
        </xs:annotation>
    </xs:element>
    <xs:element name="partyCode" type="xs:string">
        <xs:annotation>
            <xs:documentation>Code of the represented party.</xs:documentation>
        </xs:annotation>
    </xs:element>
    <xs:complexType name="XRoadRepresentedPartyType">
        <xs:sequence>
            <xs:element minOccurs="0" ref="partyClass"/>
            <xs:element minOccurs="1" ref="partyCode"/>
        </xs:sequence>
    </xs:complexType>
    <xs:element name="representedParty" type="XRoadRepresentedPartyType">
        <xs:annotation>
            <xs:documentation>Identifies a party that is being
represented in a service request.</xs:documentation>
        </xs:annotation>
    </xs:element>
</xs:schema>
```

## Annex B Example WSDL

```xml
<?xml version="1.0" encoding="UTF-8"?>
<wsdl:definitions xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/" 
    xmlns:xrd="http://x-road.eu/xsd/xroad.xsd" 
    xmlns:tns="http://v6Example.x-road.eu/producer" 
    xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/" 
    xmlns:xsd="http://www.w3.org/2001/XMLSchema" 
    xmlns:repr="http://x-road.eu/xsd/representation.xsd" targetNamespace="http://v6Example.x-road.eu/producer">
    <wsdl:types>
        <schema xmlns="http://www.w3.org/2001/XMLSchema" targetNamespace="http://v6Example.x-road.eu/producer" elementFormDefault="qualified">
            <import namespace="http://x-road.eu/xsd/xroad.xsd" schemaLocation="http://x-road.eu/xsd/xroad.xsd"/>
            <import namespace="http://x-road.eu/xsd/representation.xsd" schemaLocation="http://x-road.eu/xsd/representation.xsd"/>
            <element name="getRandom">
                <complexType>
                    <sequence>
                        <element name="amount" type="positiveInteger">
                            <annotation>
                                <appinfo>
                                    <xrd:title xml:lang="en">Amount of numbers to generate</xrd:title>
                                </appinfo>
                            </annotation>
                        </element>
                    </sequence>
                </complexType>
            </element>
            <element name="getRandomResponse">
                <complexType>
                    <sequence>
                        <element name="randomValues" type="tns:randomValues">
                            <annotation>
                                <appinfo>
                                    <xrd:title xml:lang="en">Array
of random values</xrd:title>
                                </appinfo>
                            </annotation>
                        </element>
                    </sequence>
                </complexType>
            </element>
            <complexType name="randomValues">
                <sequence>
                    <element name="randomValue" type="xsd:decimal" minOccurs="1" maxOccurs="1000">
                        <annotation>
                            <appinfo>
                                <xrd:title xml:lang="en">Random
value</xrd:title>
                            </appinfo>
                        </annotation>
                    </element>
                </sequence>
            </complexType>
        </schema>
    </wsdl:types>
    <wsdl:message name="requestHeader">
        <wsdl:part name="client" element="xrd:client"/>
        <wsdl:part name="service" element="xrd:service"/>
        <wsdl:part name="representedParty" element="repr:representedParty"/>
        <wsdl:part name="id" element="xrd:id"/>
        <wsdl:part name="userId" element="xrd:userId"/>
        <wsdl:part name="requestHash" element="xrd:requestHash"/>
        <wsdl:part name="issue" element="xrd:issue"/>
        <wsdl:part name="protocolVersion" element="xrd:protocolVersion"/>
</wsdl:message>
    <wsdl:message name="getRandom">
        <wsdl:part name="body" element="tns:getRandom"/>
    </wsdl:message>
    <wsdl:message name="getRandomResponse">
        <wsdl:part name="body" element="tns:getRandomResponse"/>
</wsdl:message>
    <wsdl:portType name="v6ExamplePortType">
        <wsdl:operation name="getRandom">
            <wsdl:documentation>
                <xrd:title>Random number generator</xrd:title>
                <xrd:notes>Operation is generating random numbers.</xrd:notes>
                <xrd:techNotes>Operation internally uses /dev/urandom
for random number generation. Operation returns
only up to 1000 random numbers.</xrd:techNotes>
            </wsdl:documentation>
            <wsdl:input message="tns:getRandom"/>
            <wsdl:output message="tns:getRandomResponse"/>
        </wsdl:operation>
    </wsdl:portType>
    <wsdl:binding name="v6ExampleBinding" type="tns:v6ExamplePortType">
        <soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http"/>
        <wsdl:operation name="getRandom">
            <soap:operation soapAction="" style="document"/>
            <xrd:version>v1</xrd:version>
            <wsdl:input>
                <soap:body use="literal"/>
                <soap:header message="tns:requestHeader" part="client" use="literal"/>
                <soap:header message="tns:requestHeader" part="service" use="literal"/>
                <soap:header message="tns:requestHeader" part="representedParty" use="literal"/>
                <soap:header message="tns:requestHeader" part="id" use="literal"/>
                <soap:header message="tns:requestHeader" part="userId" use="literal"/>
                <soap:header message="tns:requestHeader" part="issue" use="literal"/>
                <soap:header message="tns:requestHeader" part="protocolVersion" use="literal"/>
            </wsdl:input>
            <wsdl:output>
                <soap:body use="literal"/>
                <soap:header message="tns:requestHeader" part="client" use="literal"/>
                <soap:header message="tns:requestHeader" part="service" use="literal"/>
                <soap:header message="tns:requestHeader" part="representedParty" use="literal"/>
                <soap:header message="tns:requestHeader" part="id" use="literal"/>
                <soap:header message="tns:requestHeader" part="userId" use="literal"/>
                <soap:header message="tns:requestHeader" part="requestHash" use="literal"/>
                <soap:header message="tns:requestHeader" part="issue" use="literal"/>
                <soap:header message="tns:requestHeader" part="protocolVersion" use="literal"/>
</wsdl:output>
        </wsdl:operation>
    </wsdl:binding>
    <wsdl:service name="v6ExampleService">
        <wsdl:port binding="tns:v6ExampleBinding" name="v6ExampleServicePort">
            <soap:address location="http://INSERT_CORRECT_SERVICE_URL"/>
</wsdl:port>
    </wsdl:service>
</wsdl:definitions>
```

## Annex C Example Messages

### C.1 Example Request

```xml
<?xml version="1.0" encoding="UTF-8"?>
<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" 
    xmlns:xrd="http://x-road.eu/xsd/xroad.xsd" 
    xmlns:id="http://x-road.eu/xsd/identifiers" 
    xmlns:repr="http://x-road.eu/xsd/representation.xsd">
    <SOAP-ENV:Header>
        <xrd:client id:objectType="SUBSYSTEM">
            <id:xRoadInstance>EE</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>MEMBER1</id:memberCode>
            <id:subsystemCode>SUBSYSTEM1</id:subsystemCode>
        </xrd:client>
        <xrd:service id:objectType="SERVICE">
            <id:xRoadInstance>EE</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>MEMBER2</id:memberCode>
            <id:subsystemCode>SUBSYSTEM2</id:subsystemCode>
            <id:serviceCode>getRandom</id:serviceCode>
            <id:serviceVersion>v1</id:serviceVersion>
        </xrd:service>
        <repr:representedParty>
            <repr:partyClass>COM</repr:partyClass>
            <repr:partyCode>MEMBER3</repr:partyCode>
        </repr:representedParty>
        <xrd:userId>EE1234567890</xrd:userId>
        <xrd:id>4894e35d-bf0f-44a6-867a-8e51f1daa7e0</xrd:id>
        <xrd:protocolVersion>4.0</xrd:protocolVersion>
    </SOAP-ENV:Header>
    <SOAP-ENV:Body>
        <ns1:getRandom xmlns:ns1="http://v6Example.x-road.eu/producer">
            <amount>2</amount>
        </ns1:getRandom>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```

### C.1 Example Response

```xml
<?xml version="1.0" encoding="UTF-8"?>
<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" 
    xmlns:xrd="http://x-road.eu/xsd/xroad.xsd" 
    xmlns:id="http://x-road.eu/xsd/identifiers" 
    xmlns:repr="http://x-road.eu/xsd/representation.xsd">
    <SOAP-ENV:Header>
        <xrd:client id:objectType="SUBSYSTEM">
            <id:xRoadInstance>EE</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>MEMBER1</id:memberCode>
            <id:subsystemCode>SUBSYSTEM1</id:subsystemCode>
        </xrd:client>
        <xrd:service id:objectType="SERVICE">
            <id:xRoadInstance>EE</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>MEMBER2</id:memberCode>
            <id:subsystemCode>SUBSYSTEM2</id:subsystemCode>
            <id:serviceCode>getRandom</id:serviceCode>
            <id:serviceVersion>v1</id:serviceVersion>
        </xrd:service>
        <repr:representedParty>
            <repr:partyClass>COM</repr:partyClass>
            <repr:partyCode>MEMBER3</repr:partyCode>
        </repr:representedParty>
        <xrd:userId>EE1234567890</xrd:userId>
        <xrd:id>4894e35d-bf0f-44a6-867a-8e51f1daa7e0</xrd:id>
        <xrd:protocolVersion>4.0</xrd:protocolVersion>
        <xrd:requestHash algorithmId="http://www.w3.org/2001/04/xmlenc#sha512">WJGPAGv7 AebB+yhYgYjkqzsSOjCMf+kvDmMVvq0RiLOyjm8IVxxI1aB31OJG+SoYyv AngBYqP34Pt1CjJ4nTJQ==</xrd:requestHash>
    </SOAP-ENV:Header>
    <SOAP-ENV:Body>
        <xxprod:getRandomResponse xmlns:xxprod="http://v6Example.x-road.eu/producer">
            <randomValues>
                <randomValue>0.123456789</randomValue>
                <randomValue>0.987654321</randomValue>
            </randomValues>
        </xxprod:getRandomResponse>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```
