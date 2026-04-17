# Test DSP proxy flow (UC3: negotiation on control plane + mTLS transfer on dataplane)
# Sends request to consumer (ss0) which negotiates with provider (ss1) via DSP
POST http://{{ss0_proxy}}:5500/r1/DEV/COM/1234/TestService/mock1
Content-Type: application/json
X-Road-Client: DEV/COM/4321/TestClient

{"data": 1.0, "service": "random"}

HTTP 200

[Asserts]
jsonpath "$.message" == "Hello, world from POST service!"
