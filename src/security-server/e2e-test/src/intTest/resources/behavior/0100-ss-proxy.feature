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
            <xro:id>ID-SOAP-1</xro:id>
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
    Then response is received with http status code 200 and body matches "(?s).*<.*getRandomResponse.*>.+</.*getRandomResponse.*>.*"
    When SOAP request is sent to "ss1" "proxy"
    """xml
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
            <xro:id>ID-SOAP-2</xro:id>
            <xro:userId>EE1234567891</xro:userId>
            <xro:protocolVersion>4.0</xro:protocolVersion>
        </soapenv:Header>
        <soapenv:Body>
            <prod:getRandom xmlns:prod="http://test.x-road.fi/producer">
                <prod:request/>
            </prod:getRandom>
        </soapenv:Body>
    </soapenv:Envelope>
    """
    Then response is received with http status code 200 and body matches "(?s).*<.*getRandomResponse.*>.+</.*getRandomResponse.*>.*"

  Scenario: REST request is successfully transferred over X-Road proxy
    When REST request is sent to "ss1" "proxy"
    """json
    {"data": 1.0, "service": "random"}
    """
    Then response is received with http status code 200 and body path "message" is equal to "Hello, world from POST service!"

  Scenario: REST request with valid API path permission is successfully transferred over X-Road proxy
    When REST request targeted at "/api/members" API endpoint is sent to "ss1" "proxy"
    Then response is received with http status code 200 and body path "[0].name" is equal to "MTÜ Nordic Institute for Interoperability Solutions"

  Scenario: SS0 Messagelogs are successfully archived and removed from database
    When Waiting for 5 seconds to ensure that all messagelogs are archived and removed from database
    And Global configuration is fetched from "ss0"'s "proxy" for messagelog verification
    And messsagelog archives are downloaded from "ss0" "ui"
    Then "ss0" has 20 messagelogs present in the archives and all are cryptographically valid
    And "ss0" contains 0 messagelog entries

  Scenario: SS1 messagelog is successfully archived and removed from database
    When messsagelog archives are downloaded from "ss1" "ui"
    And "ss1" contains 0 messagelog entries

  Scenario: DEV/COM/4321 messagelogs can be decrypted with key 8A4BB80EEE081BDE
    Then "ss1" messsagelog archives "mlog-DEV_COM_4321" can be decrypted using key "8A4BB80EEE081BDE"
    And "ss1/8A4BB80EEE081BDE" has 10 messagelogs present in the archives and all are cryptographically valid

  Scenario: DEV/COM/4321 messagelogs can be decrypted with key E93952B01C2D2EA5
    Then "ss1" messsagelog archives "mlog-DEV_COM_4321" can be decrypted using key "E93952B01C2D2EA5"
    And "ss1/E93952B01C2D2EA5" has 10 messagelogs present in the archives and all are cryptographically valid

  Scenario: DEV/COM/1234 messagelogs can be decrypted with key 3BD9C292C63580F8
    When "ss1" messsagelog archives "mlog-DEV_COM_1234_test-consumer" can be decrypted using key "3BD9C292C63580F8"
    And "ss1/3BD9C292C63580F8" has 2 messagelogs present in the archives and all are cryptographically valid

  Scenario: messagelogs decryption with other keys fails
    And "ss1" messsagelog archives "mlog-DEV_COM_4321" can not be decrypted using key "3BD9C292C63580F8"
    And "ss1" messsagelog archives "mlog-DEV_COM_1234_test-consumer" can not be decrypted using key "E93952B01C2D2EA5"
    And "ss1" messsagelog archives "mlog-DEV_COM_1234_test-consumer" can not be decrypted using key "8A4BB80EEE081BDE"
