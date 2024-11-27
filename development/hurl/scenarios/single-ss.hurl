# Check that the Central Server UI is up and running
GET https://{{cs_host}}:4000

HTTP 200

# Log in to the Central Server
POST https://{{cs_host}}:4000/login
[FormParams]
username: xrd
password: secret

HTTP 200
[Captures]
cs_xsrf_token: cookie "XSRF-TOKEN"

# Add SS member to Central Server
POST https://{{cs_host}}:4000/api/v1/members
X-XSRF-TOKEN: {{cs_xsrf_token}}
Content-Type: application/json
{
  "member_id": {
    "member_class": "{{ss_member_class}}",
    "member_code": "{{ss_member_code}}"
  },
  "member_name": "{{ss_member_name}}"
}

HTTP 201

# Download global configuration anchor from Central Server
GET https://{{cs_host}}:4000/api/v1/configuration-sources/INTERNAL/anchor/download
X-XSRF-TOKEN: {{cs_xsrf_token}}

HTTP 200

[Captures]
gconf_anchor: body

# Initialize Security Server

# Check that the Security Server UI is up and running
GET https://{{ss_host}}:4000
HTTP 200

# Log in to the Security Server
POST https://{{ss_host}}:4000/login
[FormParams]
username: xrd
password: secret

HTTP 200
[Captures]
ss_xsrf_token: cookie "XSRF-TOKEN"

# Add configuration anchor
POST https://{{ss_host}}:4000/api/v1/system/anchor
X-XSRF-TOKEN: {{ss_xsrf_token}}
Content-Type: application/octet-stream
```
{{gconf_anchor}}
```

HTTP 201

# Initialize management Security Server
POST https://{{ss_host}}:4000/api/v1/initialization
X-XSRF-TOKEN: {{ss_xsrf_token}}
Content-Type: application/json
{
  "owner_member_class": "{{ss_member_class}}",
  "owner_member_code": "{{ss_member_code}}",
  "security_server_code": "{{ss_server_code}}",
  "software_token_pin": "{{ss_token_pin}}",
  "ignore_warnings": true
}

HTTP 201

# Log in to the Security Servers token
PUT https://{{ss_host}}:4000/api/v1/tokens/0/login
X-XSRF-TOKEN: {{ss_xsrf_token}}
Content-Type: application/json
{
  "password": "{{ss_token_pin}}"
}

HTTP 200

# Get the CA name
GET https://{{ss_host}}:4000/api/v1/certificate-authorities
X-XSRF-TOKEN: {{ss_xsrf_token}}

HTTP 200

[Captures]
ca_name: jsonpath "$[0].name"


# Add auth key to the Security Server token
POST https://{{ss_host}}:4000/api/v1/tokens/0/keys-with-csrs
X-XSRF-TOKEN: {{ss_xsrf_token}}
Content-Type: application/json
{
  "key_label": "Auth key",
  "csr_generate_request": {
    "key_usage_type": "AUTHENTICATION",
    "ca_name": "{{ca_name}}",
    "csr_format": "DER",
    "subject_field_values": {
      "CN": "{{ss_host}}",
      "C": "FI",
      "O": "Test client",
      "serialNumber": "DEV/{{ss_server_code}}/{{ss_member_class}}"
    }
  }
}
# TODO: This should return 201 according to the definition: 
# https://github.com/nordic-institute/X-Road/blob/e14d198f704b34b4d1789c2972e816152b6a35f8/src/security-server/openapi-model/src/main/resources/META-INF/openapi-definition.yaml#L4846
HTTP 200

[Captures]
ss_auth_key_id: jsonpath "$.key.id"
ss_auth_key_csr_id: jsonpath "$.csr_id"

# Get the auth key CSR
GET https://{{ss_host}}:4000/api/v1/keys/{{ss_auth_key_id}}/csrs/{{ss_auth_key_csr_id}}?csr_format=PEM
X-XSRF-TOKEN: {{ss_xsrf_token}}

HTTP 200

[Captures]
ss_auth_key_csr: body

# Sign the auth key CSR (note that the test CA needs filename so we can't just send it under
# [FormMultiPart] as we do with the other requests)
POST http://{{ca_host}}:8888/testca/sign
Content-Type: multipart/form-data; boundary=certboundary
```
--certboundary
Content-Disposition: form-data; name="type"

auth
--certboundary
Content-Disposition: form-data; name="certreq"; filename="auth.csr.pem"

{{ss_auth_key_csr}}
--certboundary--
```

HTTP 200
[Captures]
ss_auth_key_cert: body

# Import auth certificate
POST https://{{ss_host}}:4000/api/v1/token-certificates
X-XSRF-TOKEN: {{ss_xsrf_token}}
Content-Type: application/octet-stream
```
{{ss_auth_key_cert}}
```

HTTP 201

[Captures]
ss_auth_key_cert_hash: jsonpath "$.certificate_details.hash"

# Add sign key to the Security Server token
POST https://{{ss_host}}:4000/api/v1/tokens/0/keys-with-csrs
X-XSRF-TOKEN: {{ss_xsrf_token}}
Content-Type: application/json
{
  "key_label": "Sign key",
  "csr_generate_request": {
    "key_usage_type": "SIGNING",
    "ca_name": "{{ca_name}}",
    "csr_format": "DER",
    "member_id": "DEV:{{ss_member_class}}:{{ss_member_code}}",
    "subject_field_values": {
      "CN": "{{ss_host}}",
      "C": "FI",
      "O": "Test client",
      "serialNumber": "DEV/{{ss_server_code}}/{{ss_member_class}}"
    }
  }
}

# TODO: This should return 201 according to the definition (same as for auth key above)
HTTP 200

[Captures]
ss_sign_key_id: jsonpath "$.key.id"
ss_sign_key_csr_id: jsonpath "$.csr_id"

# Get the sign key CSR
GET https://{{ss_host}}:4000/api/v1/keys/{{ss_sign_key_id}}/csrs/{{ss_sign_key_csr_id}}?csr_format=PEM
X-XSRF-TOKEN: {{ss_xsrf_token}}

HTTP 200

[Captures]
ss_sign_key_csr: body

# Sign the sign key CSR (note that the test CA needs filename so we can't just send it under
# [FormMultiPart] as we do with the other requests)
POST http://{{ca_host}}:8888/testca/sign
Content-Type: multipart/form-data; boundary=certboundary
```
--certboundary
Content-Disposition: form-data; name="type"

sign
--certboundary
Content-Disposition: form-data; name="certreq"; filename="sign.csr.pem"

{{ss_sign_key_csr}}
--certboundary--
```

HTTP 200
[Captures]
ss_sign_key_cert: body

# Import auth certificate
POST https://{{ss_host}}:4000/api/v1/token-certificates
X-XSRF-TOKEN: {{ss_xsrf_token}}
Content-Type: application/octet-stream
```
{{ss_sign_key_cert}}
```

HTTP 201

[Captures]
ss_sign_key_cert_hash: jsonpath "$.certificate_details.hash"


# Register auth certificate
PUT https://{{ss_host}}:4000/api/v1/token-certificates/{{ss_auth_key_cert_hash}}/register
X-XSRF-TOKEN: {{ss_xsrf_token}}
{
  "address": "{{ss_host}}"
}

# TODO: This should be 200 according to the OpenAPI description but is 204:
# https://github.com/nordic-institute/X-Road/blob/e14d198f704b34b4d1789c2972e816152b6a35f8/src/security-server/openapi-model/src/main/resources/META-INF/openapi-definition.yaml#L640
HTTP 204

# Accept Security Server auth certificate registration
GET https://{{cs_host}}:4000/api/v1/management-requests?sort=id&desc=true&status=WAITING
X-XSRF-TOKEN: {{cs_xsrf_token}}

HTTP 200

[Captures]
ss_auth_cert_reg_req_id: jsonpath "$.items[0].id"

POST https://{{cs_host}}:4000/api/v1/management-requests/{{ss_auth_cert_reg_req_id}}/approval
X-XSRF-TOKEN: {{cs_xsrf_token}}

HTTP 200

# Activate auth certificate on Security Server
PUT https://{{ss_host}}:4000/api/v1/token-certificates/{{ss_auth_key_cert_hash}}/activate
X-XSRF-TOKEN: {{ss_xsrf_token}}

HTTP 204

# Set timestamping service on Security Server
GET https://{{ss_host}}:4000/api/v1/timestamping-services
X-XSRF-TOKEN: {{ss_xsrf_token}}

HTTP 200

[Captures]
tsa_name: jsonpath "$[0].name"
tsa_url: jsonpath "$[0].url"

POST https://{{ss_host}}:4000/api/v1/system/timestamping-services
X-XSRF-TOKEN: {{ss_xsrf_token}}
{
  "name": "{{tsa_name}}",
  "url": "{{tsa_url}}"
}

HTTP 201
