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
