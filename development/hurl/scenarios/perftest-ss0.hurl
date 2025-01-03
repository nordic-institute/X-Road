# Log in to the Security Server
POST https://{{ss0_host}}:4000/login
[FormParams]
username: xrd
password: secret

HTTP 200
[Captures]
ss0_xsrf_token: cookie "XSRF-TOKEN"


#Register payloadgen REST service
POST https://{{ss0_host}}:4000/api/v1/clients/DEV:COM:1234:TestService/service-descriptions
X-XSRF-TOKEN: {{ss0_xsrf_token}}
{
  "url": "{{is_rest_payloadgen_url}}",
  "type": "REST",
  "rest_service_code": "{{is_rest_payloadgen_service_code}}"
}

HTTP 201

[Captures]
ss0_test_rest_service_service_id: jsonpath "$.id"

# Enable REST service
PUT https://{{ss0_host}}:4000/api/v1/service-descriptions/{{ss0_test_rest_service_service_id}}/enable
X-XSRF-TOKEN: {{ss0_xsrf_token}}

# Grant access to rest service
POST https://{{ss0_host}}:4000/api/v1/clients/DEV:COM:1234:TestService/service-clients/DEV:COM:4321:TestClient/access-rights
X-XSRF-TOKEN: {{ss0_xsrf_token}}
{
  "items": [
    {
      "service_code": "{{is_rest_payloadgen_service_code}}"
    }
  ]
}
