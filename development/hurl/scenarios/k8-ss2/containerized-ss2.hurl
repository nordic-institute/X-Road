# Log in to the Central Server
POST https://{{cs_host}}:{{cs_port}}/login
[FormParams]
username: xrd
password: secret

HTTP 200
[Captures]
cs_xsrf_token: cookie "XSRF-TOKEN"

# Download global configuration anchor from Central Server
GET https://{{cs_host}}:{{cs_port}}/api/v1/configuration-sources/INTERNAL/anchor/download
X-XSRF-TOKEN: {{cs_xsrf_token}}
[Options]
output: gconf_anchor.xml

HTTP 200

# Initialize Security Server 2 (ss2)

# Check that the Security Server UI is up and running
GET https://{{ss2_host}}:4000
HTTP 200

# Log in to the Security Server
POST https://{{ss2_host}}:4000/login
[FormParams]
username: xrd
password: secret

HTTP 200
[Captures]
ss2_xsrf_token: cookie "XSRF-TOKEN"

# Add configuration anchor
POST https://{{ss2_host}}:4000/api/v1/system/anchor
X-XSRF-TOKEN: {{ss2_xsrf_token}}
[MultipartFormData]
anchor: file,gconf_anchor.xml;

HTTP 201

# Initialize Security Server
POST https://{{ss2_host}}:4000/api/v1/initialization
X-XSRF-TOKEN: {{ss2_xsrf_token}}
Content-Type: application/json
{
  "owner_member_class": "ORG",
  "owner_member_code": "SS2-Member",
  "security_server_code": "SS2",
  "software_token_pin": "Secret1234",
  "ignore_warnings": true
}

HTTP 201

# Log in to the Security Servers token
PUT https://{{ss2_host}}:4000/api/v1/tokens/0/login
X-XSRF-TOKEN: {{ss2_xsrf_token}}
Content-Type: application/json
{
  "password": "Secret1234"
}

HTTP *

# Get the CA name
GET https://{{ss2_host}}:4000/api/v1/certificate-authorities
X-XSRF-TOKEN: {{ss2_xsrf_token}}

HTTP 200

[Captures]
ca_name: jsonpath "$[0].name"

# Add auth key to the Security Server token
POST https://{{ss2_host}}:4000/api/v1/tokens/0/keys-with-csrs
X-XSRF-TOKEN: {{ss2_xsrf_token}}
Content-Type: application/json
{
  "key_label": "Auth key",
  "csr_generate_request": {
    "key_usage_type": "AUTHENTICATION",
    "ca_name": "{{ca_name}}",
    "csr_format": "DER",
    "subject_field_values": {
      "CN": "proxy",
      "C": "FI",
      "O": "Test SS2 client",
      "serialNumber": "DEV/SS2/ORG",
      "subjectAltName": "proxy,localhost"
    }
  }
}
# TODO: This should return 201 according to the definition:
# https://github.com/nordic-institute/X-Road/blob/e14d198f704b34b4d1789c2972e816152b6a35f8/src/security-server/openapi-model/src/main/resources/META-INF/openapi-definition.yaml#L4846
HTTP 200

[Captures]
ss2_auth_key_id: jsonpath "$.key.id"
ss2_auth_key_csr_id: jsonpath "$.csr_id"

# Get the auth key CSR
GET https://{{ss2_host}}:4000/api/v1/keys/{{ss2_auth_key_id}}/csrs/{{ss2_auth_key_csr_id}}?csr_format=PEM
X-XSRF-TOKEN: {{ss2_xsrf_token}}

HTTP 200

[Captures]
ss2_auth_key_csr: body

# Sign the auth key CSR (note that the test CA needs filename so we can't just send it under
# [FormMultiPart] as we do with the other requests)
POST http://{{ca_host}}:{{ca_port}}/testca/sign
Content-Type: multipart/form-data; boundary=certboundary
[Options]
output: ss2_auth_key_cert.pem
```
--certboundary
Content-Disposition: form-data; name="type"

auth
--certboundary
Content-Disposition: form-data; name="certreq"; filename="auth.csr.pem"

{{ss2_auth_key_csr}}
--certboundary--
```

HTTP 200

# Import auth certificate
POST https://{{ss2_host}}:4000/api/v1/token-certificates
X-XSRF-TOKEN: {{ss2_xsrf_token}}
[MultipartFormData]
certificate: file,ss2_auth_key_cert.pem;

HTTP 201

[Captures]
ss2_auth_key_cert_hash: jsonpath "$.certificate_details.hash"

# Add sign key to the Security Server token
POST https://{{ss2_host}}:4000/api/v1/tokens/0/keys-with-csrs
X-XSRF-TOKEN: {{ss2_xsrf_token}}
Content-Type: application/json
{
  "key_label": "Sign key",
  "csr_generate_request": {
    "key_usage_type": "SIGNING",
    "ca_name": "{{ca_name}}",
    "csr_format": "DER",
    "member_id": "DEV:ORG:SS2-Member",
    "subject_field_values": {
      "CN": "proxy",
      "C": "FI",
      "O": "Test SS2 client",
      "serialNumber": "DEV/ss2/ORG",
      "subjectAltName": "proxy,localhost"
    }
  }
}

# TODO: This should return 201 according to the definition (same as for auth key above)
HTTP 200

[Captures]
ss2_sign_key_id: jsonpath "$.key.id"
ss2_sign_key_csr_id: jsonpath "$.csr_id"

# Get the sign key CSR
GET https://{{ss2_host}}:4000/api/v1/keys/{{ss2_sign_key_id}}/csrs/{{ss2_sign_key_csr_id}}?csr_format=PEM
X-XSRF-TOKEN: {{ss2_xsrf_token}}

HTTP 200

[Captures]
ss2_sign_key_csr: body

# Sign the sign key CSR (note that the test CA needs filename so we can't just send it under
# [FormMultiPart] as we do with the other requests)
POST http://{{ca_host}}:{{ca_port}}/testca/sign
Content-Type: multipart/form-data; boundary=certboundary
[Options]
output: ss2_sign_key_cert.pem
```
--certboundary
Content-Disposition: form-data; name="type"

sign
--certboundary
Content-Disposition: form-data; name="certreq"; filename="sign.csr.pem"

{{ss2_sign_key_csr}}
--certboundary--
```

HTTP 200

# Import auth certificate
POST https://{{ss2_host}}:4000/api/v1/token-certificates
X-XSRF-TOKEN: {{ss2_xsrf_token}}
[MultipartFormData]
certificate: file,ss2_sign_key_cert.pem;

HTTP 201

[Captures]
ss2_sign_key_cert_hash: jsonpath "$.certificate_details.hash"


# Register auth certificate
PUT https://{{ss2_host}}:4000/api/v1/token-certificates/{{ss2_auth_key_cert_hash}}/register
X-XSRF-TOKEN: {{ss2_xsrf_token}}
{
  "address": "proxy"
}

# TODO: This should be 200 according to the OpenAPI description but is 204:
# https://github.com/nordic-institute/X-Road/blob/e14d198f704b34b4d1789c2972e816152b6a35f8/src/security-server/openapi-model/src/main/resources/META-INF/openapi-definition.yaml#L640
HTTP 204

# Accept Security Server auth certificate registration
GET https://{{cs_host}}:{{cs_port}}/api/v1/management-requests?sort=id&desc=true&status=WAITING
X-XSRF-TOKEN: {{cs_xsrf_token}}

HTTP 200

[Captures]
ss2_auth_cert_reg_req_id: jsonpath "$.items[0].id"

POST https://{{cs_host}}:{{cs_port}}/api/v1/management-requests/{{ss2_auth_cert_reg_req_id}}/approval
X-XSRF-TOKEN: {{cs_xsrf_token}}

HTTP 200

# Activate auth certificate on ss2 Security Server
PUT https://{{ss2_host}}:4000/api/v1/token-certificates/{{ss2_auth_key_cert_hash}}/activate
X-XSRF-TOKEN: {{ss2_xsrf_token}}

HTTP 204

# Set timestamping service on ss2 Security Server
GET https://{{ss2_host}}:4000/api/v1/timestamping-services
X-XSRF-TOKEN: {{ss2_xsrf_token}}

HTTP 200

[Captures]
tsa_name: jsonpath "$[0].name"
tsa_url: jsonpath "$[0].url"

POST https://{{ss2_host}}:4000/api/v1/system/timestamping-services
X-XSRF-TOKEN: {{ss2_xsrf_token}}
{
  "name": "{{tsa_name}}",
  "url": "{{tsa_url}}"
}

HTTP 201

# Add Subsystem to ss2 Security Server
POST https://{{ss2_host}}:4000/api/v1/clients
X-XSRF-TOKEN: {{ss2_xsrf_token}}
{
  "ignore_warnings": true,
  "client": {
    "member_class": "ORG",
    "member_code": "SS2-Member",
    "subsystem_code": "ss2-subsystem",
    "connection_type": "HTTP"
  }
}

HTTP 201

[Captures]
ss2_test_client_id: jsonpath "$.id"

# Register subsystem
PUT https://{{ss2_host}}:4000/api/v1/clients/{{ss2_test_client_id}}/register
X-XSRF-TOKEN: {{ss2_xsrf_token}}

HTTP 204

# Accept Security Server ss2-subsystem registration
GET https://{{cs_host}}:{{cs_port}}/api/v1/management-requests?sort=id&desc=true&status=WAITING
X-XSRF-TOKEN: {{cs_xsrf_token}}

HTTP 200

[Captures]
ss1_test_client_req_id: jsonpath "$.items[0].id"

POST https://{{cs_host}}:{{cs_port}}/api/v1/management-requests/{{ss1_test_client_req_id}}/approval
X-XSRF-TOKEN: {{cs_xsrf_token}}

HTTP 200

# grant access right to ss2-subsystem for services on SS0
# login to ss0
POST https://{{ss0_host}}:{{ss0_port}}/login
[FormParams]
username: xrd
password: secret

HTTP 200
[Captures]
ss0_xsrf_token: cookie "XSRF-TOKEN"

POST https://{{ss0_host}}:{{ss0_port}}/api/v1/clients/DEV%3ACOM%3A1234%3ATestService/service-clients/DEV%3AORG%3ASS2-Member%3Ass2-subsystem/access-rights
X-XSRF-TOKEN: {{ss0_xsrf_token}}
{
  "items": [
    {
       "service_code": "getRandom"
    },
    {
      "service_code": "helloService"
    },
    {
      "service_code": "mock1"
     }
    ]
}

HTTP 201
