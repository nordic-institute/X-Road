Feature: 0110 - SS: Proxy - SOAP with attachments

  Background:
    Given Environment is initialized

  Scenario: Soap request with attachments is successful over proxy
    When multipart MIME message with SOAP request and attachments is sent to "ss1" proxy
    """xml
    <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:prod="http://test.x-road.global/producer" xmlns:xro="http://x-road.eu/xsd/xroad.xsd" xmlns:iden="http://x-road.eu/xsd/identifiers">
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
            <iden:serviceCode>storeAttachments</iden:serviceCode>
            <iden:serviceVersion>v1</iden:serviceVersion>
        </xro:service>
        <xro:id>S-13</xro:id>
        <xro:userId>EE1234567890</xro:userId>
        <xro:protocolVersion>4.0</xro:protocolVersion>
    </soapenv:Header>
   <soapenv:Body>
      <prod:storeAttachments/>
   </soapenv:Body>
</soapenv:Envelope>
    """
    Then SOAP response contains the following attachments and sizes
    | att-1 | 35 |
    | att-2 | 47 |

  @DataSpace
  Scenario: Soap request with attachments is successful over proxy using DataSpace transport
    When multipart MIME message with SOAP request and attachments is sent to "ss1" proxy using DataSpace transport
    """xml
    <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:prod="http://test.x-road.global/producer" xmlns:xro="http://x-road.eu/xsd/xroad.xsd" xmlns:iden="http://x-road.eu/xsd/identifiers">
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
            <iden:serviceCode>storeAttachments</iden:serviceCode>
            <iden:serviceVersion>v1</iden:serviceVersion>
        </xro:service>
        <xro:id>S-14</xro:id>
        <xro:userId>EE1234567890</xro:userId>
        <xro:protocolVersion>4.0</xro:protocolVersion>
    </soapenv:Header>
   <soapenv:Body>
      <prod:storeAttachments/>
   </soapenv:Body>
</soapenv:Envelope>
    """
    Then SOAP response contains the following attachments and sizes
      | att-1 | 35 |
      | att-2 | 47 |

  Scenario: Soap response with attachments is successful over proxy
    When SOAP request is sent to "ss1" proxy
"""xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xro="http://x-road.eu/xsd/xroad.xsd" xmlns:iden="http://x-road.eu/xsd/identifiers">
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
            <iden:serviceCode>getAttachments</iden:serviceCode>
            <iden:serviceVersion>v1</iden:serviceVersion>
        </xro:service>
        <xro:id>S-15</xro:id>
        <xro:userId>EE1234567890</xro:userId>
        <xro:protocolVersion>4.0</xro:protocolVersion>
    </soapenv:Header>
   <soapenv:Body>
      <prod:getAttachments xmlns:prod="http://test.x-road.global/producer">
         <!--1 or more repetitions:-->
         <prod:size>10</prod:size>
         <prod:size>25</prod:size>
         <prod:size>333</prod:size>
      </prod:getAttachments>
   </soapenv:Body>
</soapenv:Envelope>
"""
    Then response is multipart MIME message
    And response is parsed as SOAP message
    And multipart MIME SOAP response contains attachments with sizes
      | attachment_0  | 10    |
      | attachment_1  | 25    |
      | attachment_2  | 333   |

  @DataSpace
  Scenario: Soap response with attachments is successful over proxy using DataSpace transport
    When SOAP request is sent to "ss1" proxy using "DataSpace" transport
"""xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xro="http://x-road.eu/xsd/xroad.xsd" xmlns:iden="http://x-road.eu/xsd/identifiers">
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
            <iden:serviceCode>getAttachments</iden:serviceCode>
            <iden:serviceVersion>v1</iden:serviceVersion>
        </xro:service>
        <xro:id>S-16</xro:id>
        <xro:userId>EE1234567890</xro:userId>
        <xro:protocolVersion>4.0</xro:protocolVersion>
    </soapenv:Header>
   <soapenv:Body>
      <prod:getAttachments xmlns:prod="http://test.x-road.global/producer">
         <!--1 or more repetitions:-->
         <prod:size>13</prod:size>
         <prod:size>1</prod:size>
         <prod:size>526</prod:size>
      </prod:getAttachments>
   </soapenv:Body>
</soapenv:Envelope>
"""
    Then response is multipart MIME message
    And response is parsed as SOAP message
    And multipart MIME SOAP response contains attachments with sizes
      | attachment_0  | 13    |
      | attachment_1  | 1   |
      | attachment_2  | 526   |
