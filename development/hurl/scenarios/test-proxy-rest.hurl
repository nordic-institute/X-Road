# Test that random service works
POST http://{{ss1_host}}:8080/r1/{{xroad_instance}}/{{member_class}}/{{ss0_member_code}}/TestService/mock1
Content-Type: application/json
X-Road-Client: {{xroad_instance}}/{{member_class}}/{{ss1_member_code}}/TestClient

{"data": 1.0, "service": "random"}

HTTP 200

[Asserts]
jsonpath "$.message" == "Hello, world from POST service!"