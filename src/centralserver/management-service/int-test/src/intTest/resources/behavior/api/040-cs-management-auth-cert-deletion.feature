@ManagementRequests
Feature: Management request: Auth cert deletion

  Scenario: Auth cert deletion request is successful
    Given Admin api is mocked with a response with status-code 202, type AUTH_CERT_DELETION_REQUEST and id 1122
    When Auth cert Deletion request was sent
    Then Response of status code 200 and requestId 1122 is returned
    And Admin api has received following auth cert deletion request
    """json
    {
    "type" : "AUTH_CERT_DELETION_REQUEST",
    "origin" : "SECURITY_SERVER",
    "security_server_id" : "EE:CLASS:MEMBER:SS1",
    "authenticationCertificate" : []
    }
    """
