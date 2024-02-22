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

# Initialize Central Server
POST https://{{cs_host}}:4000/api/v1/initialization
X-XSRF-TOKEN: {{cs_xsrf_token}}
Content-Type: application/json
{
  "software_token_pin": "Secret1234",
  "instance_identifier": "DEV",
  "central_server_address": "cs"
}

# TODO: The Central Server currently returns the incorrect HTTP code, the documentation notes that it should be "201":
# https://github.com/nordic-institute/X-Road/blob/6ef2d9e83287ccb281b0cc27ba663a5ae8c4aa16/src/central-server/openapi-model/src/main/resources/openapi-definition.yaml#L1344
HTTP 200


# Add member class to Central Server
POST https://{{cs_host}}:4000/api/v1/member-classes
X-XSRF-TOKEN: {{cs_xsrf_token}}
Content-Type: application/json
{
  "code": "COM",
  "description": "Commercial"
}

HTTP 201

# Log in Central Server token
PUT https://{{cs_host}}:4000/api/v1/tokens/0/login
X-XSRF-TOKEN: {{cs_xsrf_token}}
Content-Type: application/json
{
  "password": "Secret1234"
}

HTTP 200

# Add internal signing key to Central Server
POST https://{{cs_host}}:4000/api/v1/configuration-sources/INTERNAL/signing-keys
X-XSRF-TOKEN: {{cs_xsrf_token}}
Content-Type: application/json
{
  "key_label": "Internal signing key",
  "token_id": 0
}

# TODO: The Central Server currently returns the incorrect HTTP code, the documentation notes that it should be "201":
# https://github.com/nordic-institute/X-Road/blob/6ef2d9e83287ccb281b0cc27ba663a5ae8c4aa16/src/central-server/openapi-model/src/main/resources/openapi-definition.yaml#L1051
HTTP 200

# Add external signing key to Central Server
POST https://{{cs_host}}:4000/api/v1/configuration-sources/EXTERNAL/signing-keys
X-XSRF-TOKEN: {{cs_xsrf_token}}
Content-Type: application/json
{
  "key_label": "External signing key",
  "token_id": 0
}

# TODO: The Central Server currently returns the incorrect HTTP code, the documentation notes that it should be "201":
# https://github.com/nordic-institute/X-Road/blob/6ef2d9e83287ccb281b0cc27ba663a5ae8c4aa16/src/central-server/openapi-model/src/main/resources/openapi-definition.yaml#L1051
HTTP 200

# Add member to Central Server
POST https://{{cs_host}}:4000/api/v1/members
X-XSRF-TOKEN: {{cs_xsrf_token}}
Content-Type: application/json
#[Cookies]
#JSESSIONID: {{cssessid}}
#XSRF-TOKEN: {{cs_xsrf_token}}
{
  "member_id": {
    "member_class": "COM",
    "member_code": "1234"
  },
  "member_name": "Test member"
}

HTTP 201

# Add SS1 member to Central Server
POST https://{{cs_host}}:4000/api/v1/members
X-XSRF-TOKEN: {{cs_xsrf_token}}
Content-Type: application/json
{
  "member_id": {
    "member_class": "COM",
    "member_code": "4321"
  },
  "member_name": "Test client"
}

HTTP 201

# Add management subsystem for member 1234 to Central Server
POST https://{{cs_host}}:4000/api/v1/subsystems
X-XSRF-TOKEN: {{cs_xsrf_token}}
Content-Type: application/json
#[Cookies]
#JSESSIONID: {{cssessid}}
#XSRF-TOKEN: {{cs_xsrf_token}}
{
  "subsystem_id": {
    "member_class": "COM",
    "member_code": "1234",
    "subsystem_code": "MANAGEMENT"
  }
}

HTTP 201


# Set management subsystem for member 1234 as the management serice for the Central Server
PATCH https://{{cs_host}}:4000/api/v1/management-services-configuration
X-XSRF-TOKEN: {{cs_xsrf_token}}
Content-Type: application/json
#[Cookies]
#JSESSIONID: {{cssessid}}
#XSRF-TOKEN: {{cs_xsrf_token}}
{
  "service_provider_id": "DEV:COM:1234:MANAGEMENT"
}

HTTP 200

# Add test CA to Central Server configuration
POST https://{{cs_host}}:4000/api/v1/certification-services
X-XSRF-TOKEN: {{cs_xsrf_token}}
[MultipartFormData]
certificate_profile_info: ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider
tls_auth: false
certificate: file,ca/CA/certs/ca.pem;

HTTP 201

[Captures]
ca_id: jsonpath "$.id"

# Add OCSP to test CA in the Central Server configuration
POST https://{{cs_host}}:4000/api/v1/certification-services/{{ca_id}}/ocsp-responders
X-XSRF-TOKEN: {{cs_xsrf_token}}
[MultipartFormData]
url: http://{{cs_host}}:8888
certificate: file,ca/CA/certs/ocsp.pem;

HTTP 201

# Add TSA to the Central Server
POST https://{{cs_host}}:4000/api/v1/timestamping-services
X-XSRF-TOKEN: {{cs_xsrf_token}}
[MultipartFormData]
url: http://{{cs_host}}:8899
certificate: file,ca/CA/certs/tsa.pem;


# Download global configuration anchor from Central Server
GET https://{{cs_host}}:4000/api/v1/configuration-sources/INTERNAL/anchor/download
X-XSRF-TOKEN: {{cs_xsrf_token}}

HTTP 200

[Captures]
gconf_anchor: body

# Initialize management Security Server

# Check that the Security Server UI is up and running
GET https://{{ssm_host}}:4000
HTTP 200

# Log in to the Security Server
POST https://{{ssm_host}}:4000/login
[FormParams]
username: xrd
password: secret

HTTP 200
[Captures]
ssm_xsrf_token: cookie "XSRF-TOKEN"

# Add configuration anchor
POST https://{{ssm_host}}:4000/api/v1/system/anchor
X-XSRF-TOKEN: {{ssm_xsrf_token}}
Content-Type: application/octet-stream
```
{{gconf_anchor}}
```

HTTP 201

# Initialize management Security Server
POST https://{{ssm_host}}:4000/api/v1/initialization
X-XSRF-TOKEN: {{ssm_xsrf_token}}
Content-Type: application/json
{
  "owner_member_class": "COM",
  "owner_member_code": "1234",
  "security_server_code": "SSM",
  "software_token_pin": "Secret1234",
  "ignore_warnings": true
}

HTTP 201

# Log in to the Security Servers token
PUT https://{{ssm_host}}:4000/api/v1/tokens/0/login
X-XSRF-TOKEN: {{ssm_xsrf_token}}
Content-Type: application/json
{
  "password": "Secret1234"
}

HTTP 200

# Get the CA name
GET https://{{ssm_host}}:4000/api/v1/certificate-authorities
X-XSRF-TOKEN: {{ssm_xsrf_token}}

HTTP 200

[Captures]
ca_name: jsonpath "$[0].name"

# Add auth key to the Security Server token
POST https://{{ssm_host}}:4000/api/v1/tokens/0/keys-with-csrs
X-XSRF-TOKEN: {{ssm_xsrf_token}}
Content-Type: application/json
{
  "key_label": "Auth key",
  "csr_generate_request": {
    "key_usage_type": "AUTHENTICATION",
    "ca_name": "{{ca_name}}",
    "csr_format": "DER",
    "subject_field_values": {
      "CN": "{{ssm_host}}",
      "C": "FI",
      "O": "Test member",
      "serialNumber": "DEV/SSM/COM"
    }
  }
}
# TODO: This should return 201 according to the definition: 
# https://github.com/nordic-institute/X-Road/blob/e14d198f704b34b4d1789c2972e816152b6a35f8/src/security-server/openapi-model/src/main/resources/META-INF/openapi-definition.yaml#L4846
HTTP 200

[Captures]
ssm_auth_key_id: jsonpath "$.key.id"
ssm_auth_key_csr_id: jsonpath "$.csr_id"

# Get the auth key CSR
GET https://{{ssm_host}}:4000/api/v1/keys/{{ssm_auth_key_id}}/csrs/{{ssm_auth_key_csr_id}}?csr_format=PEM
X-XSRF-TOKEN: {{ssm_xsrf_token}}

HTTP 200

[Captures]
ssm_auth_key_csr: body

# Sign the auth key CSR (note that the test CA needs filename so we can't just send it under
# [FormMultiPart] as we do with the other requests)
POST http://{{cs_host}}:8888/testca/sign
Content-Type: multipart/form-data; boundary=certboundary
```
--certboundary
Content-Disposition: form-data; name="type"

auth
--certboundary
Content-Disposition: form-data; name="certreq"; filename="auth.csr.pem"

{{ssm_auth_key_csr}}
--certboundary--
```

HTTP 200
[Captures]
ssm_auth_key_cert: body

# Import auth certificate
POST https://{{ssm_host}}:4000/api/v1/token-certificates
X-XSRF-TOKEN: {{ssm_xsrf_token}}
Content-Type: application/octet-stream
```
{{ssm_auth_key_cert}}
```

HTTP 201

[Captures]
ssm_auth_key_cert_hash: jsonpath "$.certificate_details.hash"

# Add sign key to the Security Server token
POST https://{{ssm_host}}:4000/api/v1/tokens/0/keys-with-csrs
X-XSRF-TOKEN: {{ssm_xsrf_token}}
Content-Type: application/json
{
  "key_label": "Sign key",
  "csr_generate_request": {
    "key_usage_type": "SIGNING",
    "ca_name": "{{ca_name}}",
    "csr_format": "DER",
    "member_id": "DEV:COM:1234",
    "subject_field_values": {
      "CN": "{{ssm_host}}",
      "C": "FI",
      "O": "Test member",
      "serialNumber": "DEV/SSM/COM"
    }
  }
}

# TODO: This should return 201 according to the definition (same as for auth key above)
HTTP 200

[Captures]
ssm_sign_key_id: jsonpath "$.key.id"
ssm_sign_key_csr_id: jsonpath "$.csr_id"

# Get the sign key CSR
GET https://{{ssm_host}}:4000/api/v1/keys/{{ssm_sign_key_id}}/csrs/{{ssm_sign_key_csr_id}}?csr_format=PEM
X-XSRF-TOKEN: {{ssm_xsrf_token}}

HTTP 200

[Captures]
ssm_sign_key_csr: body

# Sign the sign key CSR (note that the test CA needs filename so we can't just send it under
# [FormMultiPart] as we do with the other requests)
POST http://{{cs_host}}:8888/testca/sign
Content-Type: multipart/form-data; boundary=certboundary
```
--certboundary
Content-Disposition: form-data; name="type"

sign
--certboundary
Content-Disposition: form-data; name="certreq"; filename="sign.csr.pem"

{{ssm_sign_key_csr}}
--certboundary--
```

HTTP 200
[Captures]
ssm_sign_key_cert: body

# Import auth certificate
POST https://{{ssm_host}}:4000/api/v1/token-certificates
X-XSRF-TOKEN: {{ssm_xsrf_token}}
Content-Type: application/octet-stream
```
{{ssm_sign_key_cert}}
```

HTTP 201

[Captures]
ssm_sign_key_cert_hash: jsonpath "$.certificate_details.hash"


# Register auth certificate
PUT https://{{ssm_host}}:4000/api/v1/token-certificates/{{ssm_auth_key_cert_hash}}/register
X-XSRF-TOKEN: {{ssm_xsrf_token}}
{
  "address": "{{ssm_host}}"
}

# TODO: This should be 200 according to the OpenAPI description but is 204:
# https://github.com/nordic-institute/X-Road/blob/e14d198f704b34b4d1789c2972e816152b6a35f8/src/security-server/openapi-model/src/main/resources/META-INF/openapi-definition.yaml#L640
HTTP 204

# Accept Security Server auth certificate registration
GET https://{{cs_host}}:4000/api/v1/management-requests?sort=id&desc=true&status=WAITING
X-XSRF-TOKEN: {{cs_xsrf_token}}

HTTP 200

[Captures]
ssm_auth_cert_reg_req_id: jsonpath "$.items[0].id"

POST https://{{cs_host}}:4000/api/v1/management-requests/{{ssm_auth_cert_reg_req_id}}/approval
X-XSRF-TOKEN: {{cs_xsrf_token}}

HTTP 200

# Register management subsystem to SSM Security Server
POST https://{{cs_host}}:4000/api/v1/management-services-configuration/register-provider
X-XSRF-TOKEN: {{cs_xsrf_token}}
{
  "security_server_id": "DEV:COM:1234:SSM"
}

HTTP 200

# Add MANAGEMENT to management Security Server
POST https://{{ssm_host}}:4000/api/v1/clients
X-XSRF-TOKEN: {{ssm_xsrf_token}}
{
  "ignore_warnings": true,
  "client": {
    "member_class": "COM",
    "member_code": "1234",
    "subsystem_code": "MANAGEMENT",
    "connection_type": "HTTP"
  }
}

HTTP 201

[Captures]
ssm_management_client_id: jsonpath "$.id"

# Get Central Server management services settings
GET https://{{cs_host}}:4000/api/v1/management-services-configuration
X-XSRF-TOKEN: {{cs_xsrf_token}}

HTTP 200

[Captures]
cs_management_service_address: jsonpath "$.services_address"
cs_management_service_wsdl: jsonpath "$.wsdl_address"

# Add Central Server management service to SSM Security Server
POST https://{{ssm_host}}:4000/api/v1/clients/{{ssm_management_client_id}}/service-descriptions
X-XSRF-TOKEN: {{ssm_xsrf_token}}
{
  "url": "{{cs_management_service_wsdl}}",
  "type": "WSDL",
  "ignore_warnings": true
}

HTTP 201

[Captures]
ssm_management_service_id: jsonpath "$.id"
ssm_auth_cert_deletion_service_id: jsonpath "$.services[0].id"

# Add security server owners group access to management services
PATCH https://{{ssm_host}}:4000/api/v1/services/{{ssm_auth_cert_deletion_service_id}}
X-XSRF-TOKEN: {{ssm_xsrf_token}}
{
  "ignore_warnings": true,
  "ssl_auth": false,
  "ssl_auth_all": true,
  "timeout": 60,
  "timeout_all": true,
  "url": "{{cs_management_service_address}}",
  "url_all": true
}

# Add security server owners group access to management services
POST https://{{ssm_host}}:4000/api/v1/clients/{{ssm_management_client_id}}/service-clients/DEV:security-server-owners/access-rights
X-XSRF-TOKEN: {{ssm_xsrf_token}}
{
  "items": [
    {
      "service_code": "authCertDeletion"
    },
    {
      "service_code": "clientDeletion"
    },
    {
      "service_code": "clientReg"
    },
    {
      "service_code": "ownerChange"
    }
  ] 
}

# Enable management subsystem on management Security Server
PUT https://{{ssm_host}}:4000/api/v1/service-descriptions/{{ssm_management_service_id}}/enable
X-XSRF-TOKEN: {{ssm_xsrf_token}}

# TODO: This should be 204 according to the OpenAPI description but is 200:
# https://github.com/nordic-institute/X-Road/blob/e14d198f704b34b4d1789c2972e816152b6a35f8/src/security-server/openapi-model/src/main/resources/META-INF/openapi-definition.yaml#L3908
HTTP 200

# Activate auth certificate on SSM Security Server
PUT https://{{ssm_host}}:4000/api/v1/token-certificates/{{ssm_auth_key_cert_hash}}/activate
X-XSRF-TOKEN: {{ssm_xsrf_token}}

HTTP 204

# Set timestamping service on SSM Security Server
GET https://{{ssm_host}}:4000/api/v1/timestamping-services
X-XSRF-TOKEN: {{ssm_xsrf_token}}

HTTP 200

[Captures]
tsa_name: jsonpath "$[0].name"
tsa_url: jsonpath "$[0].url"

POST https://{{ssm_host}}:4000/api/v1/system/timestamping-services
X-XSRF-TOKEN: {{ssm_xsrf_token}}
{
  "name": "{{tsa_name}}",
  "url": "{{tsa_url}}"
}

HTTP 201

# Initialize Security Server 1 (SS1)

# Check that the Security Server UI is up and running
GET https://{{ss1_host}}:4000
HTTP 200

# Log in to the Security Server
POST https://{{ss1_host}}:4000/login
[FormParams]
username: xrd
password: secret

HTTP 200
[Captures]
ss1_xsrf_token: cookie "XSRF-TOKEN"

# Add configuration anchor
POST https://{{ss1_host}}:4000/api/v1/system/anchor
X-XSRF-TOKEN: {{ss1_xsrf_token}}
Content-Type: application/octet-stream
```
{{gconf_anchor}}
```

HTTP 201

# Initialize management Security Server
POST https://{{ss1_host}}:4000/api/v1/initialization
X-XSRF-TOKEN: {{ss1_xsrf_token}}
Content-Type: application/json
{
  "owner_member_class": "COM",
  "owner_member_code": "4321",
  "security_server_code": "SS1",
  "software_token_pin": "Secret1234",
  "ignore_warnings": true
}

HTTP 201

# Log in to the Security Servers token
PUT https://{{ss1_host}}:4000/api/v1/tokens/0/login
X-XSRF-TOKEN: {{ss1_xsrf_token}}
Content-Type: application/json
{
  "password": "Secret1234"
}

HTTP 200

# Add auth key to the Security Server token
POST https://{{ss1_host}}:4000/api/v1/tokens/0/keys-with-csrs
X-XSRF-TOKEN: {{ss1_xsrf_token}}
Content-Type: application/json
{
  "key_label": "Auth key",
  "csr_generate_request": {
    "key_usage_type": "AUTHENTICATION",
    "ca_name": "{{ca_name}}",
    "csr_format": "DER",
    "subject_field_values": {
      "CN": "{{ss1_host}}",
      "C": "FI",
      "O": "Test client",
      "serialNumber": "DEV/SS1/COM"
    }
  }
}
# TODO: This should return 201 according to the definition: 
# https://github.com/nordic-institute/X-Road/blob/e14d198f704b34b4d1789c2972e816152b6a35f8/src/security-server/openapi-model/src/main/resources/META-INF/openapi-definition.yaml#L4846
HTTP 200

[Captures]
ss1_auth_key_id: jsonpath "$.key.id"
ss1_auth_key_csr_id: jsonpath "$.csr_id"

# Get the auth key CSR
GET https://{{ss1_host}}:4000/api/v1/keys/{{ss1_auth_key_id}}/csrs/{{ss1_auth_key_csr_id}}?csr_format=PEM
X-XSRF-TOKEN: {{ss1_xsrf_token}}

HTTP 200

[Captures]
ss1_auth_key_csr: body

# Sign the auth key CSR (note that the test CA needs filename so we can't just send it under
# [FormMultiPart] as we do with the other requests)
POST http://{{cs_host}}:8888/testca/sign
Content-Type: multipart/form-data; boundary=certboundary
```
--certboundary
Content-Disposition: form-data; name="type"

auth
--certboundary
Content-Disposition: form-data; name="certreq"; filename="auth.csr.pem"

{{ss1_auth_key_csr}}
--certboundary--
```

HTTP 200
[Captures]
ss1_auth_key_cert: body

# Import auth certificate
POST https://{{ss1_host}}:4000/api/v1/token-certificates
X-XSRF-TOKEN: {{ss1_xsrf_token}}
Content-Type: application/octet-stream
```
{{ss1_auth_key_cert}}
```

HTTP 201

[Captures]
ss1_auth_key_cert_hash: jsonpath "$.certificate_details.hash"

# Add sign key to the Security Server token
POST https://{{ss1_host}}:4000/api/v1/tokens/0/keys-with-csrs
X-XSRF-TOKEN: {{ss1_xsrf_token}}
Content-Type: application/json
{
  "key_label": "Sign key",
  "csr_generate_request": {
    "key_usage_type": "SIGNING",
    "ca_name": "{{ca_name}}",
    "csr_format": "DER",
    "member_id": "DEV:COM:4321",
    "subject_field_values": {
      "CN": "{{ss1_host}}",
      "C": "FI",
      "O": "Test client",
      "serialNumber": "DEV/SS1/COM"
    }
  }
}

# TODO: This should return 201 according to the definition (same as for auth key above)
HTTP 200

[Captures]
ss1_sign_key_id: jsonpath "$.key.id"
ss1_sign_key_csr_id: jsonpath "$.csr_id"

# Get the sign key CSR
GET https://{{ss1_host}}:4000/api/v1/keys/{{ss1_sign_key_id}}/csrs/{{ss1_sign_key_csr_id}}?csr_format=PEM
X-XSRF-TOKEN: {{ss1_xsrf_token}}

HTTP 200

[Captures]
ss1_sign_key_csr: body

# Sign the sign key CSR (note that the test CA needs filename so we can't just send it under
# [FormMultiPart] as we do with the other requests)
POST http://{{cs_host}}:8888/testca/sign
Content-Type: multipart/form-data; boundary=certboundary
```
--certboundary
Content-Disposition: form-data; name="type"

sign
--certboundary
Content-Disposition: form-data; name="certreq"; filename="sign.csr.pem"

{{ss1_sign_key_csr}}
--certboundary--
```

HTTP 200
[Captures]
ss1_sign_key_cert: body

# Import auth certificate
POST https://{{ss1_host}}:4000/api/v1/token-certificates
X-XSRF-TOKEN: {{ss1_xsrf_token}}
Content-Type: application/octet-stream
```
{{ss1_sign_key_cert}}
```

HTTP 201

[Captures]
ss1_sign_key_cert_hash: jsonpath "$.certificate_details.hash"


# Register auth certificate
PUT https://{{ss1_host}}:4000/api/v1/token-certificates/{{ss1_auth_key_cert_hash}}/register
X-XSRF-TOKEN: {{ss1_xsrf_token}}
{
  "address": "{{ss1_host}}"
}

# TODO: This should be 200 according to the OpenAPI description but is 204:
# https://github.com/nordic-institute/X-Road/blob/e14d198f704b34b4d1789c2972e816152b6a35f8/src/security-server/openapi-model/src/main/resources/META-INF/openapi-definition.yaml#L640
HTTP 204

# Accept Security Server auth certificate registration
GET https://{{cs_host}}:4000/api/v1/management-requests?sort=id&desc=true&status=WAITING
X-XSRF-TOKEN: {{cs_xsrf_token}}

HTTP 200

[Captures]
ss1_auth_cert_reg_req_id: jsonpath "$.items[0].id"

POST https://{{cs_host}}:4000/api/v1/management-requests/{{ss1_auth_cert_reg_req_id}}/approval
X-XSRF-TOKEN: {{cs_xsrf_token}}

HTTP 200

# Activate auth certificate on SS1 Security Server
PUT https://{{ss1_host}}:4000/api/v1/token-certificates/{{ss1_auth_key_cert_hash}}/activate
X-XSRF-TOKEN: {{ss1_xsrf_token}}

HTTP 204

# Set timestamping service on SS1 Security Server
POST https://{{ss1_host}}:4000/api/v1/system/timestamping-services
X-XSRF-TOKEN: {{ss1_xsrf_token}}
{
  "name": "{{tsa_name}}",
  "url": "{{tsa_url}}"
}

HTTP 201

# Add TestClient to SS1 Security Server
POST https://{{ss1_host}}:4000/api/v1/clients
X-XSRF-TOKEN: {{ss1_xsrf_token}}
{
  "ignore_warnings": true,
  "client": {
    "member_class": "COM",
    "member_code": "4321",
    "subsystem_code": "TestClient",
    "connection_type": "HTTP"
  }
}

HTTP 201

[Captures]
ss1_test_client_id: jsonpath "$.id"

# Register TestClient to SS1 Security Server
PUT https://{{ss1_host}}:4000/api/v1/clients/{{ss1_test_client_id}}/register
X-XSRF-TOKEN: {{ss1_xsrf_token}}

HTTP 204

# Accept Security Server TestClient registration
GET https://{{cs_host}}:4000/api/v1/management-requests?sort=id&desc=true&status=WAITING
X-XSRF-TOKEN: {{cs_xsrf_token}}

HTTP 200

[Captures]
ss1_test_client_req_id: jsonpath "$.items[0].id"

POST https://{{cs_host}}:4000/api/v1/management-requests/{{ss1_test_client_req_id}}/approval
X-XSRF-TOKEN: {{cs_xsrf_token}}

HTTP 200

# Add TestService to Management Security Server
POST https://{{ssm_host}}:4000/api/v1/clients
X-XSRF-TOKEN: {{ssm_xsrf_token}}
{
  "ignore_warnings": true,
  "client": {
    "member_class": "COM",
    "member_code": "1234",
    "subsystem_code": "TestService",
    "connection_type": "HTTP"
  }
}

HTTP 201

[Captures]
ssm_test_service_id: jsonpath "$.id"

# Register TestService to management Security Server
PUT https://{{ssm_host}}:4000/api/v1/clients/{{ssm_test_service_id}}/register
X-XSRF-TOKEN: {{ssm_xsrf_token}}

HTTP 204

# Accept Security Server TestService registration
GET https://{{cs_host}}:4000/api/v1/management-requests?sort=id&desc=true&status=WAITING
X-XSRF-TOKEN: {{cs_xsrf_token}}

HTTP 200

[Captures]
ssm_test_service_req_id: jsonpath "$.items[0].id"

POST https://{{cs_host}}:4000/api/v1/management-requests/{{ssm_test_service_req_id}}/approval
X-XSRF-TOKEN: {{cs_xsrf_token}}

HTTP 200


# Add Central Server management service to SSM Security Server
POST https://{{ssm_host}}:4000/api/v1/clients/{{ssm_test_service_id}}/service-descriptions
X-XSRF-TOKEN: {{ssm_xsrf_token}}
{
  "url": "{{example_service_wsdl}}",
  "type": "WSDL",
  "ignore_warnings": true
}

HTTP 201

[Captures]
ssm_test_service_service_id: jsonpath "$.id"
# TODO: Needs better naming
ssm_test_service_service_service_id: jsonpath "$.services[0].id"

# Add test service URL
PATCH https://{{ssm_host}}:4000/api/v1/services/{{ssm_test_service_service_service_id}}
X-XSRF-TOKEN: {{ssm_xsrf_token}}
{
  "ignore_warnings": true,
  "ssl_auth": false,
  "ssl_auth_all": true,
  "timeout": 60,
  "timeout_all": true,
  "url": "{{example_service_address}}",
  "url_all": true
}

HTTP 200

# Add TestClient access to TestService
POST https://{{ssm_host}}:4000/api/v1/clients/{{ssm_test_service_id}}/service-clients/{{ss1_test_client_id}}/access-rights
X-XSRF-TOKEN: {{ssm_xsrf_token}}
{
  "items": [
    {
      "service_code": "getRandom"
    },
    {
      "service_code": "helloService"
    },
    {
      "service_code": "listPeople"
    },
    {
      "service_code": "personDetails"
    }
  ] 
}

HTTP 201

# Enable management subsystem on management Security Server
PUT https://{{ssm_host}}:4000/api/v1/service-descriptions/{{ssm_test_service_service_id}}/enable
X-XSRF-TOKEN: {{ssm_xsrf_token}}

# TODO: This should be 204 according to the OpenAPI description but is 200:
# https://github.com/nordic-institute/X-Road/blob/e14d198f704b34b4d1789c2972e816152b6a35f8/src/security-server/openapi-model/src/main/resources/META-INF/openapi-definition.yaml#L3908
HTTP 200

# Test that random service works
POST http://{{ss1_host}}:8080
Content-Type: text/xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xro="http://x-road.eu/xsd/xroad.xsd" xmlns:iden="http://x-road.eu/xsd/identifiers" >
    <soapenv:Header>
        <xro:client iden:objectType="SUBSYSTEM">
            <iden:xRoadInstance>DEV</iden:xRoadInstance>
            <iden:memberClass>COM</iden:memberClass>
            <iden:memberCode>4321</iden:memberCode>
            <iden:subsystemCode>TestClient</iden:subsystemCode>
        </xro:client>
        <xro:service iden:objectType="SERVICE">
            <iden:xRoadInstance>DEV</iden:xRoadInstance>
            <iden:memberClass>COM</iden:memberClass>
            <iden:memberCode>1234</iden:memberCode>
            <iden:subsystemCode>TestService</iden:subsystemCode>
            <iden:serviceCode>getRandom</iden:serviceCode>
            <iden:serviceVersion>v1</iden:serviceVersion>
        </xro:service>	  
        <xro:id>ID11234</xro:id>
        <xro:userId>EE1234567890</xro:userId>
        <xro:protocolVersion>4.0</xro:protocolVersion>
    </soapenv:Header>
    <soapenv:Body>
        <prod:getRandom xmlns:prod="http://test.x-road.fi/producer">
            <prod:request/>
        </prod:getRandom>   
    </soapenv:Body>
</soapenv:Envelope>

HTTP 200

[Asserts]
xpath "number(//*[local-name() = 'data'])" isFloat
