# X-Road: Protocol for Management Services

**Technical Specification**

Version: 1.15  
Doc. ID: PR-MSERV

| Date       | Version | Description                                                                 | Author             |
|------------|---------|-----------------------------------------------------------------------------|--------------------|
| 19.08.2015 | 0.1     | Initial version                                                             | Martin Lind        |
| 28.08.2015 | 0.2     | Added comments and made editorial changes                                   | Margus Freudenthal |
| 03.09.2015 | 0.3     | Re-structuring and accuracy improvements                                    | Martin Lind        |
| 13.09.2015 | 0.4     | Made editorial changes                                                      | Margus Freudenthal |
| 16.09.2015 | 0.5     | Correct example message for authentication certificate registration request | Martin Lind        |
| 17.09.2015 | 0.6     | Improvements for example messages and referential improvements              | Martin Lind        |
| 17.09.2015 | 0.7     | Improvements for Schema fragments                                           | Martin Lind        |
| 18.09.2015 | 0.8     | Updating Schema in the WSDL                                                 | Martin Lind        |
| 21.09.2015 | 1.0     | Editorial changes made                                                      | Imbi Nõgisto       |
| 21.09.2015 | 1.1     | Document renamed                                                            | Imbi Nõgisto       |
| 01.10.2015 | 1.2     | Field *requestId* added and redundant elements removed                      | Martin Lind        |
| 05.10.2015 | 1.3     | Updated example messages                                                    | Martin Lind        |
| 06.10.2015 | 1.4     | Correct header fields for WSDL                                              | Martin Lind        |
| 17.10.2015 | 1.6     | Editorial changes related to *requestId* field                              | Margus Freudenthal |
| 28.10.2015 | 1.7     | Complete X-Road identifiers schema added                                    | Siim Annuk         |
| 30.10.2015 | 1.8     | Header field *userId* removed from management services WSDL                 | Kristo Heero       |
| 11.12.2015 | 1.9     | Corrected documentation about registering only subsystems                   | Siim Annuk         |
| 07.06.2017 | 1.10    | Additional signature algorithms supported                                   | Kristo Heero       |
| 06.03.2018 | 1.11    | Added terms section, term doc reference and link, fixed references          | Tatu Repo          |
| 06.02.2019 | 1.12    | Update *clientReg* message description                                      | Petteri Kivimäki   |
| 03.06.2019 | 1.13    | Add ownerChange management service                                          | Ilkka Seppälä      |
| 29.06.2019 | 1.14    | Rename *newOwner* element to *client* in ownerChange management service     | Petteri Kivimäki   |
| 10.05.2023 | 1.15    | Security Categories removed.                                                | Justas Samuolis    |

## Table of Contents

  - [License](#license)
  - [1 Introduction](#1-introduction)
    - [1.1 Terms and abbreviations](#11-terms-and-abbreviations)
    - [1.2 References](#12-references)
  - [2 Format of the Messages](#2-format-of-the-messages)
    - [2.1 *clientReg* - Security Server Client Registration](#21-clientreg---security-server-client-registration)
    - [2.2 *clientDeletion* - Security Server Client Deletion](#22-clientdeletion---security-server-client-deletion)
    - [2.3 *authCertReg* - Security Server Authentication Certificate Registration](#23-authcertreg---security-server-authentication-certificate-registration)
    - [2.4 *authCertDeletion* - Security Server Authentication Certificate Deletion](#24-authcertdeletion---security-server-authentication-certificate-deletion)
    - [2.5 *ownerChange* - Security Server Owner Change](#25-ownerchange---security-server-owner-change)
  - [Annex A. Example messages](#annex-a-example-messages)
    - [A.1 clientReg](#a1-clientreg)
    - [A.2 clientDeletion](#a2-clientdeletion)
    - [A.3 authCertReg](#a3-authcertreg)
    - [A.4 authCertDeletion](#a4-authcertdeletion)
    - [A.5 ownerChange](#a5-ownerchange)
  - [Annex B WSDL File for Management Services](#annex-b-wsdl-file-for-management-services)

## License

This work is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/.

## 1 Introduction

Management services are services provided by the X-Road governing organization to manage security servers and security server clients. They are called by security servers to register in central server the configuration changes made by the security server administrator. The management services are the following:

* *clientReg* – registering an X-Road subsystem as a client of the security server;

* *clientDeletion* – removing a client from the security server;

* *authCertReg* – adding an authentication certificate to the security server;

* *authCertDeletion* – removing an authentication certificate from the security server.
  
* *ownerChange* - changing the owner member of the security server.

The management services are implemented as standard X-Road services (see \[[PR-MESS](#Ref_PR-MESS)\] for detailed description of the protocol) that are offered by the X-Road governing authority. The exception is the *authCertReg* service that, for technical reasons, is implemented as HTTPS POST (see below for details).

This protocol builds on existing transport and message encoding mechanisms. Therefore, this specification does not cover the technical details and error conditions related to making HTTPS requests together with processing MIME-encoded messages. These concerns are discussed in detail in their respective standards.

Section 2 as well as [Annex B](#annex-b-wsdl-file-for-management-services), of this specification contain normative information. All the other sections are informative in nature. All the references are normative.

This specification does not include option for partially implementing the protocol – the conformant implementation must implement the entire specification.

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in this document (in uppercase, as shown) are to be interpreted as described in \[[REQUIREMENT](#Ref_REQ)\].

### 1.1 Terms and abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\].

### 1.2 References

- <a name="Ref_REQ"></a>[REQUIREMENT] Key words for use in RFCs to Indicate Requirement Levels. Request for Comments 2119, Internet Engineering Task Force, March 1997.
- <a name="Ref_DM-CS"></a>[DM-CS] X-Road: Central Server Data Model. Document ID: [DM-CS](../DataModels/dm-cs_x-road_central_server_configuration_data_model.md)
- <a name="Ref_PR-MESS"></a>[PR-MESS] X-Road: Message Protocol v4.0. Document ID: [PR-MESS](../Protocols/pr-mess_x-road_message_protocol.md)
- <a name="Ref_WSDL"></a>[WSDL] Web Services Description Language (WSDL) 1.1. World Wide Web Consortium. 15 March 2001.
- <a name="Ref_DER"></a>[DER] DER encoding. ITU-T X.690. July 2002.
- <a name="Ref_TERMS" class="anchor"></a>\[TA-TERMS\] X-Road Terms and Abbreviations. Document ID: [TA-TERMS](../terms_x-road_docs.md).

## 2 Format of the Messages

This section describes the input and output parameters of the management services. The low-level technical details of the services are specified using the WSDL \[[WSDL](#Ref_WSDL)\] syntax. See [Annex  B](#annex-b-wsdl-file-for-management-services) for management services WSDL file.

### 2.1 *clientReg* - Security Server Client Registration

The client registration service is invoked by the security server when a new client is added to the server.

The body of the client registration message (request or response) contains the following fields:

* **client** – identifier of the subsystem to be added to the security server;

* **server** – identifier of the security server where the client is added;

* **requestId** – for responses only, unique identifier of the request that is stored in the central server database \[[DM-CS](#Ref_DM-CS)\].

The XML Schema fragment of the client registration request body is shown below. For clarity, documentation in the schema fragment is omitted.

```xml
<xsd:complexType name="ClientRequestType">
    <xsd:sequence>
        <xsd:element name="server" type="id:XRoadSecurityServerIdentifierType"/>
        <xsd:element name="client" type="id:XRoadClientIdentifierType"/>
        <element name="requestId" type="tns:RequestIdType" minOccurs="0"/>
    </xsd:sequence>
</xsd:complexType>
```

The request is sent using HTTP POST method. The content type of the request MUST be *multipart/related* and the request must contain the following MIME parts.

1. X-Road SOAP request message. The message MUST contain the regular X-Road headers and the two data fields (*server*, *client*). The content type of this part MUST be *text/xml*.

2. Signature of the member that owns the subsystem to be registered as a security server client. The MIME part must contain signature of the SOAP request message, created with the private key corresponding to a **signing certificate** of the subsystem's owner. The content type of this part must be *application/octet-stream*. Additionally, the part MUST include header field *signature-algorithm-ID* that identifies the signature algorithm. Currently supported signature algorithms are *SHA256withRSA*, *SHA384withRSA*, *SHA512withRSA*, *SHA256withRSAandMGF1*, *SHA384withRSAandMGF1*, and *SHA512withRSAandMGF1*.

3. Signing certificate of the subsystem's owner that was used to create the second MIME part. The content type of this part MUST be *application/octet-stream*.

4. OCSP response certifying that the signing certificate was valid at the time of creation of the request. The content type of this part MUST be *application/octet-stream*.

The response echoes back the client and the server fields of the request and adds the field *requestId*.

An example of the client registration request and response is given in [Annex A.1](#a1-clientreg).

### 2.2 *clientDeletion* - Security Server Client Deletion

The *clientDeletion* service is invoked by the security server when a client is unregistered.

The body of the client deletion message (request or response) contains following fields:

* **client** – identifier of the subsystem to be removed from the security server;

* **server** – identifier of the security server where the client is removed;

* **requestId** – for responses only, unique identifier of the request that is stored in the central server database \[[DM-CS](#Ref_DM-CS)\].

The XML Schema fragment of the client deletion request body shown below.

```xml
<xsd:complexType name="ClientRequestType">
    <xsd:sequence>
        <xsd:element name="server" type="id:XRoadSecurityServerIdentifierType"/>
        <xsd:element name="client" type="id:XRoadClientIdentifierType"/>
        <element name="requestId" type="tns:RequestIdType" minOccurs="0"/>
    </xsd:sequence>
</xsd:complexType>
```

The response echoes back the client and the server fields of the request and adds the field *requestId*.

An example of the client deletion request and response is given in [Annex A.2](#a2-clientdeletion).

### 2.3 *authCertReg* - Security Server Authentication Certificate Registration

The *authCertReg* service is invoked by the security server when a new authentication certificate is added to the server.

The body of the authentication certificate registration message (request or response) contains the following fields:

* **server** – identifier of the security server where the authentication certificate is added;

* **address** – DNS address of the security server;

* **authCert** – contents (in DER encoding \[[DER](#Ref_DER)\]) of the authentication certificate that will be added to the security server;

* **requestId** – for responses only, unique identifier of the request that is stored in the central server database \[[DM-CS](#Ref_DM-CS)\].

The XML Schema fragment of the authentication certificate registration request body is shown below. For clarity, documentation in the schema fragment is omitted.

```xml
<xsd:complexType name="AuthCertRegRequestType">
    <xsd:sequence>
        <xsd:element name="server" type="id:XRoadSecurityServerIdentifierType"/>
        <xsd:element name="address" type="string" minOccurs="0"/>
        <xsd:element name="authCert" type="base64Binary"/>
        <element name="requestId" type="tns:RequestIdType" minOccurs="0"/>
    </xsd:sequence>
</xsd:complexType>
```

Unlike the other requests, the authentication certificate registration request cannot be sent as a regular X-Road request. This is caused by a bootstrapping problem – sending an X‑Road message requires that the authentication certificate of the security server is registered at the central server. However, the certificate is registered only as a result of invoking this service. Therefore, another mechanism is needed.

The authentication certificate registration request is sent to the central server directly via HTTPS. When making the HTTPS connection the client MUST verify that the server uses the TLS certificate that is given in the global configuration.

If the central server encounters an error, it responds with a SOAP fault message.

The request is sent using HTTP POST method. The content type of the request MUST be *multipart/related* and the request must contain the following MIME parts.

1. X-Road SOAP request message. The message MUST contain the regular X-Road headers and the three data fields (*server*, *address*, *authCert*). The content type of this part MUST be *text/xml*.

2. Proof of possession of the authentication key. The MIME part must contain signature of the SOAP request message (the body of the first MIME part). The signature MUST be given using the private key corresponding to the **authentication certificate** that is being registered (*authCert* field of the SOAP message). The content type of this part must be *application/octet-stream*. Additionally, the part MUST include header field *signature-algorithm-ID* that identifies the signature algorithm. Currently supported signature algorithms are *SHA256withRSA*, *SHA384withRSA*, and *SHA512withRSA*.

3. Signature of the security server's owner. The MIME part must contain signature of the SOAP request message, created with the private key corresponding to a **signing certificate** of the security server's owner. The content type of this part must be *application/octet-stream*. Additionally, the part MUST include header field *signature-algorithm-ID* that identifies the signature algorithm. Currently supported signature algorithms are *SHA256withRSA*, *SHA384withRSA*, *SHA512withRSA*, *SHA256withRSAandMGF1*, *SHA384withRSAandMGF1*, and *SHA512withRSAandMGF1*.

4. Authentication certificate that is being registered (*authCert* field of the SOAP message). The content type of this part MUST be *application/octet-stream*.

5. Signing certificate of the security server's owner that was used to create the third MIME part. The content type of this part MUST be *application/octet-stream*.

6. OCSP response certifying that the signing certificate was valid at the time of creation of the request. The content type of this part MUST be *application/octet-stream*.

The central server sends responds with X-Road response message (content type MUST be *text/xml*). The response echoes back the three fields of the SOAP request and adds the field *requestId*.

An example of the authentication certificate registration request and response is given in [Annex A.3](#a3-authcertreg).

### 2.4 *authCertDeletion* - Security Server Authentication Certificate Deletion

The *authCertDeletion* service is invoked by the security server when an authentication certificate is deleted from the server. The body of the authentication certificate deletion message (request or response) contains the following fields:

* **server** – identifier of the security server where the authentication certificate is removed;

* **authCert** – contents (in DER encoding) of the authentication certificate that is removed from the security server;

* **requestId** – for responses only, unique identifier of the request that is stored in the central server database \[[DM-CS](#Ref_DM-CS)\].

The XML Schema fragment of the authentication certificate deletion request body is shown below.

```xml
<xsd:complexType name="AuthCertDeletionRequestType">
    <xsd:sequence>
        <xsd:element name="server" type="id:XRoadSecurityServerIdentifierType"/>
        <xsd:element name="authCert" type="base64Binary"/>
        <element name="requestId" type="tns:RequestIdType" minOccurs="0"/>
    </xsd:sequence>
</xsd:complexType>
```

The response echoes back the client and the server fields of the request and adds the field *requestId*.

An example of the authentication certificate deletion request and response is given in [Annex A.4](#a4-authcertdeletion).

### 2.5 *ownerChange* - Security Server Owner Change

The owner change service is invoked by the security server when the owner member of the security server is changed.

The body of the owner change message (request or response) contains the following fields:

* **server** – identifier of the security server where the owner is changed;

* **client** – identifier of the new owner member of the security server;

* **requestId** – for responses only, unique identifier of the request that is stored in the central server database \[[DM-CS](#Ref_DM-CS)\].

The XML Schema fragment of the client registration request body is shown below. For clarity, documentation in the schema fragment is omitted.

```xml
<xsd:complexType name="ClientRequestType">
    <xsd:sequence>
        <xsd:element name="server" type="id:XRoadSecurityServerIdentifierType"/>
        <xsd:element name="client" type="id:XRoadClientIdentifierType"/>
        <element name="requestId" type="tns:RequestIdType" minOccurs="0"/>
    </xsd:sequence>
</xsd:complexType>
```

The request is sent using HTTP POST method. The content type of the request MUST be *multipart/related* and the request must contain the following MIME parts.

1. X-Road SOAP request message. The message MUST contain the regular X-Road headers and the two data fields (*server*, *client*). The content type of this part MUST be *text/xml*.

2. Signature of the new owner member of the security server. The MIME part must contain signature of the SOAP request message, created with the private key corresponding to a **signing certificate** of the new owner member. The content type of this part must be *application/octet-stream*. Additionally, the part MUST include header field *signature-algorithm-ID* that identifies the signature algorithm. Currently supported signature algorithms are *SHA256withRSA*, *SHA384withRSA*, *SHA512withRSA*, *SHA256withRSAandMGF1*, *SHA384withRSAandMGF1*, and *SHA512withRSAandMGF1*.

3. Signing certificate of the new owner member that was used to create the second MIME part. The content type of this part MUST be *application/octet-stream*.

4. OCSP response certifying that the new owner member's signing certificate was valid at the time of creation of the request. The content type of this part MUST be *application/octet-stream*.

The response echoes back the server and the client fields of the request and adds the field *requestId*.

An example of the owner change request and response is given in [Annex A.5](#a5-ownerchange).

## Annex A. Example messages

### A.1 clientReg

Request message

```xml
--jetty113950090iemuz6a3
Content-Type: text/xml; charset=UTF-8
<?xml version="1.0" encoding="UTF-8"?>
<SOAP-ENV:Envelope
        xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
        xmlns:id="http://x-road.eu/xsd/identifiers"
        xmlns:xroad="http://x-road.eu/xsd/xroad.xsd">
    <SOAP-ENV:Header>
        <xroad:client id:objectType="MEMBER">
            <id:xRoadInstance>EE</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>TS1OWNER</id:memberCode>
        </xroad:client>
        <xroad:service id:objectType="SERVICE">
            <id:xRoadInstance>EE</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>TS1OWNER</id:memberCode>
            <id:serviceCode>clientReg</id:serviceCode>
        </xroad:service>
        <xroad:id>8770348d-c5f1-4f23-989e-7dd91fb59eff</xroad:id>
        <xroad:protocolVersion>4.0</xroad:protocolVersion>
    </SOAP-ENV:Header>
    <SOAP-ENV:Body>
        <xroad:clientReg>
            <xroad:server id:objectType="SERVER">
                <id:xRoadInstance>EE</id:xRoadInstance>
                <id:memberClass>GOV</id:memberClass>
                <id:memberCode>TS1OWNER</id:memberCode>
                <id:serverCode>TS1</id:serverCode>
            </xroad:server>
            <xroad:client id:objectType="SUBSYSTEM">
                <id:xRoadInstance>EE</id:xRoadInstance>
                <id:memberClass>COM</id:memberClass>
                <id:memberCode>client</id:memberCode>
                <id:subsystemCode>subsystem</id:subsystemCode>
            </xroad:client>
        </xroad:clientReg>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
--jetty113950090iemuz6a3
Content-Type: application/octet-stream
signature-algorithm-id: SHA512withRSA
 
[SUBSYSTEM OWNER SIGNATURE BYTES]
--jetty113950090iemuz6a3
Content-Type: application/octet-stream
 
[SUBSYSTEM OWNER CERTIFICATE BYTES]
--jetty113950090iemuz6a3
Content-Type: application/octet-stream
 
[SUBSYSTEM OWNER CERTIFICATE OCSP RESPONSE BYTES]
--jetty113950090iemuz6a3--
```

Response message

```xml
<?xml version="1.0" encoding="UTF-8"?>
<SOAP-ENV:Envelope
        xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
        xmlns:id="http://x-road.eu/xsd/identifiers"
        xmlns:xroad="http://x-road.eu/xsd/xroad.xsd">
    <SOAP-ENV:Header>
        <xroad:client id:objectType="MEMBER">
            <id:xRoadInstance>EE</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>TS1OWNER</id:memberCode>
        </xroad:client>
        <xroad:service id:objectType="SERVICE">
            <id:xRoadInstance>EE</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>TS1OWNER</id:memberCode>
            <id:serviceCode>clientReg</id:serviceCode>
        </xroad:service>
        <xroad:id>8770348d-c5f1-4f23-989e-7dd91fb59eff</xroad:id>
        <xroad:protocolVersion>4.0</xroad:protocolVersion>
        <xroad:requestHash
                algorithmId="http://www.w3.org/2001/04/xmlenc#sha512">
            LGxmFNQhkhehCsbrrBgX4w64N0Z+knazghehKDYwJzSmVwf8tyVCYHyD8Vp5eSNNMtm0
            XDBzMOkqQ3uSDfNrLw==
        </xroad:requestHash>
    </SOAP-ENV:Header>
    <SOAP-ENV:Body>
        <xroad:clientRegResponse>
            <xroad:server id:objectType="SERVER">
                <id:xRoadInstance>EE</id:xRoadInstance>
                <id:memberClass>GOV</id:memberClass>
                <id:memberCode>TS1OWNER</id:memberCode>
                <id:serverCode>TS1</id:serverCode>
            </xroad:server>
            <xroad:client id:objectType="SUBSYSTEM">
                <id:xRoadInstance>EE</id:xRoadInstance>
                <id:memberClass>COM</id:memberClass>
                <id:memberCode>client</id:memberCode>
                <id:subsystemCode>subsystem</id:subsystemCode>
            </xroad:client>
            <xroad:requestId>394</xroad:requestId>
        </xroad:clientRegResponse>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```

### A.2 clientDeletion

Request message

```xml
<?xml version="1.0" encoding="UTF-8"?>
<SOAP-ENV:Envelope
        xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
        xmlns:id="http://x-road.eu/xsd/identifiers"
        xmlns:xroad="http://x-road.eu/xsd/xroad.xsd">
    <SOAP-ENV:Header>
        <xroad:client id:objectType="MEMBER">
            <id:xRoadInstance>EE</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>TS1OWNER</id:memberCode>
        </xroad:client>
        <xroad:service id:objectType="SERVICE">
            <id:xRoadInstance>EE</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>TS1OWNER</id:memberCode>
            <id:serviceCode>clientDeletion</id:serviceCode>
        </xroad:service>
        <xroad:id>0e0d804a-b4e2-4f56-b5a0-2c32e4288f7d</xroad:id>
        <xroad:protocolVersion>4.0</xroad:protocolVersion>
    </SOAP-ENV:Header>
    <SOAP-ENV:Body>
        <xroad:clientDeletion>
            <xroad:server id:objectType="SERVER">
                <id:xRoadInstance>EE</id:xRoadInstance>
                <id:memberClass>GOV</id:memberClass>
                <id:memberCode>TS1OWNER</id:memberCode>
                <id:serverCode>TS1</id:serverCode>
            </xroad:server>
            <xroad:client id:objectType="SUBSYSTEM">
                <id:xRoadInstance>EE</id:xRoadInstance>
                <id:memberClass>COM</id:memberClass>
                <id:memberCode>client</id:memberCode>
                <id:subsystemCode>subsystem</id:subsystemCode>
            </xroad:client>
        </xroad:clientDeletion>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```

Response message

```xml
<?xml version="1.0" encoding="UTF-8"?>
<SOAP-ENV:Envelope
        xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
        xmlns:id="http://x-road.eu/xsd/identifiers"
        xmlns:xroad="http://x-road.eu/xsd/xroad.xsd">
    <SOAP-ENV:Header>
        <xroad:client id:objectType="MEMBER">
            <id:xRoadInstance>EE</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>TS1OWNER</id:memberCode>
        </xroad:client>
        <xroad:service id:objectType="SERVICE">
            <id:xRoadInstance>EE</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>TS1OWNER</id:memberCode>
            <id:serviceCode>clientDeletion</id:serviceCode>
        </xroad:service>
        <xroad:id>0e0d804a-b4e2-4f56-b5a0-2c32e4288f7d</xroad:id>
        <xroad:protocolVersion>4.0</xroad:protocolVersion>
        <xroad:requestHash algorithmId="http://www.w3.org/2001/04/xmlenc#sha512">
            KHe7PMAcYgNzcS7/4KImaYZxpLry0l+1zkFgzKXVkmzkYXg9IjBgX7CP6wDXwYT0qVON
            6NiF74LvlSwpPupO5A==
        </xroad:requestHash>
    </SOAP-ENV:Header>
    <SOAP-ENV:Body>
        <xroad:clientDeletionResponse>
            <xroad:server id:objectType="SERVER">
                <id:xRoadInstance>EE</id:xRoadInstance>
                <id:memberClass>GOV</id:memberClass>
                <id:memberCode>TS1OWNER</id:memberCode>
                <id:serverCode>TS1</id:serverCode>
            </xroad:server>
            <xroad:client id:objectType="SUBSYSTEM">
                <id:xRoadInstance>EE</id:xRoadInstance>
                <id:memberClass>COM</id:memberClass>
                <id:memberCode>client</id:memberCode>
                <id:subsystemCode>subsystem</id:subsystemCode>
            </xroad:client>
	     <xroad:requestId>395</xroad:requestId>
        </xroad:clientDeletionResponse>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```

### A.3 authCertReg

Request message

```xml
--jetty113950090iemuz6a3
Content-Type: text/xml; charset=UTF-8

<?xml version="1.0" encoding="UTF-8"?>
<SOAP-ENV:Envelope
        xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
        xmlns:id="http://x-road.eu/xsd/identifiers"
        xmlns:xroad="http://x-road.eu/xsd/xroad.xsd">
    <SOAP-ENV:Header>
        <xroad:client id:objectType="MEMBER">
            <id:xRoadInstance>EE</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>TS1OWNER</id:memberCode>
        </xroad:client>
        <xroad:service id:objectType="SERVICE">
            <id:xRoadInstance>EE</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>TS1OWNER</id:memberCode>
            <id:serviceCode>authCertReg</id:serviceCode>
        </xroad:service>
        <xroad:id>9a82c2d1-27d6-4053-85a7-f37327c6dba7</xroad:id>
        <xroad:protocolVersion>4.0</xroad:protocolVersion>
    </SOAP-ENV:Header>
    <SOAP-ENV:Body>
        <xroad:authCertReg>
            <xroad:server id:objectType="SERVER">
                <id:xRoadInstance>EE</id:xRoadInstance>
                <id:memberClass>GOV</id:memberClass>
                <id:memberCode>TS1OWNER</id:memberCode>
                <id:serverCode>TS1</id:serverCode>
            </xroad:server>
            <xroad:address>192.168.74.202</xroad:address>
            <xroad:authCert>
                MIIDtzCCAp+gAwIBAgIIaAPFaI/REfAwDQYJKoZIhvcNAQEFBQAwNzERMA8GA1UE
                AwwIQWRtaW5DQTExFTATBgNVBAoMDEVKQkNBIFNhbXBsZTELMAkGA1UEBhMCU0Uw
                HhcNMTUxMDA1MTEyNzQzWhcNMTcxMDA0MTEyNzQzWjAuMQswCQYDVQQGEwJFRTEM
                MAoGA1UECgwDR09WMREwDwYDVQQDDAhUUzFPV05FUjCCASIwDQYJKoZIhvcNAQEB
                BQADggEPADCCAQoCggEBAIkX6/b/yUNSIvZatpFDqUDJ4l+igH+z8/kyLlu92VL6
                H7hkCL6ggn7qsHOTGaxOupXQBKx/EDMOt+cpbhQlQCSoU2LmXYklv9FEGXTUBt5U
                VlT1mZXQkfPVT2ozWQeGEOe7RLApaldgfFgg6AklsuOTe0FgJTfqXrnjVy84MRht
                56nw0V6SnujGMVxQJR1IJC13I5wRbVbkyOxX52vqJ7Kh/2GWtNj2AgY9VbZA6/8S
                3fMVHWQUbVtFV/2LyjQ0OrwPm0VXsrqRnlh0tln3AtgNOiPgmg72aWNPwlPx7+rE
                02t+0O+KieC3IZppY2044tC699ui5/nOZPrlIqC1XcCAwEAAaOBzzCBzDBNBggrB
                gEFBQcBAQRBMD8wPQYIKwYBBQUHMAGGMWh0dHA6Ly9sb2NhbGhvc3Q6ODA4MC9la
                mJjYS9wdWJsaWN3ZWIvc3RhdHVzL29jc3AwHQYDVR0OBBYEFCB7AE2wTs7iLMMxG
                tilpSg8bShnMAwGA1UdEwEB/wQCMAAwHwYDVR0jBBgwFoAUdy2JLgO2/fjSZTkxN
                SLQRhro0gkwDgYDVR0PAQH/BAQDAgO4MB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrB
                gEFBQcDAjANBgkqhkiG9w0BAQUFAAOCAQEATCbKukYbOV5R4I/ivhEXIJAA8azeJ
                NONWg0+74v9hdInDSDuXreJkkpJNz0pZaaDnbsWFF+LGcB8UDTc6jOGOaH1b2iSh
                qzq/jL+Le9iSi8V26aWmKJipt5fsU5E/OJAA0KMnNjhtq5FDdP7gCD7+pPVq2FwE
                Wf9nsNtAq8uETc5f9PNGxE6PrDl2Gy2K3m4T/0kvQIiMFsk1z054/9rW/w+dQSSs
                xHhYHOPzwbSEsoeSw3UEqeKdaYUspFs+eGD4b3dexwEe5M0oZAwL/+/56eTcOhne
                nP9A+8jF1vlXnP/m+tThaftcMZa/NTvpceLx36TDUIwB222ddkyN2Offw==
            </xroad:authCert>
        </xroad:authCertReg>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
--jetty113950090iemuz6a3
Content-Type: application/octet-stream
signature-algorithm-id: SHA512withRSA

[AUTHENTICATION CERTIFICATE SIGNATURE BYTES]
--jetty113950090iemuz6a3
Content-Type: application/octet-stream
signature-algorithm-id: SHA512withRSA

[SECURITY SERVER OWNER SIGNATURE BYTES]
--jetty113950090iemuz6a3
Content-Type: application/octet-stream

[AUTHENTICATION CERTIFICATE BYTES]
--jetty113950090iemuz6a3
Content-Type: application/octet-stream

[SECURITY SERVER OWNER CERTIFICATE BYTES]
--jetty113950090iemuz6a3
Content-Type: application/octet-stream

[SECURITY SERVER OWNER CERTIFICATE OCSP RESPONSE BYTES]
--jetty113950090iemuz6a3--
```

Response message

```xml
<?xml version="1.0" encoding="UTF-8"?>
<SOAP-ENV:Envelope
        xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
        xmlns:id="http://x-road.eu/xsd/identifiers"
        xmlns:xroad="http://x-road.eu/xsd/xroad.xsd">
    <SOAP-ENV:Header>
        <xroad:client id:objectType="MEMBER">
            <id:xRoadInstance>EE</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>TS1OWNER</id:memberCode>
        </xroad:client>
        <xroad:service id:objectType="SERVICE">
            <id:xRoadInstance>EE</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>TS1OWNER</id:memberCode>
            <id:serviceCode>authCertReg</id:serviceCode>
        </xroad:service>
        <xroad:id>9a82c2d1-27d6-4053-85a7-f37327c6dba7</xroad:id>
        <xroad:protocolVersion>4.0</xroad:protocolVersion>
    </SOAP-ENV:Header>
    <SOAP-ENV:Body>
        <xroad:authCertRegResponse>
            <xroad:server id:objectType="SERVER">
                <id:xRoadInstance>EE</id:xRoadInstance>
                <id:memberClass>GOV</id:memberClass>
                <id:memberCode>TS1OWNER</id:memberCode>
                <id:serverCode>TS1</id:serverCode>
            </xroad:server>
            <xroad:address>192.168.74.202</xroad:address>
            <xroad:authCert>
                MIIDtzCCAp+gAwIBAgIIaAPFaI/REfAwDQYJKoZIhvcNAQEFBQAwNzERMA8GA1UE
                AwwIQWRtaW5DQTExFTATBgNVBAoMDEVKQkNBIFNhbXBsZTELMAkGA1UEBhMCU0Uw
                HhcNMTUxMDA1MTEyNzQzWhcNMTcxMDA0MTEyNzQzWjAuMQswCQYDVQQGEwJFRTEM
                MAoGA1UECgwDR09WMREwDwYDVQQDDAhUUzFPV05FUjCCASIwDQYJKoZIhvcNAQEB
                BQADggEPADCCAQoCggEBAIkX6/b/yUNSIvZatpFDqUDJ4l+igH+z8/kyLlu92VL6
                H7hkCL6ggn7qsHOTGaxOupXQBKx/EDMOtcpbhQlQCSoU2LmXYklv9FEGXTUBt5UV
                lT1mZXQkfPVT2ozWQeGEOe7RLApaldgfFgg6AklsuOTe0FgJTfqXrnjVy84MRht5
                6nw0V6SnujGMVxQJR1IJC13I5wRbVbkyOxX52vqJ7Kh/2GWtNj2AgY9VbZA6/8ES
                3fMVHWQUbVtFV/2LyjQ0OrwPm0VXsrqRnlh0tln3AtgNOiPgmg72aWNPwlPx7+rE
                02t+0O+KieC3IZppY2044tC699ui5nOZPrlIqC1XcCAwEAAaOBzzCBzDBNBggrBg
                EFBQcBAQRBMD8wPQYIKwYBBQUHMAGGMWh0dHA6Ly9sb2NhbGhvc3Q6ODA4MC9lam
                JjYS9wdWJsaWN3ZWIvc3RhdHVzL29jc3AwHQYDVR0OBBYEFCB7AE2wTs7iLMMxGt
                ilpSg8bShnMAwGA1UdEwEB/wQCMAAwHwYDVR0jBBgwFoAUdy2JLgO2/fjSZTkxNS
                LQRhro0gkwDgYDVR0PAQH/BAQDAgO4MB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBg
                EFBQcDAjANBgkqhkiG9w0BAQUFAAOCAQEATCbKukYbOV5R4I/ivhEXIJAA8azeJN
                ONWg0+74v9hdInDSDuXreJkkpJNz0pZaaDnbsWFF+LGcB8UDTc6jOGOaH1b2iShq
                zq/jL+Le9iSi8V26aWmKJipt5fsU5E/OJAA0KMnNjhtq5FDdP7gCD7+pPVq2FwEW
                f9nsNtAq8uETc5f9PNGxE6PrDl2Gy2K3m4T/0kvQIiMFsk1z054/9rW/w+dQSSsx
                HhYHOPzwbSEsoeSw3UEqeKdaYUspFs+eGD4b3dexwEe5M0oZAwL/+/56eTcOhnen
                P9A+8jF1vlXnP/m+tThaftcMZa/NTvpceLx36TDUIwB222ddkyN2Offw==
            </xroad:authCert>
            <xroad:requestId>392</xroad:requestId>
        </xroad:authCertRegResponse>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```

### A.4 authCertDeletion

Request message

```xml
<?xml version="1.0" encoding="UTF-8"?>
<SOAP-ENV:Envelope
        xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
        xmlns:id="http://x-road.eu/xsd/identifiers"
        xmlns:xroad="http://x-road.eu/xsd/xroad.xsd">
    <SOAP-ENV:Header>
        <xroad:client id:objectType="MEMBER">
            <id:xRoadInstance>EE</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>TS1OWNER</id:memberCode>
        </xroad:client>
        <xroad:service id:objectType="SERVICE">
            <id:xRoadInstance>EE</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>TS1OWNER</id:memberCode>
            <id:serviceCode>authCertDeletion</id:serviceCode>
        </xroad:service>
        <xroad:id>2c3094ae-3e19-46f7-b26d-e7ecb35dfc63</xroad:id>
        <xroad:protocolVersion>4.0</xroad:protocolVersion>
    </SOAP-ENV:Header>
    <SOAP-ENV:Body>
        <xroad:authCertDeletion>
            <xroad:server id:objectType="SERVER">
                <id:xRoadInstance>EE</id:xRoadInstance>
                <id:memberClass>GOV</id:memberClass>
                <id:memberCode>TS1OWNER</id:memberCode>
                <id:serverCode>TS1</id:serverCode>
            </xroad:server>
            <xroad:authCert>
                MIIDtzCCAp+gAwIBAgIIaAPFaI/REfAwDQYJKoZIhvcNAQEFBQAwNzERMA8GA1UE
                AwwIQWRtaW5DQTExFTATBgNVBAoMDEVKQkNBIFNhbXBsZTELMAkGA1UEBhMCU0Uw
                HhcNMTUxMDA1MTEyNzQzWhcNMTcxMDA0MTEyNzQzWjAuMQswCQYDVQQGEwJFRTEM
                MAoGA1UECgwDR09WMREwDwYDVQQDDAhUUzFPV05FUjCCASIwDQYJKoZIhvcNAQEB
                BQADggEPADCCAQoCggEBAIkX6/b/yUNSIvZatpFDqUDJ4l+igH+z8/kyLlu92VL6
                H7hkCL6ggn7qsHOTGaxOupXQBKx/EDMOt+cpbhQlQCSoU2LmXYklv9FEGXTUBt5U
                VlT1mZXQkfPVT2ozWQeGEOe7RLApaldgfFgg6AklsuOTe0FgJTfqXrnjVy84MRht
                56nw0V6SnujGMVxQJR1IJC13I5wRbVbkyOxX52vqJ7Kh/2GWtNj2AgY9VbZA6/8E
                S3fMVHWQUbVtFV/2LyjQ0OrwPm0VXsrqRnlh0tln3AtgNOiPgmg72aWNPwlPx7+r
                E02t+0O+KieC3IZppY2044tC699ui5/nOZPrlIqC1XcCAwEAAaOBzzCBzDBNBggr
                BgEFBQcBAQRBMD8wPQYIKwYBBQUHMAGGMWh0dHA6Ly9sb2NhbGhvc3Q6ODA4MC9l
                amJjYS9wdWJsaWN3ZWIvc3RhdHVzL29jc3AwHQYDVR0OBBYEFCB7AE2wTs7iLMMx
                GtilpSg8bShnMAwGA1UdEwEB/wQCMAAwHwYDVR0jBBgwFoAUdy2JLgO2/fjSZTkx
                NSLQRhro0gkwDgYDVR0PAQH/BAQDAgO4MB0GA1UdJQQWMBQGCCsGAQUFBwMBBggr
                BgEFBQcDAjANBgkqhkiG9w0BAQUFAAOCAQEATCbKukYbOV5R4I/ivhEXIJAA8aze
                JNONWg0+74v9hdInDSDuXreJkkpJNz0pZaaDnbsWFF+LGcB8UDTc6jOGOaH1b2iS
                hqzq/jL+Le9iSi8V26aWmKJipt5fsU5E/OJAA0KMnNjhtq5FDdP7gCD7+pPVq2Fw
                EWf9nsNtAq8uETc5f9PNGxE6PrDl2Gy2K3m4T/0kvQIiMFsk1z054/9rW/w+dQSS
                sxHhYHOPzwbSEsoeSw3UEqeKdaYUspFs+eGD4b3dexwEe5M0oZAwL/+/56eTcOhn
                enP9A+8jF1vlXnP/m+tThaftcMZa/NTvpceLx36TDUIwB222ddkyN2Offw==
            </xroad:authCert>
        </xroad:authCertDeletion>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```

Response message

```xml
<?xml version="1.0" encoding="UTF-8"?>
<SOAP-ENV:Envelope
        xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
        xmlns:id="http://x-road.eu/xsd/identifiers"
        xmlns:xroad="http://x-road.eu/xsd/xroad.xsd">
    <SOAP-ENV:Header>
        <xroad:client id:objectType="MEMBER">
            <id:xRoadInstance>EE</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>TS1OWNER</id:memberCode>
        </xroad:client>
        <xroad:service id:objectType="SERVICE">
            <id:xRoadInstance>EE</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>TS1OWNER</id:memberCode>
            <id:serviceCode>authCertDeletion</id:serviceCode>
        </xroad:service>
        <xroad:id>2c3094ae-3e19-46f7-b26d-e7ecb35dfc63</xroad:id>
        <xroad:protocolVersion>4.0</xroad:protocolVersion>
        <xroad:requestHash
                algorithmId="http://www.w3.org/2001/04/xmlenc#sha512">
            Zvs1uF2GW3zdma1r9K9keOGhNPOjCr3TEZNpxfpRCtsqqy3ljiLorMZ3e5iNZtX6Ek60
            xtV12Gue8Mme1ryZmQ==
        </xroad:requestHash>
    </SOAP-ENV:Header>
    <SOAP-ENV:Body>
        <xroad:authCertDeletionResponse>
            <xroad:server id:objectType="SERVER">
                <id:xRoadInstance>EE</id:xRoadInstance>
                <id:memberClass>GOV</id:memberClass>
                <id:memberCode>TS1OWNER</id:memberCode>
                <id:serverCode>TS1</id:serverCode>
            </xroad:server>
            <xroad:authCert>
                MIIDtzCCAp+gAwIBAgIIaAPFaI/REfAwDQYJKoZIhvcNAQEFBQAwNzERMA8GA1UE
                AwwIQWRtaW5DQTExFTATBgNVBAoMDEVKQkNBIFNhbXBsZTELMAkGA1UEBhMCU0Uw
                HhcNMTUxMDA1MTEyNzQzWhcNMTcxMDA0MTEyNzQzWjAuMQswCQYDVQQGEwJFRTEM
                MAoGA1UECgwDR09WMREwDwYDVQQDDAhUUzFPV05FUjCCASIwDQYJKoZIhvcNAQEB
                BQADggEPADCCAQoCggEBAIkX6/b/yUNSIvZatpFDqUDJ4l+igH+z8/kyLlu92VL6
                H7hkCL6ggn7qsHOTGaxOupXQBKx/EDMOt+cpbhQlQCSoU2LmXYklv9FEGXTUBt5U
                VlT1mZXQkfPVT2ozWQeGEOe7RLApaldgfFgg6AklsuOTe0FgJTfqXrnjVy84MRht
                56nw0V6SnujGMVxQJR1IJC13I5wRbVbkyOxX52vqJ7Kh/2GWtNj2AgY9VbZA6/8E
                S3fMVHWQUbVtFV/2LyjQ0OrwPm0VXsrqRnlh0tln3AtgNOiPgmg72aWNPwlPx7+r
                E02t+0O+KieC3IZppY2044tC699ui5/nOZPrlIqC1XcCAwEAAaOBzzCBzDBNBggr
                BgEFBQcBAQRBMD8wPQYIKwYBBQUHMAGGMWh0dHA6Ly9sb2NhbGhvc3Q6ODA4MC9l
                amJjYS9wdWJsaWN3ZWIvc3RhdHVzL29jc3AwHQYDVR0OBBYEFCB7AE2wTs7iLMMx
                GtilpSg8bShnMAwGA1UdEwEB/wQCMAAwHwYDVR0jBBgwFoAUdy2JLgO2/fjSZTkx
                NSLQRhro0gkwDgYDVR0PAQH/BAQDAgO4MB0GA1UdJQQWMBQGCCsGAQUFBwMBBggr
                BgEFBQcDAjANBgkqhkiG9w0BAQUFAAOCAQEATCbKukYbOV5R4I/ivhEXIJAA8aze
                JNONWg0+74v9hdInDSDuXreJkkpJNz0pZaaDnbsWFF+LGcB8UDTc6jOGOaH1b2iS
                hqzq/jL+Le9iSi8V26aWmKJipt5fsU5E/OJAA0KMnNjhtq5FDdP7gCD7+pPVq2Fw
                EWf9nsNtAq8uETc5f9PNGxE6PrDl2Gy2K3m4T/0kvQIiMFsk1z054/9rW/w+dQSS
                sxHhYHOPzwbSEsoeSw3UEqeKdaYUspFs+eGD4b3dexwEe5M0oZAwL/+/56eTcOhn
                enP9A+8jF1vlXnP/m+tThaftcMZa/NTvpceLx36TDUIwB222ddkyN2Offw==
            </xroad:authCert>
            <xroad:requestId>392</xroad:requestId>
        </xroad:authCertDeletionResponse>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```

### A.5 ownerChange

Request message

```xml
--jetty113950090iemuz6a3
Content-Type: text/xml; charset=UTF-8
<?xml version="1.0" encoding="UTF-8"?>
<SOAP-ENV:Envelope
        xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
        xmlns:id="http://x-road.eu/xsd/identifiers"
        xmlns:xroad="http://x-road.eu/xsd/xroad.xsd">
    <SOAP-ENV:Header>
        <xroad:client id:objectType="MEMBER">
            <id:xRoadInstance>EE</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>TS1OWNER</id:memberCode>
        </xroad:client>
        <xroad:service id:objectType="SERVICE">
            <id:xRoadInstance>EE</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>TS1OWNER</id:memberCode>
            <id:serviceCode>ownerChange</id:serviceCode>
        </xroad:service>
        <xroad:id>40c1a424-729d-4d52-bd77-ac6f70d1dac0</xroad:id>
        <xroad:protocolVersion>4.0</xroad:protocolVersion>
    </SOAP-ENV:Header>
    <SOAP-ENV:Body>
        <xroad:ownerChange>
            <xroad:server id:objectType="SERVER">
                <id:xRoadInstance>EE</id:xRoadInstance>
                <id:memberClass>GOV</id:memberClass>
                <id:memberCode>TS1OWNER</id:memberCode>
                <id:serverCode>TS1</id:serverCode>
            </xroad:server>
            <xroad:client id:objectType="MEMBER">
                <id:xRoadInstance>EE</id:xRoadInstance>
                <id:memberClass>COM</id:memberClass>
                <id:memberCode>MACK</id:memberCode>
            </xroad:client>
        </xroad:ownerChange>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
--jetty113950090iemuz6a3
Content-Type: application/octet-stream
signature-algorithm-id: SHA512withRSA
 
[NEW OWNER SIGNATURE BYTES]
--jetty113950090iemuz6a3
Content-Type: application/octet-stream
 
[NEW OWNER CERTIFICATE BYTES]
--jetty113950090iemuz6a3
Content-Type: application/octet-stream
 
[NEW OWNER CERTIFICATE OCSP RESPONSE BYTES]
--jetty113950090iemuz6a3--
```

Response message

```xml
<?xml version="1.0" encoding="UTF-8"?>
<SOAP-ENV:Envelope
        xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
        xmlns:id="http://x-road.eu/xsd/identifiers"
        xmlns:xroad="http://x-road.eu/xsd/xroad.xsd">
    <SOAP-ENV:Header>
        <xroad:client id:objectType="MEMBER">
            <id:xRoadInstance>EE</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>TS1OWNER</id:memberCode>
        </xroad:client>
        <xroad:service id:objectType="SERVICE">
            <id:xRoadInstance>EE</id:xRoadInstance>
            <id:memberClass>GOV</id:memberClass>
            <id:memberCode>TS1OWNER</id:memberCode>
            <id:serviceCode>ownerChange</id:serviceCode>
        </xroad:service>
        <xroad:id>40c1a424-729d-4d52-bd77-ac6f70d1dac0</xroad:id>
        <xroad:protocolVersion>4.0</xroad:protocolVersion>
        <xroad:requestHash
                algorithmId="http://www.w3.org/2001/04/xmlenc#sha512">
            LGxmFNQhkhehCsbrrBgX4w64N0Z+knazghehKDYwJzSmVwf8tyVCYHyD8Vp5eSNNMtm0
            XDBzMOkqQ3uSDfNrLw==
        </xroad:requestHash>
    </SOAP-ENV:Header>
    <SOAP-ENV:Body>
        <xroad:ownerChangeResponse>
            <xroad:server id:objectType="SERVER">
                <id:xRoadInstance>EE</id:xRoadInstance>
                <id:memberClass>GOV</id:memberClass>
                <id:memberCode>TS1OWNER</id:memberCode>
                <id:serverCode>TS1</id:serverCode>
            </xroad:server>
            <xroad:client id:objectType="MEMBER">
                <id:xRoadInstance>EE</id:xRoadInstance>
                <id:memberClass>COM</id:memberClass>
                <id:memberCode>MACK</id:memberCode>
            </xroad:client>
            <xroad:requestId>691</xroad:requestId>
        </xroad:ownerChangeResponse>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```

## Annex B WSDL File for Management Services

```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<wsdl:definitions xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
        xmlns:tns="http://x-road.eu/centralservice/"
        xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/"
        xmlns:xroad="http://x-road.eu/xsd/xroad.xsd"
        xmlns:xsd="http://www.w3.org/2001/XMLSchema"
        name="centralservice"
        targetNamespace="http://x-road.eu/centralservice/">
    <wsdl:types>
        <!-- Schema for identifiers -->
        <xs:schema elementFormDefault="qualified" jxb:version="2.1"
                targetNamespace="http://x-road.eu/xsd/identifiers"
                xmlns="http://x-road.eu/xsd/identifiers"
                xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <xs:complexType name="XRoadIdentifierType">
                <xs:annotation>
                    <xs:documentation>
                        Globally unique identifier in the X-Road system.
                        Identifier consists of object type specifier and list of
                        hierarchical codes (starting with code that identifiers
                        the X-Road instance).
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
                    <xs:documentation>
                        Enumeration for X-Road identifier types.
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
                    <xs:documentation>
                        Identifies the X-Road instance.
                        This field is applicable to all identifier types.
                    </xs:documentation>
                </xs:annotation>
            </xs:element>
            <xs:element name="memberClass" type="xs:string">
                <xs:annotation>
                    <xs:documentation>
                        Type of the member (company, government institution,
                        private person, etc.)
                    </xs:documentation>
                </xs:annotation>
            </xs:element>
            <xs:element name="memberCode" type="xs:string">
                <xs:annotation>
                    <xs:documentation>
                        Code that uniquely identifies a member of given member
                        type.
                    </xs:documentation>
                </xs:annotation>
            </xs:element>
            <xs:element name="subsystemCode" type="xs:string">
                <xs:annotation>
                    <xs:documentation>
                        Code that uniquely identifies a subsystem of given
                        X-Road member.
                    </xs:documentation>
                </xs:annotation>
            </xs:element>
            <xs:element name="groupCode" type="xs:string">
                <xs:annotation>
                    <xs:documentation>
                        Code that uniquely identifies a global group in given
                        X-Road instance.
                    </xs:documentation>
                </xs:annotation>
            </xs:element>
            <xs:element name="serviceCode" type="xs:string">
                <xs:annotation>
                    <xs:documentation>
                        Code that uniquely identifies a service offered by given
                        X-Road member or subsystem.
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
                    <xs:documentation>
                        Code that uniquely identifies security server offered by
                        a given X-Road member or subsystem.
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
        <!-- Schema for requests (reduced) -->
        <xsd:schema
                xmlns="http://www.w3.org/2001/XMLSchema"
                xmlns:tns="http://x-road.eu/xsd/xroad.xsd"
                xmlns:id="http://x-road.eu/xsd/identifiers"
                targetNamespace="http://x-road.eu/xsd/xroad.xsd"
                elementFormDefault="qualified">
            <xsd:import namespace="http://x-road.eu/xsd/identifiers" id="id"/>
            <xsd:element name="clientReg" type="tns:ClientRequestType"/>
            <xsd:element name="clientRegResponse" type="tns:ClientRequestType"/>
            <xsd:element name="clientDeletion" type="tns:ClientRequestType"/>
            <xsd:element name="clientDeletionResponse"  
                    type="tns:ClientRequestType"/>
            <xsd:element name="authCertReg" type="tns:AuthCertRegRequestType"/>
            <xsd:element name="authCertRegResponse"
                    type="tns:AuthCertRegRequestType"/>
            <xsd:element name="authCertDeletion"
                    type="tns:AuthCertDeletionRequestType"/>
            <xsd:element name="authCertDeletionResponse"
                    type="tns:AuthCertDeletionRequestType"/>
            <xsd:element name="ownerChange" type="tns:ClientRequestType"/>
            <xsd:element name="ownerChangeResponse" type="tns:ClientRequestType"/>
            <!-- Header fields -->
            <xsd:element name="client" type="id:XRoadClientIdentifierType"/>
            <xsd:element name="service" type="id:XRoadServiceIdentifierType"/>
            <xsd:element name="id" type="xsd:string"/>
            <xsd:element name="protocolVersion" type="xsd:string"/>
            <xsd:element name="requestHash" type="xsd:string"/>
            <xsd:complexType name="AuthCertRegRequestType">
                <xsd:sequence>
                    <xsd:element name="server"
                            type="id:XRoadSecurityServerIdentifierType">
                        <xsd:annotation>
                            <xsd:documentation>
                                Identity of the security server the
                                authentication certificate will be associated
                                with.
                            </xsd:documentation>
                        </xsd:annotation>
                    </xsd:element>
                    <xsd:element name="address" type="string" minOccurs="0">
                        <xsd:annotation>
                            <xsd:documentation>
                                Address of the security server the
                                authentication certificate will be associated
                                with.
                            </xsd:documentation>
                        </xsd:annotation>
                    </xsd:element>
                    <xsd:element name="authCert" type="base64Binary">
                        <xsd:annotation>
                            <xsd:documentation>
                              Contents (in DER encoding) of the authentication
                              certificate that will be added to the list of
                              certificates authenticating the security server.
                          </xsd:documentation>
                        </xsd:annotation>
                    </xsd:element>
                    <xsd:element name="requestId" type="tns:RequestIdType"
                            minOccurs="0"/>
                </xsd:sequence>
            </xsd:complexType>
            <xsd:complexType name="AuthCertDeletionRequestType">
                <xsd:sequence>
                    <xsd:element name="server"
                            type="id:XRoadSecurityServerIdentifierType">
                        <xsd:annotation>
                            <xsd:documentation>
                                Identity of the security server the
                                authentication certificate will be deleted from.
                            </xsd:documentation>
                        </xsd:annotation>
                    </xsd:element>
                    <xsd:element name="authCert" type="base64Binary">
                        <xsd:annotation>
                            <xsd:documentation>
                                Contents (in DER encoding) of the authentication
                                certificate that will be deleted from the list
                                of certificates authenticating the security
                                server.
                            </xsd:documentation>
                        </xsd:annotation>
                    </xsd:element>
                    <xsd:element name="requestId" type="tns:RequestIdType"
                            minOccurs="0"/>
                </xsd:sequence>
            </xsd:complexType>
            <xsd:complexType name="ClientRequestType">
                <xsd:sequence>
                    <xsd:element name="server"
                            type="id:XRoadSecurityServerIdentifierType">
                        <xsd:annotation>
                            <xsd:documentation>
                                Identifier of the security server where the
                                client is added to or removed from (depending on
                                the request type).
                            </xsd:documentation>
                        </xsd:annotation>
                    </xsd:element>
                    <xsd:element name="client"
                            type="id:XRoadClientIdentifierType">
                        <xsd:annotation>
                            <xsd:documentation>
                                Identifier of the client
                                associated with the security server. When the
                                request is for registering client, the client is
                                added to the security server. When the request
                                is for deleting client, the client is removed
                                from the clients' list of the security server.
                            </xsd:documentation>
                        </xsd:annotation>
                    </xsd:element>
                    <xsd:element name="requestId" type="tns:RequestIdType"
                          minOccurs="0"/>
                </xsd:sequence>
            </xsd:complexType>
            <xsd:simpleType name="RequestIdType">
                <xsd:annotation>
                    <xsd:documentation>
                        For responses only, unique identifier of the request
                        that is stored in the central server database.
                    </xsd:documentation>
                </xsd:annotation>
                <xsd:restriction base="integer"/>
            </xsd:simpleType>
        </xsd:schema>
    </wsdl:types>
    <wsdl:message name="requestheader">
        <wsdl:part name="client" element="xroad:client"/>
        <wsdl:part name="service" element="xroad:service"/>
        <wsdl:part name="id" element="xroad:id"/>
        <wsdl:part name="protocolVersion" element="xroad:protocolVersion"/>
        <wsdl:part name="requestHash" element="xroad:requestHash"/>
    </wsdl:message>
    <wsdl:message name="clientReg">
        <wsdl:part element="xroad:clientReg" name="parameters"/>
    </wsdl:message>
    <wsdl:message name="clientRegResponse">
        <wsdl:part element="xroad:clientRegResponse" name="parameters"/>
    </wsdl:message>
    <wsdl:message name="clientDeletion">
        <wsdl:part element="xroad:clientDeletion" name="parameters"/>
    </wsdl:message>
    <wsdl:message name="clientDeletionResponse">
        <wsdl:part element="xroad:clientDeletionResponse" name="parameters"/>
    </wsdl:message>
    <wsdl:message name="authCertReg">
        <wsdl:part element="xroad:authCertReg" name="parameters"/>
    </wsdl:message>
    <wsdl:message name="authCertRegResponse">
        <wsdl:part element="xroad:authCertRegResponse" name="parameters"/>
    </wsdl:message>
    <wsdl:message name="authCertDeletion">
        <wsdl:part element="xroad:authCertDeletion" name="parameters"/>
    </wsdl:message>
    <wsdl:message name="authCertDeletionResponse">
        <wsdl:part element="xroad:authCertDeletionResponse" name="parameters"/>
    </wsdl:message>
    <wsdl:message name="ownerChange">
        <wsdl:part element="xroad:ownerChange" name="parameters"/>
    </wsdl:message>
    <wsdl:message name="ownerChangeResponse">
        <wsdl:part element="xroad:ownerChangeResponse" name="parameters"/>
    </wsdl:message>
    <wsdl:portType name="centralservice">
        <wsdl:operation name="clientReg">
            <wsdl:input message="tns:clientReg"/>
            <wsdl:output message="tns:clientRegResponse"/>
        </wsdl:operation>
        <wsdl:operation name="clientDeletion">
            <wsdl:input message="tns:clientDeletion"/>
            <wsdl:output message="tns:clientDeletionResponse"/>
        </wsdl:operation>
        <wsdl:operation name="authCertDeletion">
            <wsdl:input message="tns:authCertDeletion"/>
            <wsdl:output message="tns:authCertDeletionResponse"/>
        </wsdl:operation>
        <wsdl:operation name="ownerChange">
            <wsdl:input message="tns:ownerChange"/>
            <wsdl:output message="tns:ownerChangeResponse"/>
        </wsdl:operation>
    </wsdl:portType>
    <wsdl:binding name="centralserviceSOAP" type="tns:centralservice">
        <soap:binding style="document"
                transport="http://schemas.xmlsoap.org/soap/http"/>
        <wsdl:operation name="clientReg">
            <soap:operation soapAction=""/>
            <wsdl:input>
                <soap:body use="literal"/>
                <soap:header message="tns:requestheader" part="client"
                        use="literal"/>
                <soap:header message="tns:requestheader" part="service"
                        use="literal"/>
                <soap:header message="tns:requestheader" part="id"
                        use="literal"/>
                <soap:header message="tns:requestheader" part="protocolVersion"
                        use="literal"/>
            </wsdl:input>
            <wsdl:output>
                <soap:body use="literal"/>
                <soap:header message="tns:requestheader" part="client"
                        use="literal"/>
                <soap:header message="tns:requestheader" part="service"
                        use="literal"/>
                <soap:header message="tns:requestheader" part="id"
                        use="literal"/>
                <soap:header message="tns:requestheader" part="protocolVersion"
                        use="literal"/>
                <soap:header message="tns:requestheader" part="requestHash"
                        use="literal"/>
            </wsdl:output>
        </wsdl:operation>
        <wsdl:operation name="clientDeletion">
            <soap:operation soapAction=""/>
            <wsdl:input>
                <soap:body use="literal"/>
                <soap:header message="tns:requestheader" part="client"
                      use="literal"/>
                <soap:header message="tns:requestheader" part="service"
                      use="literal"/>
                <soap:header message="tns:requestheader" part="id"
                      use="literal"/>
                <soap:header message="tns:requestheader" part="protocolVersion"
                      use="literal"/>
            </wsdl:input>
            <wsdl:output>
                <soap:body use="literal"/>
                <soap:header message="tns:requestheader" part="client"
                      use="literal"/>
                <soap:header message="tns:requestheader" part="service"
                      use="literal"/>
                <soap:header message="tns:requestheader" part="id"
                      use="literal"/>
                <soap:header message="tns:requestheader" part="protocolVersion"
                      use="literal"/>
                <soap:header message="tns:requestheader" part="requestHash"
                      use="literal"/>
            </wsdl:output>
        </wsdl:operation>
        <wsdl:operation name="authCertDeletion">
            <soap:operation soapAction=""/>
            <wsdl:input>
                <soap:body use="literal"/>
                <soap:header message="tns:requestheader" part="client"
                      use="literal"/>
                <soap:header message="tns:requestheader" part="service"
                      use="literal"/>
                <soap:header message="tns:requestheader" part="id"
                      use="literal"/>
                <soap:header message="tns:requestheader" part="protocolVersion"
                      use="literal"/>
            </wsdl:input>
            <wsdl:output>
                <soap:body use="literal"/>
                <soap:header message="tns:requestheader" part="client"
                      use="literal"/>
                <soap:header message="tns:requestheader" part="service"
                      use="literal"/>
                <soap:header message="tns:requestheader" part="id"
                      use="literal"/>
                <soap:header message="tns:requestheader" part="protocolVersion"
                      use="literal"/>
                <soap:header message="tns:requestheader" part="requestHash"
                      use="literal"/>
            </wsdl:output>
        </wsdl:operation>
        <wsdl:operation name="ownerChange">
            <soap:operation soapAction=""/>
            <wsdl:input>
                <soap:body use="literal"/>
                <soap:header message="tns:requestheader" part="client"
                        use="literal"/>
                <soap:header message="tns:requestheader" part="service"
                        use="literal"/>
                <soap:header message="tns:requestheader" part="id"
                        use="literal"/>
                <soap:header message="tns:requestheader" part="protocolVersion"
                        use="literal"/>
            </wsdl:input>
            <wsdl:output>
                <soap:body use="literal"/>
                <soap:header message="tns:requestheader" part="client"
                        use="literal"/>
                <soap:header message="tns:requestheader" part="service"
                        use="literal"/>
                <soap:header message="tns:requestheader" part="id"
                        use="literal"/>
                <soap:header message="tns:requestheader" part="protocolVersion"
                        use="literal"/>
                <soap:header message="tns:requestheader" part="requestHash"
                        use="literal"/>
            </wsdl:output>
        </wsdl:operation>
    </wsdl:binding>
    <wsdl:service name="centralservice">
        <wsdl:port binding="tns:centralserviceSOAP" name="centralserviceSOAP">
            <soap:address
                    location="http://INSERT_MANAGEMENT_SERVICE_ADDRESS_HERE"/>
        </wsdl:port>
    </wsdl:service>
</wsdl:definitions>
```
