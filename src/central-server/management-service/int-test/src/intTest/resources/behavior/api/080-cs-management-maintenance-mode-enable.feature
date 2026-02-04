@ManagementRequests
Feature: Management requests API: Security server maintenance mode enable

  Scenario: Enable maintenance mode request is successful
    Given Admin api is mocked with a response with status-code 202, type MAINTENANCE_MODE_ENABLE_REQUEST and id 1122
    When Maintenance mode enable request with message "I'll be back" was sent
    Then Response of status code 200 and requestId 1122 is returned
    And Admin api has received following request
    """json
    {
    "type" : "MAINTENANCE_MODE_ENABLE_REQUEST",
    "origin" : "SECURITY_SERVER",
    "security_server_id" : "EE:CLASS:MEMBER:SS1",
    "message" : "I'll be back"
    }
    """

  Scenario: Enable maintenance mode request fails with soap fault when request sender is not server owner
    When Maintenance mode enable request with clientId "EE:CLASS:MEMBER" and serverId "EE:CLASS:MEMBER2:SS1" and message "I'll be back" was sent
    Then Response of status code 500 and soap faultCode "invalid_request" and soap faultString "Sender does not match server owner." is returned
    And Admin api has not received any request

