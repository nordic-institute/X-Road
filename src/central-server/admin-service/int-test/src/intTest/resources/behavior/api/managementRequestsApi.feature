@ManagementRequests
Feature: Management requests API

  Background:
    Given Authentication header is set to MANAGEMENT_SERVICE
    And member class 'E2E' is created
    And new member 'CS:E2E:member-1' is added

  @Modifying
  Scenario: Add/delete Authentication certificate
    Given new security server 'CS:E2E:member-1:SS-X' authentication certificate registered from 'SECURITY_SERVER'
    And management request is approved
    When user can get security server 'CS:E2E:member-1:SS-X' authentication certificates
    Then authentication certificate of 'CS:E2E:member-1:SS-X' is deleted
    And security server 'CS:E2E:member-1:SS-X' has no authentication certificates

  @Modifying
  Scenario: Auto approve Authentication certificate
    Given Authentication header is set to REGISTRATION_OFFICER
    And new security server 'CS:E2E:member-1:SS-X' authentication certificate registered from 'CENTER'
    And management request is with status 'WAITING'
    When Authentication header is set to MANAGEMENT_SERVICE
    And new security server 'CS:E2E:member-1:SS-X' authentication certificate registered from 'SECURITY_SERVER'
    Then management request is with status 'APPROVED'
    And user can get security server 'CS:E2E:member-1:SS-X' authentication certificates

  @Modifying
  Scenario: Decline authentication certificate registration
    Given new security server 'CS:E2E:member-1:SS-X' authentication certificate registered from 'SECURITY_SERVER'
    And management request is with status 'WAITING'
    Then management request is declined
    And management request is with status 'DECLINED'
    And member 'CS:E2E:member-1' is not in global group 'security-server-owners'

  @Modifying
  Scenario: Add/delete security server client
    Given new security server 'CS:E2E:member-1:SS-X' authentication certificate registered from 'SECURITY_SERVER'
    And management request is approved
    And new member 'CS:E2E:member-2' is added
    When client 'CS:E2E:member-2' is registered as security server 'CS:E2E:member-1:SS-X' client from 'SECURITY_SERVER'
    And management request is approved
    And security server 'CS:E2E:member-1:SS-X' clients contains 'CS:E2E:member-2'
    Then member 'CS:E2E:member-2' is deleted as security server 'CS:E2E:member-1:SS-X' client
    And security server 'CS:E2E:member-1:SS-X' has no clients

  @Modifying
  Scenario: Auto approve security server client
    Given new security server 'CS:E2E:member-1:SS-X' authentication certificate registered from 'SECURITY_SERVER'
    And management request is approved
    And new member 'CS:E2E:member-2' is added
    And Authentication header is set to REGISTRATION_OFFICER
    And client 'CS:E2E:member-2' is registered as security server 'CS:E2E:member-1:SS-X' client from 'CENTER'
    And management request is with status 'WAITING'
    When Authentication header is set to MANAGEMENT_SERVICE
    And client 'CS:E2E:member-2' is registered as security server 'CS:E2E:member-1:SS-X' client from 'SECURITY_SERVER'
    Then management request is with status 'APPROVED'
    And security server 'CS:E2E:member-1:SS-X' clients contains 'CS:E2E:member-2'

  @Modifying
  Scenario: Decline client registration
    Given new security server 'CS:E2E:member-1:SS-X' authentication certificate registered from 'SECURITY_SERVER'
    And management request is approved
    And new member 'CS:E2E:member-2' is added
    When client 'CS:E2E:member-2' is registered as security server 'CS:E2E:member-1:SS-X' client from 'SECURITY_SERVER'
    And management request is with status 'WAITING'
    Then management request is declined
    And management request is with status 'DECLINED'

  @Modifying
  Scenario: Changing security server owner
    And new security server 'CS:E2E:member-1:SS-X' authentication certificate registered from 'SECURITY_SERVER'
    And management request is approved
    And member 'CS:E2E:member-1' is in global group 'security-server-owners'
    And new member 'CS:E2E:member-2' is added
    And member 'CS:E2E:member-2' is not in global group 'security-server-owners'
    When client 'CS:E2E:member-2' is registered as security server 'CS:E2E:member-1:SS-X' client from 'SECURITY_SERVER'
    And management request is approved
    Then owner of security server 'CS:E2E:member-1:SS-X' can be changed to 'CS:E2E:member-2'
    And management request is approved
    And management request is with status 'APPROVED'
    And member 'CS:E2E:member-1' is not in global group 'security-server-owners'
    And member 'CS:E2E:member-2' is in global group 'security-server-owners'

  @Modifying
  Scenario: View management request details
    And new security server 'CS:E2E:member-1:SS-X' authentication certificate registered from 'SECURITY_SERVER'
    And management request is with status 'WAITING'
    Then details of management request can be retrieved for security server 'CS:E2E:member-1:SS-X'
