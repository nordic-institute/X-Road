@GlobalGroups
@Modifying
Feature: Global groups API

  #The global group 'security-server-owners' is created by default. So initially there is 1 global group.

  Background:
    Given Authentication header is set to REGISTRATION_OFFICER

  Scenario: Add global group
    Given new global group 'test-group' with description 'group description' is added
    And Response is of status code 201
    When new global group 'test-group' with description 'group description' is added
    Then Response is of status code 409 and error code 'global_group_exists'

  Scenario: Global groups list
    Given new global group 'test-group-1' with description 'group 1 description' is added
    And new global group 'test-group-2' with description 'group 2 description' is added
    And new global group 'test-group-3' with description 'group 3 description' is added
    Then global groups list contains 4 entries

  Scenario: Update global group
    Given new global group 'test-group' with description 'group description' is added
    When global group 'test-group' description is updated to 'new description'
    Then global group 'test-group' description is 'new description'

  Scenario: Delete global group
    Given new global group 'test-group' with description 'group description' is added
    And global groups list contains 2 entries
    When global group 'test-group' is deleted
    Then global groups list contains 1 entries
    And deleting not existing group fails with status code 404 and error code 'global_group_not_found'
    And deleting global group 'security-server-owners' fails with status code 400 and error code 'owners_global_group_cannot_be_deleted'

  Scenario: Global group filter model for empty group
    Given new global group 'test-group' with description 'description' is added
    Then global group 'test-group' has filter model as follows
      | $instances     |  |
      | $memberClasses |  |
      | $codes         |  |
      | $subsystems    |  |

  Scenario: Global group filter model
    Given Authentication header is set to MANAGEMENT_SERVICE
    And member class 'E2E' is created
    And member class 'TEST' is created
    And new member 'CS:TEST:m-1' is added
    And new member 'CS:E2E:m-2' is added
    And new security server 'CS:E2E:m-2:SS-1' authentication certificate registered with origin 'SECURITY_SERVER' and approved
    And new security server 'CS:TEST:m-1:SS-2' authentication certificate registered with origin 'SECURITY_SERVER' and approved
    Then global group 'security-server-owners' has filter model as follows
      | $instances     | CS       |
      | $memberClasses | E2E,TEST |
      | $codes         | m-2,m-1  |
      | $subsystems    |          |

  Scenario: Global group members list
    Given Authentication header is set to MANAGEMENT_SERVICE
    And member class 'E2E' is created
    And member class 'TEST' is created
    And new member 'CS:TEST:m-1' is added
    And new member 'CS:E2E:m-2' is added
    And new member 'CS:TEST:m-3' is added
    And new security server 'CS:E2E:m-2:SS-1' authentication certificate registered with origin 'SECURITY_SERVER' and approved
    And new security server 'CS:E2E:m-2:SS-2' authentication certificate registered with origin 'SECURITY_SERVER' and approved
    And new security server 'CS:TEST:m-1:SS-3' authentication certificate registered with origin 'SECURITY_SERVER' and approved
    And new security server 'CS:TEST:m-3:SS-4' authentication certificate registered with origin 'SECURITY_SERVER' and approved
    And new security server 'CS:TEST:m-1:SS-5' authentication certificate registered with origin 'SECURITY_SERVER' and approved
    And Authentication header is set to REGISTRATION_OFFICER
    Then global group 'security-server-owners' members list is queried and validated using params
      | $q   | $sortBy | $desc | $types           | $instance | $class | $codes      | $subsystems | $pageSize | $page | $itemsInPage | $total | $sortFieldExp |
      |      |         |       |                  |           |        |             |             | 2         | 1     | 2            | 3      |               |
      |      |         |       |                  |           |        |             |             | 2         | 2     | 1            | 3      |               |
      |      |         |       |                  |           |        |             |             |           |       | 3            | 3      |               |
      | E2E  |         |       |                  |           |        |             |             |           |       | 1            | 1      |               |
      | TEST |         |       |                  |           |        |             |             |           |       | 2            | 2      |               |
      |      |         |       | MEMBER           |           |        |             |             |           |       | 3            | 3      |               |
      |      |         |       | SUBSYSTEM        |           |        |             |             |           |       | 0            | 0      |               |
      |      |         |       | MEMBER,SUBSYSTEM |           |        |             |             |           |       | 3            | 3      |               |
      |      |         |       |                  | CS        |        |             |             |           |       | 3            | 3      |               |
      |      |         |       |                  | other     |        |             |             |           |       | 0            | 0      |               |
      |      |         |       |                  |           | TEST   |             |             |           |       | 2            | 2      |               |
      |      |         |       |                  |           | E2E    |             |             |           |       | 1            | 1      |               |
      |      |         |       |                  |           | other  |             |             |           |       | 0            | 0      |               |
      |      |         |       |                  |           |        | m-1         |             |           |       | 1            | 1      |               |
      |      |         |       |                  |           |        | m-2,m-1     |             |           |       | 2            | 2      |               |
      |      |         |       |                  |           |        | other       |             |           |       | 0            | 0      |               |
      |      |         |       |                  |           |        |             | other       |           |       | 0            | 0      |               |
      | m    |         |       | MEMBER,SUBSYSTEM | CS        |        | m-2,m-1,m-3 |             | 2         | 1     | 2            | 3      |               |
    #todo: add more tests after sorting is fixed
