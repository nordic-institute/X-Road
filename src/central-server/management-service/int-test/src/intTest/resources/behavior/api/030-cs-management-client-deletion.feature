@ManagementRequests
Feature: Management requests API: Client deletion

  Scenario: Client deletion request is successful
    Given Admin api is mocked with a response with status-code 202, type CLIENT_DELETION_REQUEST and id 1122
    When Client deletion request with clientId "EE:CLASS:MEMBER" was sent
    Then Response of status code 200 and requestId 1122 is returned
    And Admin api has received following request
    """json
    {
    "type" : "CLIENT_DELETION_REQUEST",
    "origin" : "SECURITY_SERVER",
    "security_server_id" : "EE:CLASS:MEMBER:SS1",
    "client_id" : "EE:CLASS:MEMBER"
    }
    """

  Scenario: Client deletion fails with soap fault when request sender is not server owner
    When Client deletion request with clientId "EE:CLASS:MEMBER" and serverId "EE:CLASS:MEMBER2:SS1" was sent
    Then Response of status code 500 and soap faultCode "InvalidRequest" and soap faultString "Sender does not match server owner." is returned
    And Admin api has not received any request
