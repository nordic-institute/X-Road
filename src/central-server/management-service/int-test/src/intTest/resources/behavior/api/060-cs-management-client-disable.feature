@ManagementRequests
Feature: Management requests API: Client disabling

  Scenario: Client disabling request is successful
    Given Admin api is mocked with a response with status-code 202, type CLIENT_DISABLE_REQUEST and id 1122
    When Client disable request with clientId "EE:CLASS:MEMBER:SUBSYSTEM" was sent
    Then Response of status code 200 and requestId 1122 is returned
    And Admin api has received following request
    """json
    {
    "type" : "CLIENT_DISABLE_REQUEST",
    "origin" : "SECURITY_SERVER",
    "security_server_id" : "EE:CLASS:MEMBER:SS1",
    "client_id" : "EE:CLASS:MEMBER:SUBSYSTEM"
    }
    """

  Scenario: Client disable fails with soap fault when request sender is not server owner
    When Client disable request with clientId "EE:CLASS:MEMBER:SUBSYSTEM" and serverId "EE:CLASS:MEMBER2:SS1" was sent
    Then Response of status code 500 and soap faultCode "InvalidRequest" and soap faultString "Sender does not match server owner." is returned
    And Admin api has not received any request
