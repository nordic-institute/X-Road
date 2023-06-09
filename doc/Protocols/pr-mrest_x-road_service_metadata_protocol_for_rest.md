# X-Road: Service Metadata Protocol for REST <!-- omit in toc -->

**Technical Specification**

Version: 0.7  
Doc. ID: PR-MREST

---

## Version history <!-- omit in toc -->

| Date       | Version | Description                                   | Author           |
|------------|---------|-----------------------------------------------|------------------|
| 29.07.2019 | 0.1     | Initial version                               | Ilkka Seppälä    |
| 06.08.2019 | 0.2     | Add getOpenAPI description                    | Ilkka Seppälä    |
| 09.10.2019 | 0.3     | Clarify the listCentralServices response type | Jarkko Hyöty     |
| 07.11.2019 | 0.4     | Clarify getOpenAPI description                | Ilkka Seppälä    |
| 05.10.2021 | 0.5     | Update listMethods and allowedMethods         | Ilkka Seppälä    |
| 17.04.2023 | 0.6     | Remove central services support               | Justas Samuolis  |
| 10.05.2023 | 0.7     | Security Categories removed.                  | Justas Samuolis  |

## Table of Contents <!-- omit in toc -->

- [License](#license)
- [1 Introduction](#1-introduction)
    - [1.1 Terms and abbreviations](#11-terms-and-abbreviations)
    - [1.2 References](#12-references)
- [2 Retrieving List of Service Providers](#2-retrieving-list-of-service-providers)
- [3 Retrieving List of Services](#4-retrieving-list-of-services)
- [4 Retrieving the OpenAPI description of a Service](#5-retrieving-the-openapi-description-of-a-service)
- [Annex A Service Descriptions for REST Metadata Services](#annex-a-service-descriptions-for-rest-metadata-services)
- [Annex B Example Messages](#annex-b-example-messages)
    - [B.1 listMethods Response](#b1-listmethods-response)
    - [B.2 allowedMethods Response](#b2-allowedmethods-response)
    - [B.3 getOpenAPI Response](#b3-getopenapi-response)

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this
license, visit http://creativecommons.org/licenses/by-sa/3.0/

## 1 Introduction

X-Road Service Metadata protocol \[[PR-META](#Ref_PR-META)\] describes how X-Road metaservices can be called via SOAP
interface. This specification complements it by describing REST call mechanisms.

REST metaservices conform to X-Road Message Protocol for REST \[[PR-REST](#Ref_PR-REST)\].

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "
OPTIONAL" in this document (in uppercase, as shown) are to be interpreted as described in \[[RFC2119](#Ref_RFC2119)\].

### 1.1 Terms and abbreviations

See X-Road terms and abbreviations documentation \[[TA-TERMS](#Ref_TERMS)\].

### 1.2 References

1. <a name="Ref_PR-META" class="anchor"></a>\[PR-META\] X-Road: Service Metadata Protocol. Document ID:
   [PR-META](pr-meta_x-road_service_metadata_protocol.md).
2. <a name="Ref_RFC2119" class="anchor"></a>\[RFC2119\] Key words for use in RFCs to Indicate Requirement Levels,
   Internet Engineering Task Force, 1997,
   [https://www.ietf.org/rfc/rfc2119.txt](https://www.ietf.org/rfc/rfc2119.txt)
3. <a id="Ref_TERMS" class="anchor"></a>\[TA-TERMS\] X-Road Terms and Abbreviations. Document
   ID: [TA-TERMS](../terms_x-road_docs.md).
4. <a id="Ref_PR-REST" class="anchor"></a>\[PR-REST\] X-Road: Message Protocol for REST. Document ID:
   [PR-REST](pr-rest_x-road_message_protocol_for_rest.md).

## 2 Retrieving List of Service Providers

For retrieving the list of service providers listClients metaservice is used. It can be invoked with simple HTTP GET to
right URL. Service output format is controlled with Accept header. The details of listClients are described in
\[[PR-META](#Ref_PR-META)\].

## 4 Retrieving List of Services

X-Road provides two methods for getting the list of services and service endpoints offered by an X-Road client:

* `listMethods` lists all REST services and service endpoints offered by a service provider.

* `allowedMethods` lists all REST services and service endpoints offered by a service provider that the caller has
  permission to invoke. Notice that the endpoints may contain wildcards and the amount of concrete endpoints may
  actually be larger. Generally, fetching the OpenAPI service description is the preferred method for discovering
  service endpoints.

Both methods are invoked as regular X-Road REST services (see specification \[[PR-REST](#Ref_PR-REST)\] for details on
the X-Road REST protocol).

The serviceId MUST contain the identifier of the target service provider and the value of the serviceCode element MUST
be either `listMethods` or `allowedMethods`.

Request example

```http
GET /r1/INSTANCE/CLASS2/MEMBER2/SUBSYSTEM2/listMethods
```

HTTP request headers

```http
X-Road-Client: INSTANCE/CLASS1/MEMBER1/SUBSYSTEM1
```

The body of the response message MUST contain a list of services and service endpoints provided by the service
provider (in case of listMethods) or open to the given client (in case of allowedMethods). The response SHALL NOT
contain names of the metainfo services.

Annex [A](#annex-a-service-descriptions-for-rest-metadata-services) contains the OpenAPI description of the REST
metadata services.

Annexes [B.1](#c1-listmethods-response) and [B.2](#c2-allowedmethods-response) contain example response messages for
services, respectively.

## 5 Retrieving the OpenAPI description of a Service

X-Road provides a metaservice for fetching service descriptions of REST services.

* `getOpenAPI` returns the OpenAPI service description of a REST service

The method is invoked as regular X-Road REST service (see specification \[[PR-REST](#Ref_PR-REST)\] for details on the
X-Road REST protocol).

The serviceId MUST contain the identifier of the target service provider and the value of the serviceCode element MUST
be `getOpenAPI`.

The query parameters must contain `serviceCode=xxx` where xxx is the service code of the REST service we want to get the
service description from.

Request example

```http
GET /r1/INSTANCE/CLASS2/MEMBER2/SUBSYSTEM2/getOpenAPI?serviceCode=listFirms
```

HTTP request headers

```http
X-Road-Client: INSTANCE/CLASS1/MEMBER1/SUBSYSTEM1
```

Note that fetching the OpenAPI service description respects the "Verify TLS Certificate" setting of the service.

The body of the response MUST contain the OpenAPI service description of the REST service indicated by the query
parameters.

Annex [A](#annex-a-service-descriptions-for-rest-metadata-services) contains the OpenAPI description of the REST
metadata services.

Annex [B.3](#b3-getopenapi-response) contains an example response message for the service.

## Annex A Service Descriptions for REST Metadata Services

```yaml
openapi: 3.0.0
info:
  title: X-Road Service Metadata API for REST
  version: '0.3'
servers:
  - url: https://{securityserver}/r1
    variables:
      securityserver:
        default: ''
        description: 'security server address'
paths:
  /{xRoadInstance}/{memberClass}/{memberCode}/{subsystemCode}/listMethods:
    parameters:
      - $ref: '#/components/parameters/xRoadInstance'
      - $ref: '#/components/parameters/memberClass'
      - $ref: '#/components/parameters/memberCode'
      - $ref: '#/components/parameters/subsystemCode'
    get:
      tags:
        - metaservices
      summary: List REST services and endpoints for a service provider
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
          description: List of REST services and endpoints for a service provider
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/restServiceDetailsListType'
  /{xRoadInstance}/{memberClass}/{memberCode}/{subsystemCode}/allowedMethods:
    parameters:
      - $ref: '#/components/parameters/xRoadInstance'
      - $ref: '#/components/parameters/memberClass'
      - $ref: '#/components/parameters/memberCode'
      - $ref: '#/components/parameters/subsystemCode'
    get:
      tags:
        - metaservices
      summary: List of allowed REST services and endpoints for a service provider
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
          description: List of allowed REST services and endpoints for a service provider
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/restServiceDetailsListType'
  /{xRoadInstance}/{memberClass}/{memberCode}/{subsystemCode}/getOpenAPI:
    parameters:
      - $ref: "#/components/parameters/xRoadInstance"
      - $ref: "#/components/parameters/memberClass"
      - $ref: "#/components/parameters/memberCode"
      - $ref: "#/components/parameters/subsystemCode"
    get:
      tags:
        - metaservices
      summary: Returns OpenAPI service description for a REST service
      operationId: getOpenAPI
      parameters:
        - name: serviceCode
          in: query
          schema:
            type: string
        - name: X-Road-Client
          in: header
          schema:
            type: string
      responses:
        '200':
          description: OpenAPI description of the specified REST service
          content:
            application/json:
              schema:
                type: string
            text/yaml:
              schema:
                type: string
        '400':
          description: Error in request
        '500':
          description: Internal error
components:
  parameters:
    xRoadInstance:
      name: xRoadInstance
      required: true
      in: path
      schema:
        type: string
    memberClass:
      name: memberClass
      required: true
      in: path
      schema:
        type: string
    memberCode:
      name: memberCode
      required: true
      in: path
      schema:
        type: string
    subsystemCode:
      name: subsystemCode
      required: true
      in: path
      schema:
        type: string
  schemas:
    restServiceDetailsListType:
      type: object
      properties:
        member:
          type: array
          items:
            $ref: '#/components/schemas/xroadRestServiceDetailsType'
    xroadRestServiceDetailsType:
      type: object
      properties:
        objectType:
          type: object
          properties:
            object_type:
              type: string
              enum:
                - MEMBER
                - SUBSYSTEM
                - SERVER
                - GLOBALGROUP
                - SERVICE
                - LOCALGROUP
        serviceType:
          type: string
        xRoadInstance:
          type: string
        memberClass:
          type: string
        memberCode:
          type: string
        subsystemCode:
          type: string
        serviceCode:
          type: string
        serviceVersion:
          type: string
        endpointList:
          type: object
          properties:
            member:
              type: array
              items:
                $ref: '#/components/schemas/endpoint'
    endpoint:
      type: object
      properties:
        method:
          type: string
        path:
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
      "service_type": "OPENAPI",
      "subsystem_code": "SUBSYSTEM2",
      "xroad_instance": "INSTANCE",
      "endpoint_list": [
        {
          "method": "PUT",
          "path": "/pet"
        },
        {
          "method": "POST",
          "path": "/pet"
        },
        {
          "method": "GET",
          "path": "/pet/findByStatus"
        },
        {
          "method": "GET",
          "path": "/pet/findByTags"
        },
        {
          "method": "GET",
          "path": "/pet/*"
        },
        {
          "method": "POST",
          "path": "/pet/*"
        },
        {
          "method": "DELETE",
          "path": "/pet/*"
        },
        {
          "method": "POST",
          "path": "/pet/*/uploadImage"
        },
        {
          "method": "GET",
          "path": "/store/inventory"
        },
        {
          "method": "POST",
          "path": "/store/order"
        },
        {
          "method": "GET",
          "path": "/store/order/*"
        },
        {
          "method": "DELETE",
          "path": "/store/order/*"
        },
        {
          "method": "POST",
          "path": "/user"
        },
        {
          "method": "POST",
          "path": "/user/createWithList"
        },
        {
          "method": "GET",
          "path": "/user/login"
        },
        {
          "method": "GET",
          "path": "/user/logout"
        },
        {
          "method": "GET",
          "path": "/user/*"
        },
        {
          "method": "PUT",
          "path": "/user/*"
        },
        {
          "method": "DELETE",
          "path": "/user/*"
        }
      ]
    },
    {
      "member_class": "CLASS2",
      "member_code": "MEMBER2",
      "object_type": "SERVICE",
      "service_code": "kore",
      "service_type": "REST",
      "subsystem_code": "SUBSYSTEM2",
      "xroad_instance": "INSTANCE",
      "endpoint_list": [
        {
          "method": "GET",
          "path": "/school"
        },
        {
          "method": "PUT",
          "path": "/school"
        },
        {
          "method": "POST",
          "path": "/school"
        }
      ]
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
      "service_type": "OPENAPI",
      "subsystem_code": "SUBSYSTEM2",
      "xroad_instance": "INSTANCE",
      "endpoint_list": [
        {
          "method": "GET",
          "path": "/pet/findByStatus"
        },
        {
          "method": "GET",
          "path": "/pet/findByTags"
        },
        {
          "method": "GET",
          "path": "/pet/*"
        }
      ]
    }
  ]
}
```

### B.3 getOpenAPI Response

`curl -H "accept: application/json" -H "X-Road-Client:INSTANCE/CLASS1/MEMBER1/SUBSYSTEM1" "https://SECURITYSERVER:443/r1/INSTANCE/CLASS2/MEMBER2/SUBSYSTEM2/getOpenAPI?serviceCode=listFirms"`

```yaml
openapi: "3.0.0"
info:
  version: 1.0.0
  title: Firm listing
servers:
  - url: https://example.com
paths:
  /firms:
    get:
      summary: List all firms
      operationId: listFirms
      tags:
        - firms
      parameters:
        - name: limit
          in: query
          description: How many items to return at one time (max 100)
          required: false
          schema:
            type: integer
            format: int32
      responses:
        '200':
          description: A paged array of firms
          headers:
            x-next:
              description: A link to the next page of responses
              schema:
                type: string
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/Firms"
        default:
          description: unexpected error
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/Error"
components:
  schemas:
    Firm:
      required:
        - id
        - name
        - size
        - country
      properties:
        id:
          type: integer
          format: int64
        name:
          type: string
        tag:
          type: string
    Firms:
      type: array
      items:
        $ref: "#/components/schemas/Firm"
    Error:
      required:
        - code
        - message
      properties:
        code:
          type: integer
          format: int32
        message:
          type: string
```
