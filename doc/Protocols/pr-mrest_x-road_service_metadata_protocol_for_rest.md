![](img/eu_regional_development_fund_horizontal_div_15.png "European Union | European Regional Development Fund | Investing in your future")

---

# X-Road: Service Metadata Protocol for REST <!-- omit in toc --> 
**Technical Specification**  

Version: 0.1  
Doc. ID: PR-MREST  

---

## Version history <!-- omit in toc --> 

 Date       | Version | Description                                                     | Author
 ---------- | ------- | --------------------------------------------------------------- | --------------------
 29.07.2019 | 0.1     | Initial version                                                 | Ilkka Seppälä
 
## Table of Contents <!-- omit in toc --> 

- [License](#license)
- [1 Introduction](#1-introduction)
  - [1.1 Terms and abbreviations](#11-terms-and-abbreviations)
  - [1.2 References](#12-references)
- [2 Retrieving List of Service Providers](#2-retrieving-list-of-service-providers)
- [3 Retrieving List of Central Services](#3-retrieving-list-of-central-services)
- [4 Retrieving List of Services](#4-retrieving-list-of-services)
- [5 Retrieving the OpenAPI description of a Service](#5-retrieving-the-openapi-description-of-a-service)
- [Annex A Service Descriptions for REST Metadata Services](#annex-a-service-descriptions-for-rest-metadata-services)
- [Annex B Example Messages](#annex-b-example-messages)
  - [B.1 listMethods Response](#b1-listmethods-response)
  - [B.2 allowedMethods Response](#b2-allowedmethods-response)

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/

## 1 Introduction

X-Road Service Metadata protocol \[[PR-META](#Ref_PR-META)\] describes how X-Road metaservices can be called via SOAP interface. This specification complements it by describing REST call mechanisms.

REST metaservices conform to X-Road Message Protocol for REST \[[PR-REST](#Ref_PR-REST)\].

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in this document (in uppercase, as shown) are to be interpreted as described in \[[RFC2119](#Ref_RFC2119)\].

### 1.1 Terms and abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\].

### 1.2 References

1. <a name="Ref_PR-META" class="anchor"></a>\[PR-META\] X-Road: Service Metadata Protocol. Document ID:
[PR-META](pr-meta_x-road_service_metadata_protocol.md).
2. <a name="Ref_RFC2119" class="anchor"></a>\[RFC2119\] Key words for use in RFCs to Indicate Requirement Levels, Internet Engineering Task Force, 1997,
[https://www.ietf.org/rfc/rfc2119.txt](https://www.ietf.org/rfc/rfc2119.txt)
3. <a id="Ref_TERMS" class="anchor"></a>\[TA-TERMS\] X-Road Terms and Abbreviations. Document ID: [TA-TERMS](../terms_x-road_docs.md).
4. <a id="Ref_PR-REST" class="anchor"></a>\[PR-REST\] X-Road: Message Protocol for REST. Document ID:
[PR-REST](pr-rest_x-road_message_protocol_for_rest.md).

## 2 Retrieving List of Service Providers

For retrieving the list of service providers listClients metaservice is used. It can be invoked with simple HTTP GET to right URL. Service output format is controlled with Accept header. The details of listClients are described in \[[PR-META](#Ref_PR-META)\].

## 3 Retrieving List of Central Services

For retrieving the list of central services listCentralServices metaservice is used. It can be invoked with simple HTTP GET to right URL. The details of listCentralServices are described in \[[PR-META](#Ref_PR-META)\].

## 4 Retrieving List of Services

X-Road provides two methods for getting the list of services offered by an X-Road client:

* `listMethods` lists all REST services offered by a service provider.

* `allowedMethods` lists all REST services offered by a service provider that the caller has permission to invoke.

Both methods are invoked as regular X-Road REST services (see specification \[[PR-REST](#Ref_PR-REST)\] for details on the X-Road REST protocol).

The serviceId MUST contain the identifier of the target service provider and the value of the serviceCode element MUST be either `listMethods` or `allowedMethods`.

Request example
```
GET /r1/INSTANCE/CLASS2/MEMBER2/SUBSYSTEM2/listMethods
```
HTTP request headers
```
X-Road-Client: INSTANCE/CLASS1/MEMBER1/SUBSYSTEM1
```

The body of the response message MUST contain a list of services provided by the service provider (in case of listMethods) or open to the given client (in case of allowedMethods). The response SHALL NOT contain names of the metainfo services.

Annex [A](#annex-a-service-descriptions-for-rest-metadata-services) contains the OpenAPI description of the REST metadata services.

Annexes [B.1](#c1-listmethods-response) and [B.2](#c2-allowedmethods-response) contain example response messages for services, respectively.

## 5 Retrieving the OpenAPI description of a Service

TBD

## Annex A Service Descriptions for REST Metadata Services

```yaml
openapi: 3.0.0
info:
  title: X-Road Service Metadata API for REST
  version: '0.1'
servers:
  - url: 'https://{securityserver}/'
paths:
  /listMethods:
    get:
      tags:
        - metaservices
      summary: List REST services for a service provider
      operationId: listMethods
      parameters:
        - name: serviceId
          in: query
          schema:
            type: string
        - name: X-Road-Client
          in: header
          schema:
            type: string
      responses:
        '200':
          description: List of REST services
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/methodList'
  /allowedMethods:
    get:
      tags:
        - metaservices
      summary: List of allowed REST services for a service provider
      operationId: allowedMethods
      parameters:
        - name: serviceId
          in: query
          schema:
            type: string
        - name: X-Road-Client
          in: header
          schema:
            type: string
      responses:
        '200':
          description: List of allowed REST services
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/methodList'
components:
  schemas:
    methodList:
      type: object
      properties:
        member:
          type: array
          items:
            $ref: '#/components/schemas/serviceId'
    serviceId:
      type: object
      properties:
        type:
          type: object
          properties:
            object_type:
              type: string
              enum:
                - MEMBER
                - SUBSYSTEM
                - SERVER
                - GLOBALGROUP
                - SECURITYCATEGORY
                - SERVICE
                - CENTRALSERVICE
                - LOCALGROUP
        xRoadInstance:
          type: string
        memberClass:
          type: string
        memberCode:
          type: string
        serviceVersion:
          type: string
        subsystemCode:
          type: string
        serviceCode:
          type: string
```

## Annex B Example Messages

### B.1 listMethods Response

`curl -H "accept: application/json" -H "X-Road-Client:INSTANCE/CLASS1/MEMBER1/SUBSYSTEM1" "https://SECURITYSERVER:443/r1/INSTANCE/CLASS2/MEMBER2/SUBSYSTEM2/listMethods"`

```json
{
    "service": [
        {
            "member_class": "CLASS2",
            "member_code": "MEMBER2",
            "object_type": "SERVICE",
            "service_code": "payloadgen",
            "subsystem_code": "SUBSYSTEM2",
            "xroad_instance": "INSTANCE"
        },
        {
            "member_class": "CLASS2",
            "member_code": "MEMBER2",
            "object_type": "SERVICE",
            "service_code": "kore",
            "subsystem_code": "SUBSYSTEM2",
            "xroad_instance": "INSTANCE"
        }
    ]
}
```

### B.2 allowedMethods Response

`curl -H "accept: application/json" -H "X-Road-Client:INSTANCE/CLASS1/MEMBER1/SUBSYSTEM1" "https://SECURITYSERVER:443/r1/INSTANCE/CLASS2/MEMBER2/SUBSYSTEM2/allowedMethods"`

```json
{
    "service": [
        {
            "member_class": "CLASS2",
            "member_code": "MEMBER2",
            "object_type": "SERVICE",
            "service_code": "payloadgen",
            "subsystem_code": "SUBSYSTEM2",
            "xroad_instance": "INSTANCE"
        }
    ]
}
```
