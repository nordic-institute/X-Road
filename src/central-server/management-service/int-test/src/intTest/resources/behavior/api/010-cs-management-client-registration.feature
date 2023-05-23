@ManagementRequests
Feature: Management requests API: Client registration

  Scenario: Client registration is successful
    Given Admin api is mocked with a response with status-code 202, type CLIENT_REGISTRATION_REQUEST and id 1122
    When Client Registration request with clientId "EE:CLASS:MEMBER" was sent
    Then Response of status code 200 and requestId 1122 is returned
    And Admin api has received following request
    """json
    {
    "type" : "CLIENT_REGISTRATION_REQUEST",
    "origin" : "SECURITY_SERVER",
    "security_server_id" : "EE:CLASS:MEMBER:SS1",
    "client_id" : "EE:CLASS:MEMBER"
    }
    """

  Scenario: Client registration fails with soap fault on bad admin-api response
    Given Admin api is mocked with a response with status-code 409, type CLIENT_REGISTRATION_REQUEST and id 1122
    When Client Registration request with clientId "EE:CLASS:MEMBER" was sent
    Then Response of status code 500 and soap fault is returned

  Scenario: Client registration fails with soap fault on invalid signature
    Given Admin api is mocked with a response with status-code 200, type CLIENT_REGISTRATION_REQUEST and id 1122
    When Client Registration request with clientId "EE:CLASS:MEMBER" and invalid signature was sent
    Then Response of status code 500 and soap fault "InvalidSignatureValue" is returned
    And Admin api has not received any request

  Scenario: Client registration fails with soap fault on invalid certificate
    Given Admin api is mocked with a response with status-code 200, type CLIENT_REGISTRATION_REQUEST and id 1122
    When Client Registration request with clientId "EE:CLASS:MEMBER" and invalid client certificate was sent
    Then Response of status code 500 and soap fault "IncorrectCertificate" is returned
    And Admin api has not received any request

  Scenario: Client registration fails with soap fault on revoked ocsp certificate
    Given Admin api is mocked with a response with status-code 200, type CLIENT_REGISTRATION_REQUEST and id 1122
    When Client Registration request with ClientId "EE:CLASS:MEMBER" and revoked OCSP was sent
    Then Response of status code 500 and soap fault "CertValidation" is returned
    And Admin api has not received any request

  Scenario: Client registration fails with soap fault on empty request
    Given Admin api is mocked with a response with status-code 200, type CLIENT_REGISTRATION_REQUEST and id 1122
    When Client Registration request with empty request was sent
    Then Response of status code 500 and soap fault "InvalidRequest" is returned
    And Admin api has not received any request

  Scenario: Client registration fails with invalid clientId
    Given Admin api is mocked with a response with status-code 200, type CLIENT_REGISTRATION_REQUEST and id 1122
    When Client Registration request with clientId "EE:CLASS:___" was sent
    Then Response of status code 500 and soap fault "InvalidRequest" is returned
    And Admin api has not received any request

  Scenario: Client registration fails with soap fault when request sender is not server owner
    When Client Registration request with clientId "EE:CLASS:MEMBER" and serverId "EE:CLASS:MEMBER2:SS1" was sent
    Then Response of status code 500 and soap faultCode "InvalidRequest" and soap faultString "Sender does not match server owner." is returned
    And Admin api has not received any request
