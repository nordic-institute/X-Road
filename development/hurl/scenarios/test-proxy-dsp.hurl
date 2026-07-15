# Test DSP proxy flow (UC3: negotiation on control plane + mTLS transfer on dataplane)
# Sends request to consumer (ss1) — the SS that has TestClient subsystem registered (see setup.hurl).
# Provider is ss0 (member 1234, owns TestService). Assets are resolved dynamically from serverconf
# by the X-Road EDC asset extension at request time, so when ss1's proxy resolves the service it
# negotiates DSP with ss0's CP and transfers via dataplane.
POST http://{{ss1_proxy}}:8080/r1/DEV/COM/1234/TestService/mock1
Content-Type: application/json
X-Road-Client: DEV/COM/4321/TestClient

{"data": 1.0, "service": "random"}

HTTP 200

[Asserts]
jsonpath "$.message" == "Hello, world from POST service!"
