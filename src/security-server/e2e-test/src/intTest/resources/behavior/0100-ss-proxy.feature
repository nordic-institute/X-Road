@Initialization
Feature: 0100 - SS: Proxy

  Background:
    Given Environment is initialized

  Scenario: Soap request is successful over proxy
    When SOAP request is sent to "ss1" "proxy"
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
    Then response is received with http status code 200 and body path "Envelope.Body.getRandomResponse" is not empty

  Scenario: REST request is successfully transferred over X-Road proxy
    When REST request is sent to "ss1" "proxy"
    """json
    {"data": 1.0, "service": "random"}
    """
    Then response is received with http status code 200 and body path "message" is equal to "Hello, world from POST service!"

  Scenario: REST request with valid API path permission is successfully transferred over X-Road proxy
    When REST request targeted at "/api/members" API endpoint is sent to "ss1" "proxy"
    Then response is received with http status code 200 and body path "[0].name" is equal to "MTÜ Nordic Institute for Interoperability Solutions"

  Scenario: Messagelogs are successfully archived and removed from database
    When Waiting for 31 seconds to ensure that all messagelogs are archived and removed from database
    Then "ss0"'s "ui" service has 18 messagelogs present in the archives
    And "ss0" contains 0 messagelog entries
    And "ss1"'s "ui" service has 10 messagelogs present in the archives
    And "ss1" contains 0 messagelog entries
