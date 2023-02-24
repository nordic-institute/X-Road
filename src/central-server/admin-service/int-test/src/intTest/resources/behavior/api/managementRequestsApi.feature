@ManagementRequests
Feature: Management requests API

  Background:
    Given Authentication header is set to MANAGEMENT_SERVICE

  @Modifying
  Scenario: Changing security server owner
    Given member class 'E2E' is created
    And new member 'CS:E2E:member-1' is added
    And new security server 'CS:E2E:member-1:SS-X' authentication certificate registered
    And management request is approved
    And new member 'CS:E2E:member-2' is added
    And member 'CS:E2E:member-2' is not in global group 'security-server-owners'
    When client 'CS:E2E:member-2' is registered as security server 'CS:E2E:member-1:SS-X' client
    And management request is approved
    Then owner of security server 'CS:E2E:member-1:SS-X' can be changed to 'CS:E2E:member-2'
    And management request is approved
    And member 'CS:E2E:member-2' is in global group 'security-server-owners'

  @Modifying
  Scenario: View management request details
    Given member class 'E2E' is created
    And new member 'CS:E2E:member-1' is added
    And new security server 'CS:E2E:member-1:SS-X' authentication certificate registered
    And management request is with status 'WAITING'
    Then details of management request can be retrieved for security server 'CS:E2E:member-1:SS-X'
