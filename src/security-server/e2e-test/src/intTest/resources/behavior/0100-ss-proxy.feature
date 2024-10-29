@Initialization
Feature: 0100 - SS: Proxy

  Background:
    Given Environment is initialized

  Scenario: Soap request is successful over proxy
    When SOAP request is sent to "ss1" proxy
    """xml
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
    """
    Then response is sent of http status code 200 and body path "Envelope.Body" is not empty


  Scenario: REST request is successful transferred over X-Road proxy
    When REST request is sent to "ss1" proxy
    """json
    {"data": 1.0, "service": "random"}
    """
    Then response is sent of http status code 200 and body path "message" is equal to "Hello, world from POST service!"
