@ManagementRequests
Feature: Management requests API: Security Server address change

  Scenario: Address change request is successful
    Given Admin api is mocked with a response with status-code 202, type ADDRESS_CHANGE_REQUEST and id 1133
    When Address change request with new address "server.address" was sent
    Then Response of status code 200 and requestId 1133 is returned
    And Admin api has received following request
    """json
    {
    "type" : "ADDRESS_CHANGE_REQUEST",
    "origin" : "SECURITY_SERVER",
    "security_server_id" : "EE:CLASS:MEMBER:SS1",
    "server_address" : "server.address"
    }
    """
