# Security Token Extension for the X-Road Message Protocol

Version: 1.2  
Doc. ID: PR-SECTOKEN

| Date       | Version | Description                                      | Author           |
|------------|---------|--------------------------------------------------|------------------|
| 20.10.2017 | 1.0     | Initial version                                  | Olli Lindgren    |
| 06.03.2018 | 1.1     | Added terms section, term doc reference and link | Tatu Repo        | 
| 17.06.2022 | 1.2     | Update document title                            | Petteri Kivimäki | 

## Table of Contents

<!-- toc -->

- [License](#license)
- [1 Introduction](#1-introduction)
  * [1.1 Terms and abbreviations](#11-terms-and-abbreviations)
  * [1.2 References](#12-references)
- [2 Format of messages](#2-format-of-messages)
  * [2.1 Schema header](#21-schema-header)
  * [2.2 Added `securityToken` element](#22-added-securitytoken-element)
  * [2.3 JSON Web Tokens and the `securityToken` attribute `tokenType`](#23-json-web-tokens-and-the-securitytoken-attribute-tokentype)
  * [2.4 Message headers](#24-message-headers)
- [3 X-Road message logging and the security token](#3-x-road-message-logging-and-the-security-token)
- [4 XML Schema for the extension](#4-xml-schema-for-the-extension)
- [5 Examples](#5-examples)
  * [5.1 Request](#51-request)

<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/.


## 1 Introduction

This specification describes an extension of the X-Road protocol for sending loosely defined security tokens as X-Road header data.
The X-Road message protocol version 4.0 \[[PR-MESS](#Ref_PR-MESS)\] already supports sending arbitrary SOAP headers end-to-end. This
extension just provides a common, defined way to deliver security tokens with the X-Road protocol using the `securityToken` element. 

The motivation for the extension was the need to provide a common way to transfer JSON Web Tokens \[[JWT-RFC](#Ref_JWT-RFC)\] over X-Road.
Examples using JWT as payload in the security token can be found in the [Examples](#examples) section. 

### 1.1 Terms and abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\].

### 1.2 References

| Document ID||
| ------------- |-------------|
| <a name="Ref_PR-MESS"></a>\[PR-MESS\] | [X-Road: Message Protocol v4.0](../pr-mess_x-road_message_protocol.md)      |
| <a name="Ref_UG-SS"></a>\[UG-SS\] | [Security Server User guide](../../Manuals/ug-ss_x-road_6_security_server_user_guide.md)      |
| <a name="Ref_UG-SYSPAR"></a>\[UG-SYSPAR\] | [X-Road: System Parameters User Guide](../../Manuals/ug-syspar_x-road_v6_system_parameters.md)      |
| <a name="Ref_JWT-RFC"></a>\[JWT-RFC\] | [Internet Engineering Task Force Request for Comments 7516:  JSON Web Token (JWT)](https://tools.ietf.org/html/rfc7519)      |
| <a id="Ref_TERMS" class="anchor"></a>\[TA-TERMS\] | [X-Road Terms and Abbreviations](../../terms_x-road_docs.md)

## 2 Format of messages

This section describes the XML format for expressing the security token. The data
structures and elements defined in this section are in the namespace `http://x-road.eu/xsd/security-token.xsd`. The
schema file can be found at [`http://x-road.eu/xsd/security-token.xsd`](http://x-road.eu/xsd/security-token.xsd).
The XML Schema for this extension is also listed in the section [XML Schema for the extension](#xml-schema-for-the-extension).

Note that at the moment, there is no unifying schema that would combine the message protocol and this extension under
the same namespace. That means there is no single schema that would validate an X-Road message with this extension in use.


### 2.1 Schema header

The following listing shows the header of the schema definition

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema
    elementFormDefault="qualified"
    targetNamespace="http://x-road.eu/xsd/security-token.xsd"
    xmlns="http://x-road.eu/xsd/security-token.xsd"
    xmlns:xs="http://www.w3.org/2001/XMLSchema">

</xs:schema>

```

### 2.2 Added `securityToken` element
A new `securityToken` element was added to deliver the security token information.

```xml
<xs:element name="securityToken">
  <xs:complexType>
    <xs:simpleContent>
      <xs:restriction base="xs:string">
        <xs:attribute name="tokenType" type="xs:anyURI"/>
      </xs:restriction>
    </xs:simpleContent>
  </xs:complexType>
  <xs:annotation>
    <xs:documentation>Contains a security token</xs:documentation>
  </xs:annotation>
</xs:element>

```

### 2.3 JSON Web Tokens and the `securityToken` attribute `tokenType`
When transferring JSON Web Tokens, the URI attribute `tokenType` should have the value `urn:ietf:params:oauth:token-type:jwt`
which is the URI content type for JWT content specified by [section 10.2.1.](https://tools.ietf.org/html/rfc7519#section-10.2.1)
of the JSON Web Token RFC \[[JWT-RFC](#Ref_JWT-RFC)\]. However, using this value for the `tokenType` is not enforced in any way. The JWT content is not
currently validated or verified by the security server.



### 2.4 Message headers
 This section describes the additional SOAP headers that are added by this extension.

|Field | Mandatory/Optional | Description |
|-------------|-------------|-------------|
| securityToken | Optional | The security token |


## 3 X-Road message logging and the security token
By default, if the message logging add-on (package `xroad-addon-messagelog`) is installed on a security server, all X-Road SOAP messages are logged
with all their SOAP headers, including the security token. You can read more about the message logging in [Chapter 11](../../Manuals/ug-ss_x-road_6_security_server_user_guide.md#11-message-log)
of the  the Security Server User Guide \[[UG-SS](#Ref_UG-SS)\].

In case the security token contains sensitive data that should not be logged, the message logging can be configured to not log the SOAP body, which also drops the `securityToken` SOAP header. You can
read more about the SOAP body logging options in the [Message log add-on parameters section](../../Manuals/ug-syspar_x-road_v6_system_parameters.md#message-log-add-on-parameters-message-log) of the X-Road System Parameters User
Guide \[[UG-SYSPAR](#Ref_UG-SYSPAR)\].

## 4 XML Schema for the extension
The XML Schema for the extension is below. It can also be found at [`http://x-road.eu/xsd/security-token.xsd`](http://x-road.eu/xsd/security-token.xsd) and locally [here](./security-token.xsd).
 ```xml
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema elementFormDefault="qualified"
    targetNamespace="http://x-road.eu/xsd/security-token.xsd"
    xmlns="http://x-road.eu/xsd/security-token.xsd"
    xmlns:xs="http://www.w3.org/2001/XMLSchema">

<!-- Header elements -->
<xs:element name="securityToken">
  <xs:complexType>
    <xs:simpleContent>
      <xs:restriction base="xs:string">
        <xs:attribute name="tokenType" type="xs:anyURI"/>
      </xs:restriction>
    </xs:simpleContent>
  </xs:complexType>
  <xs:annotation>
    <xs:documentation>Contains a security token</xs:documentation>
  </xs:annotation>
</xs:element>
</xs:schema>

```

## 5 Examples
Below is an example request using a JSON Web Token as the security token.

### 5.1 Request
```xml
<soapenv:Envelope
    xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
    xmlns:xro="http://x-road.eu/xsd/xroad.xsd"
    xmlns:iden="http://x-road.eu/xsd/identifiers"
    xmlns:prod="http://example.org/provider"
    xmlns:ext="http://x-road.eu/xsd/security-token.xsd">
    <soapenv:Header>
        <xro:protocolVersion>4.0</xro:protocolVersion>
        <xro:id>ID11234</xro:id>
        <xro:client iden:objectType="SUBSYSTEM">
            ...
        </xro:client>
        <xro:service iden:objectType="SERVICE">
            ...
            <iden:serviceCode>service</iden:serviceCode>
        </xro:service>
        <ext:securityToken tokenType="urn:ietf:params:oauth:token-type:jwt">eyJhbGciOiJIUzI1NiJ9.eyJuYW1lIjoiVGVzdCJ9.negHPJEwkKcNcgVC6dNtzPZk_48Kig6IzxnabL9jKsw</ext:securityToken>
    </soapenv:Header>
    <soapenv:Body>
        <prod:service>
            ...
        </prod:service>
    </soapenv:Body>
</soapenv:Envelope>
```
