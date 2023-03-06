@SecurityServer
Feature: Security Server API

  @Modifying
  Scenario: Get list of security servers
    Given Authentication header is set to MANAGEMENT_SERVICE
    And member class 'TEST' is created
    And new member 'CS:TEST:member-1' is added
    And new security server 'CS:TEST:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    And Authentication header is set to SYSTEM_ADMINISTRATOR
    Then security servers list contains 'CS:TEST:member-1:SS-X'
    And security servers list sorting by unknown field fails

  @Modifying
  Scenario Outline: Security servers list sorting
    Given Authentication header is set to MANAGEMENT_SERVICE
    And member class 'TEST' is created
    And member class 'ANOTHER' is created
    And new member 'CS:TEST:member-1' is added
    And new member 'CS:ANOTHER:member-2' is added
    And new security server 'CS:TEST:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    And new security server 'CS:ANOTHER:member-2:SS-A' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    And Authentication header is set to SYSTEM_ADMINISTRATOR
    When user requests security servers list sorted by '<$sortField>' '<$sortDirection>'
    Then the list is sorted by '<$responseFieldExpression>' '<$sortDirection>'
    Examples:
      | $sortField            | $sortDirection | $responseFieldExpression |
      | xroad_id.server_code  | desc           | xroadId.serverCode       |
      | xroad_id.server_code  | asc            | xroadId.serverCode       |
      | xroad_id.member_code  | desc           | xroadId.memberCode       |
      | xroad_id.member_code  | asc            | xroadId.memberCode       |
      | xroad_id.member_class | desc           | xroadId.memberClass      |
      | xroad_id.member_class | asc            | xroadId.memberClass      |
      | owner_name            | desc           | ownerName                |
      | owner_name            | asc            | ownerName                |

  @Modifying
  Scenario: Get security server details
    Given Authentication header is set to MANAGEMENT_SERVICE
    And member class 'TEST' is created
    And new member 'CS:TEST:member-1' is added
    And new security server 'CS:TEST:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    And Authentication header is set to REGISTRATION_OFFICER
    Then user can get security server 'CS:TEST:member-1:SS-X' details
    And getting non existing security server details fails

  @Modifying
  Scenario: Get security server clients
    Given Authentication header is set to MANAGEMENT_SERVICE
    And member class 'TEST' is created
    And new member 'CS:TEST:member-2' is added
    And new security server 'CS:TEST:member-2:SS-2' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    Then security server 'CS:TEST:member-2:SS-2' has no clients
    When new member 'CS:TEST:member-7' is added
    And client 'CS:TEST:member-7' is registered as security server 'CS:TEST:member-2:SS-2' client from 'SECURITY_SERVER'
    And management request is approved
    And Authentication header is set to REGISTRATION_OFFICER
    Then security server 'CS:TEST:member-2:SS-2' clients contains 'CS:TEST:member-7'

  @Modifying
  Scenario: Modify security server address
    Given Authentication header is set to MANAGEMENT_SERVICE
    And member class 'TEST' is created
    And new member 'CS:TEST:member-1' is added
    And new security server 'CS:TEST:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    And Authentication header is set to REGISTRATION_OFFICER
    Then security server 'CS:TEST:member-1:SS-X' address is updated
    And updating the address of a non-existing security server fails

  @Modifying
  Scenario: Get security server authentication certificates
    Given Authentication header is set to MANAGEMENT_SERVICE
    And member class 'TEST' is created
    And new member 'CS:TEST:member-1' is added
    And new security server 'CS:TEST:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    And Authentication header is set to REGISTRATION_OFFICER
    Then user can get security server 'CS:TEST:member-1:SS-X' authentication certificates
