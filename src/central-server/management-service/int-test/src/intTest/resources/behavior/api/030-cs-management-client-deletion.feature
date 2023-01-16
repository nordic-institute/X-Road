@ManagementRequests
Feature: Management request: Client deletion

  Scenario: Client deletion request is successful
    Given Admin api is mocked with a response with status-code 202, type CLIENT_DELETION_REQUEST and id 1122
    When Client Deletion request with ClientId "EE:CLASS:MEMBER" was sent
    Then Response of status code 200 and requestId 1122 is returned
    And Admin api has received following request
    """json
    {
    "type" : "CLIENT_DELETION_REQUEST",
    "origin" : "SECURITY_SERVER",
    "security_server_id" : "EE:CLASS:MEMBER:SS1",
    "clientId" : "EE:CLASS:MEMBER"
    }
    """
