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
  Scenario Outline: Security server list paging and query
    Given Authentication header is set to MANAGEMENT_SERVICE
    And member class 'TEST' is created
    And new member 'CS:TEST:member' is added with name 'name first'
    And new member 'CS:TEST:another' is added with name 'name second'
    And new security server 'CS:TEST:member:SS-1' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    And new security server 'CS:TEST:member:SS-3' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    And new security server 'CS:TEST:another:SS-2' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    Then security servers list, query '<$query>' paged by <$pageSize>, page <$requestedPage> contains <$itemsInPage> entries, <$itemsTotal> in total
    Examples:
      | $query          | $pageSize | $requestedPage | $itemsInPage | $itemsTotal |
      |                 | 2         | 1              | 2            | 3           |
      |                 | 2         | 2              | 1            | 3           |
      |                 | 2         | 3              | 0            | 3           |
      | TEST            | 2         | 1              | 2            | 3           |
      | TEST            | 2         | 2              | 1            | 3           |
      | member          | 2         | 1              | 2            | 2           |
      | another         | 2         | 1              | 1            | 1           |
      | SS              | 5         | 1              | 3            | 3           |
      | SS-1            | 2         | 1              | 1            | 1           |
      | SS-2            | 2         | 1              | 1            | 1           |
      | SS-3            | 2         | 1              | 1            | 1           |
      | first           | 2         | 1              | 2            | 2           |
      | second          | 2         | 1              | 1            | 1           |
      | should not find | 25        | 1              | 0            | 0           |

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

  @Modifying
  Scenario: Delete security server
    Given Authentication header is set to MANAGEMENT_SERVICE
    And member class 'TEST' is created
    And new member 'CS:TEST:member-1' is added
    And new security server 'CS:TEST:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    And new member 'CS:TEST:member-2' is added
    And client 'CS:TEST:member-2' is registered as security server 'CS:TEST:member-1:SS-X' client from 'SECURITY_SERVER'
    And management request is approved
    And new member 'CS:TEST:member-3' is added
    And client 'CS:TEST:member-3' is registered as security server 'CS:TEST:member-1:SS-X' client from 'SECURITY_SERVER'
    And management request is approved
    Then security servers list contains 'CS:TEST:member-1:SS-X'
    When user deletes security server 'CS:TEST:member-1:SS-X'
    Then security servers list does not contain 'CS:TEST:member-1:SS-X'
