# Bootstrap the initial admin user (no-op once one exists)
POST https://{{ss0_host}}:4000/api/v1/initialization/admin-user
Content-Type: application/json
{
  "username": "{{ui_username}}",
  "password": "{{ui_password}}"
}
HTTP *
[Asserts]
status toString matches "^(201|409)$"

# Log in to the Security Server
POST https://{{ss0_host}}:4000/login
[FormParams]
username: {{ui_username}}
password: {{ui_password}}

HTTP 200
[Captures]
ss0_xsrf_token: cookie "XSRF-TOKEN"


#Register payloadgen REST service
POST https://{{ss0_host}}:4000/api/v1/clients/{{xroad_instance}}:{{member_class}}:{{ss0_member_code}}:TestService/service-descriptions
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
POST https://{{ss0_host}}:4000/api/v1/clients/{{xroad_instance}}:{{member_class}}:{{ss0_member_code}}:TestService/service-clients/{{xroad_instance}}:{{member_class}}:{{ss1_member_code}}:TestClient/access-rights
X-XSRF-TOKEN: {{ss0_xsrf_token}}
{
  "items": [
    {
      "service_code": "{{is_rest_payloadgen_service_code}}"
    }
  ]
}
