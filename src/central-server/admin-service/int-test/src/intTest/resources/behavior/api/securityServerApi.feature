@SecurityServer
Feature: Security Server API

  @Modifying
  Scenario: Get list of security servers
    Given Authentication header is set to SECURITY_OFFICER
    And member class 'TEST' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new member 'CS:TEST:member-1' is added
    And Authentication header is set to MANAGEMENT_SERVICE
    And new security server 'CS:TEST:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    And Authentication header is set to REGISTRATION_OFFICER
    Then security servers list contains 'CS:TEST:member-1:SS-X'
    And security servers list sorting by unknown field fails

  @Modifying
  Scenario Outline: Security servers list sorting
    Given Authentication header is set to SECURITY_OFFICER
    And member class 'TEST' is created
    And member class 'ANOTHER' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new member 'CS:TEST:member-1' is added
    And new member 'CS:ANOTHER:member-2' is added
    And Authentication header is set to MANAGEMENT_SERVICE
    And new security server 'CS:TEST:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    And new security server 'CS:ANOTHER:member-2:SS-A' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    And Authentication header is set to REGISTRATION_OFFICER
    When user requests security servers list sorted by '<$sortField>' '<$sortDirection>'
    Then the list is sorted by '<$responseFieldExpression>' '<$sortDirection>'
    Examples:
      | $sortField             | $sortDirection | $responseFieldExpression |
      | server_id.server_code  | desc           | serverId.serverCode      |
      | server_id.server_code  | asc            | serverId.serverCode      |
      | server_id.member_code  | desc           | serverId.memberCode      |
      | server_id.member_code  | asc            | serverId.memberCode      |
      | server_id.member_class | desc           | serverId.memberClass     |
      | server_id.member_class | asc            | serverId.memberClass     |
      | owner_name             | desc           | ownerName                |
      | owner_name             | asc            | ownerName                |

  @Modifying
  Scenario: Security server list paging and query
    Given Authentication header is set to SECURITY_OFFICER
    And member class 'TEST' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new member 'CS:TEST:member' is added with name 'name first'
    And new member 'CS:TEST:another' is added with name 'name second'
    And Authentication header is set to MANAGEMENT_SERVICE
    And new security server 'CS:TEST:member:SS-1' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    And new security server 'CS:TEST:member:SS-3' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    And new security server 'CS:TEST:another:SS-2' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    Then security servers list, queried with '' paged by 2, page 1 contains 2 entries, 3 in total
    And security servers list, queried with '' paged by 2, page 2 contains 1 entries, 3 in total
    And security servers list, queried with '' paged by 2, page 3 contains 0 entries, 3 in total
    And security servers list, queried with 'TEST' paged by 2, page 1 contains 2 entries, 3 in total
    And security servers list, queried with 'TEST' paged by 2, page 2 contains 1 entries, 3 in total
    And security servers list, queried with 'member' paged by 2, page 1 contains 2 entries, 2 in total
    And security servers list, queried with 'another' paged by 2, page 1 contains 1 entries, 1 in total
    And security servers list, queried with 'SS' paged by 5, page 1 contains 3 entries, 3 in total
    And security servers list, queried with 'SS-1' paged by 2, page 1 contains 1 entries, 1 in total
    And security servers list, queried with 'SS-2' paged by 2, page 1 contains 1 entries, 1 in total
    And security servers list, queried with 'SS-3' paged by 2, page 1 contains 1 entries, 1 in total
    And security servers list, queried with 'first' paged by 2, page 1 contains 2 entries, 2 in total
    And security servers list, queried with 'second' paged by 2, page 1 contains 1 entries, 1 in total
    And security servers list, queried with 'should not find' paged by 25, page 1 contains 0 entries, 0 in total

  @Modifying
  Scenario: Get security server details
    Given Authentication header is set to SECURITY_OFFICER
    And member class 'TEST' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new member 'CS:TEST:member-1' is added
    And Authentication header is set to MANAGEMENT_SERVICE
    And new security server 'CS:TEST:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    And Authentication header is set to REGISTRATION_OFFICER
    Then user can get security server 'CS:TEST:member-1:SS-X' details
    And getting non existing security server details fails

  @Modifying
  Scenario: Get security server clients
    Given Authentication header is set to SECURITY_OFFICER
    And member class 'TEST' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new member 'CS:TEST:member-2' is added
    And Authentication header is set to MANAGEMENT_SERVICE
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
    Given Authentication header is set to SECURITY_OFFICER
    And member class 'TEST' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new member 'CS:TEST:member-1' is added
    And Authentication header is set to MANAGEMENT_SERVICE
    And new security server 'CS:TEST:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    And Authentication header is set to REGISTRATION_OFFICER
    Then security server 'CS:TEST:member-1:SS-X' address is updated
    And updating the address of a non-existing security server fails

  @Modifying
  Scenario: Get security server authentication certificates
    Given Authentication header is set to SECURITY_OFFICER
    And member class 'TEST' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new member 'CS:TEST:member-1' is added
    And Authentication header is set to MANAGEMENT_SERVICE
    And new security server 'CS:TEST:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    And Authentication header is set to REGISTRATION_OFFICER
    Then user can get security server 'CS:TEST:member-1:SS-X' authentication certificates

  @Modifying
  Scenario: Delete security server
    Given Authentication header is set to SECURITY_OFFICER
    And member class 'TEST' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new member 'CS:TEST:member-1' is added
    And Authentication header is set to MANAGEMENT_SERVICE
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
    And management request list contains requests of given type
      | $type                      | $count |
      | AUTH_CERT_DELETION_REQUEST | 1      |
      | CLIENT_DELETION_REQUEST    | 2      |
    And management request of types 'AUTH_CERT_DELETION_REQUEST,CLIENT_DELETION_REQUEST' details has comment 'SERVER:CS/TEST/member-1/SS-X deletion'

  @Modifying
  Scenario: Deleting security server authentication certificate
    Given Authentication header is set to SECURITY_OFFICER
    And member class 'TEST' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new member 'CS:TEST:member-1' is added
    And Authentication header is set to MANAGEMENT_SERVICE
    And new security server 'CS:TEST:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    And Authentication header is set to REGISTRATION_OFFICER
    Then user can delete security server 'CS:TEST:member-1:SS-X' authentication certificate
    And security server 'CS:TEST:member-1:SS-X' has no authentication certificates
