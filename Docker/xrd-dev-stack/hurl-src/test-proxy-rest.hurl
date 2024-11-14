# Test that random service works
POST http://{{ss1_host}}:8080/r1/DEV/COM/1234/TestService/mock1
Content-Type: application/json
X-Road-Client: DEV/COM/4321/TestClient

{"data": 1.0, "service": "random"}

HTTP 200

[Asserts]
jsonpath "$.message" == "Hello, world from POST service!"