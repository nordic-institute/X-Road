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

# Second sender member on the same consumer SS (ss1): test-consumer belongs to
# member DEV:COM:1234, not to ss1's owner (4321). Its DSP exchange must negotiate
# as member 1234's derived participant context. Because asset access is cached per
# (consumer context, asset), this is necessarily a separate negotiation from the
# TestClient exchange above. test-consumer is ACLed to the SOAP services on
# TestService (see setup.hurl), so the call succeeds end-to-end.
POST http://{{ss1_proxy}}:8080
Content-Type: text/xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xro="http://x-road.eu/xsd/xroad.xsd" xmlns:iden="http://x-road.eu/xsd/identifiers" >
    <soapenv:Header>
        <xro:client iden:objectType="SUBSYSTEM">
            <iden:xRoadInstance>DEV</iden:xRoadInstance>
            <iden:memberClass>COM</iden:memberClass>
            <iden:memberCode>1234</iden:memberCode>
            <iden:subsystemCode>test-consumer</iden:subsystemCode>
        </xro:client>
        <xro:service iden:objectType="SERVICE">
            <iden:xRoadInstance>DEV</iden:xRoadInstance>
            <iden:memberClass>COM</iden:memberClass>
            <iden:memberCode>1234</iden:memberCode>
            <iden:subsystemCode>TestService</iden:subsystemCode>
            <iden:serviceCode>getRandom</iden:serviceCode>
            <iden:serviceVersion>v1</iden:serviceVersion>
        </xro:service>
        <xro:id>ID-dsp-second-member</xro:id>
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

# Negative: mock1 is ACLed only to DEV:COM:4321:TestClient (see setup.hurl), so a
# sender from member 1234 must be denied. Since this story, the consumer presents
# the real sender member's context, so the denial happens at contract negotiation
# on the provider side; the consumer proxy surfaces it as a sanitized X-Road error.
POST http://{{ss1_proxy}}:8080/r1/DEV/COM/1234/TestService/mock1
Content-Type: application/json
X-Road-Client: DEV/COM/1234/test-consumer

{"data": 1.0, "service": "random"}

HTTP 500

[Asserts]
header "X-Road-Error" exists
