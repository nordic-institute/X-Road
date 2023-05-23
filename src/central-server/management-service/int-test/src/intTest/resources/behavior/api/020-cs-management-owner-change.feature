@ManagementRequests
Feature: Management requests API: Owner change
  As owner change flow is pretty much the same as client registration, we're checking only handful of scenarios

  Scenario: Owner change is successful
    Given Admin api is mocked with a response with status-code 202, type OWNER_CHANGE_REQUEST and id 1122
    When Owner change request with clientId "EE:CLASS:MEMBER" was sent
    Then Response of status code 200 and requestId 1122 is returned
    And Admin api has received following request
    """json
    {
    "type" : "OWNER_CHANGE_REQUEST",
    "origin" : "SECURITY_SERVER",
    "security_server_id" : "EE:CLASS:MEMBER:SS1",
    "client_id" : "EE:CLASS:MEMBER"
    }
    """

  Scenario: Owner change fails with soap fault when request sender is not server owner
    When Owner change request with clientId "EE:CLASS:MEMBER" and serverId "EE:CLASS:MEMBER2:SS1" was sent
    Then Response of status code 500 and soap faultCode "InvalidRequest" and soap faultString "Sender does not match server owner." is returned
    And Admin api has not received any request
