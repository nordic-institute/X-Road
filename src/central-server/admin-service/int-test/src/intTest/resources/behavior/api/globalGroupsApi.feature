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
    Given Authentication header is set to SECURITY_OFFICER
    And member class 'E2E' is created
    And member class 'TEST' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new member 'CS:TEST:m-1' is added
    And new member 'CS:E2E:m-2' is added
    And Authentication header is set to MANAGEMENT_SERVICE
    And new security server 'CS:E2E:m-2:SS-1' authentication certificate registered with origin 'SECURITY_SERVER' and approved
    And new security server 'CS:TEST:m-1:SS-2' authentication certificate registered with origin 'SECURITY_SERVER' and approved
    Then global group 'security-server-owners' has filter model as follows
      | $instances     | CS       |
      | $memberClasses | E2E,TEST |
      | $codes         | m-2,m-1  |
      | $subsystems    |          |

  Scenario: Global group members list
    Given Authentication header is set to SECURITY_OFFICER
    And member class 'E2E' is created
    And member class 'TEST' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new member 'CS:TEST:m-1' is added
    And new member 'CS:E2E:m-2' is added
    And new member 'CS:TEST:m-3' is added
    And Authentication header is set to MANAGEMENT_SERVICE
    And new security server 'CS:E2E:m-2:SS-1' authentication certificate registered with origin 'SECURITY_SERVER' and approved
    And new security server 'CS:E2E:m-2:SS-2' authentication certificate registered with origin 'SECURITY_SERVER' and approved
    And new security server 'CS:TEST:m-1:SS-3' authentication certificate registered with origin 'SECURITY_SERVER' and approved
    And new security server 'CS:TEST:m-3:SS-4' authentication certificate registered with origin 'SECURITY_SERVER' and approved
    And new security server 'CS:TEST:m-1:SS-5' authentication certificate registered with origin 'SECURITY_SERVER' and approved
    And Authentication header is set to REGISTRATION_OFFICER
    Then global group 'security-server-owners' members list is queried and validated using params
      | $q   | $sortBy    | $desc | $types           | $instance | $class | $codes      | $subsystems | $pageSize | $page | $itemsInPage | $total | $sortFieldExp |
      |      |            |       |                  |           |        |             |             | 2         | 1     | 2            | 3      |               |
      |      |            |       |                  |           |        |             |             | 2         | 2     | 1            | 3      |               |
      |      |            |       |                  |           |        |             |             |           |       | 3            | 3      |               |
      | E2E  | name       | true  |                  |           |        |             |             |           |       | 1            | 1      |               |
      | TEST |            |       |                  |           |        |             |             |           |       | 2            | 2      |               |
      |      | created_at | false | MEMBER           |           |        |             |             |           |       | 3            | 3      |               |
      |      | type       | true  | SUBSYSTEM        |           |        |             |             |           |       | 0            | 0      |               |
      |      | code       | false | MEMBER,SUBSYSTEM |           |        |             |             |           |       | 3            | 3      |               |
      |      |            |       |                  | CS        |        |             |             |           |       | 3            | 3      |               |
      |      |            |       |                  | other     |        |             |             |           |       | 0            | 0      |               |
      |      | class      | true  |                  |           | TEST   |             |             |           |       | 2            | 2      |               |
      |      |            |       |                  |           | E2E    |             |             |           |       | 1            | 1      |               |
      |      |            |       |                  |           | other  |             |             |           |       | 0            | 0      |               |
      |      |            |       |                  |           |        | m-1         |             |           |       | 1            | 1      |               |
      |      | instance   | false |                  |           |        | m-2,m-1     |             |           |       | 2            | 2      |               |
      |      |            |       |                  |           |        | other       |             |           |       | 0            | 0      |               |
      |      | subsystem  |       |                  |           |        |             | other       |           |       | 0            | 0      |               |
      | memb |            |       | MEMBER,SUBSYSTEM | CS        |        | m-2,m-1,m-3 |             | 2         | 1     | 2            | 3      |               |

  Scenario: Add member to global group
    Given Authentication header is set to SECURITY_OFFICER
    And member class 'TEST' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new global group 'test-group' with description 'group description' is added
    And new member 'CS:TEST:member' is added
    When members are added to group 'test-group'
      | $identifier    | $isNew |
      | CS:TEST:member | true   |
    Then Response is of status code 201
    And global group 'test-group' has 1 member
    And global group 'test-group' members list is queried and validated using params
      | $q | $sortBy | $desc | $types | $instance | $class | $codes | $subsystems | $pageSize | $page | $itemsInPage | $total | $sortFieldExp |
      |    |         |       |        |           |        |        |             | 5         | 1     | 1            | 1      |               |

  Scenario: Add members to global group
    Given Authentication header is set to SECURITY_OFFICER
    And member class 'TEST' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new global group 'test-group' with description 'group description' is added
    And new member 'CS:TEST:member1' is added
    And new subsystem 'CS:TEST:member1:subsystem' is added
    And new member 'CS:TEST:member2' is added
    And new subsystem 'CS:TEST:member2:subsystem2' is added
    And new subsystem 'CS:TEST:member2:subsystem3' is added
    When members are added to group 'test-group'
      | $identifier                | $isNew |
      | CS:TEST:member1            | true   |
      | CS:TEST:member1:subsystem  | true   |
      | CS:TEST:member2            | true   |
      | CS:TEST:member2:subsystem2 | true   |
      | CS:TEST:member2:subsystem3 | true   |
    Then Response is of status code 201
    And global group 'test-group' has 5 members
    And global group 'test-group' members list is queried and validated using params
      | $q      | $sortBy | $desc | $types    | $instance | $class | $codes | $subsystems | $pageSize | $page | $itemsInPage | $total | $sortFieldExp |
      |         |         |       |           |           |        |        |             | 5         | 1     | 5            | 5      |               |
      |         |         |       | MEMBER    |           |        |        |             |           |       | 2            | 2      |               |
      |         |         |       | SUBSYSTEM |           |        |        |             |           |       | 3            | 3      |               |
      | member1 |         |       |           |           |        |        |             |           |       | 2            | 2      |               |
      | member2 |         |       |           |           |        |        |             |           |       | 3            | 3      |               |

  Scenario: Add same members twice to global group
    Given Authentication header is set to SECURITY_OFFICER
    And member class 'TEST' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new global group 'test-group' with description 'group description' is added
    And new member 'CS:TEST:member1' is added
    And new subsystem 'CS:TEST:member1:subsystem1' is added
    And new subsystem 'CS:TEST:member1:subsystem2' is added
    When members are added to group 'test-group'
      | $identifier                | $isNew |
      | CS:TEST:member1            | true   |
      | CS:TEST:member1:subsystem1 | true   |
    Then Response is of status code 201
    And global group 'test-group' has 2 members
    And global group 'test-group' members list is queried and validated using params
      | $q | $sortBy | $desc | $types    | $instance | $class | $codes | $subsystems | $pageSize | $page | $itemsInPage | $total | $sortFieldExp |
      |    |         |       |           |           |        |        |             | 5         | 1     | 2            | 2      |               |
      |    |         |       | MEMBER    |           |        |        |             |           |       | 1            | 1      |               |
      |    |         |       | SUBSYSTEM |           |        |        |             |           |       | 1            | 1      |               |
    When members are added to group 'test-group'
      | $identifier                | $isNew |
      | CS:TEST:member1:subsystem1 | false  |
      | CS:TEST:member1:subsystem2 | true   |
    Then Response is of status code 201
    And global group 'test-group' has 3 members
    And global group 'test-group' members list is queried and validated using params
      | $q | $sortBy | $desc | $types    | $instance | $class | $codes | $subsystems | $pageSize | $page | $itemsInPage | $total | $sortFieldExp |
      |    |         |       |           |           |        |        |             | 5         | 1     | 3            | 3      |               |
      |    |         |       | MEMBER    |           |        |        |             |           |       | 1            | 1      |               |
      |    |         |       | SUBSYSTEM |           |        |        |             |           |       | 2            | 2      |               |

  Scenario: Adding member to owner group is prohibited
    When members are added to group 'security-server-owners'
      | $identifier    | $isNew |
      | CS:TEST:member | true   |
    Then Response is of status code 400 and error code 'cannot_add_member_to_owners_group'

  Scenario: Adding non existing member to global group should fail
    Given Authentication header is set to SECURITY_OFFICER
    And member class 'TEST' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new global group 'test-group' with description 'group description' is added
    And new member 'CS:TEST:member' is added
    And new subsystem 'CS:TEST:member:subsystem' is added
    When members are added to group 'test-group'
      | $identifier              | $isNew |
      | CS:TEST:member           | true   |
      | CS:TEST:member:subsystem | true   |
    Then Response is of status code 201
    And global group 'test-group' has 2 members
    When members are added to group 'test-group'
      | $identifier       | $isNew |
      | CS:TEST:not-found | true   |
    Then Response is of status code 404 and error code "member_not_found"
    When members are added to group 'test-group'
      | $identifier              | $isNew |
      | CS:TEST:member:not-found | true   |
    Then Response is of status code 404 and error code "subsystem_not_found"

  Scenario: Delete global group member fails on protected group
    Given Authentication header is set to SECURITY_OFFICER
    And member class 'E2E' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new member 'CS:E2E:m-2' is added
    And Authentication header is set to MANAGEMENT_SERVICE
    And new security server 'CS:E2E:m-2:SS-1' authentication certificate registered with origin 'SECURITY_SERVER' and approved
    When global group "security-server-owners" member 'CS:E2E:m-2' is deleted
    Then Response is of status code 400 and error code 'owners_global_group_member_cannot_be_deleted'

  Scenario: Add and delete members to global group
    Given Authentication header is set to SECURITY_OFFICER
    And member class 'E2E' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new global group 'test-group' with description 'group description' is added
    And new member 'CS:E2E:m-1' is added
    And new member 'CS:E2E:m-2' is added
    And new member 'CS:E2E:m-3' is added
    And members are added to group 'test-group'
      | $identifier | $isNew |
      | CS:E2E:m-1  | true   |
      | CS:E2E:m-2  | true   |
      | CS:E2E:m-3  | true   |
    And new global group 'test-group' with description 'group description' is added
    And global group 'test-group' has 3 members
    When global group "test-group" member "CS:E2E:m-2" is deleted
    Then global group "test-group" members list with page size 10 is queried
    And global group "test-group" members list does not contain "CS:E2E:m-2" member

  Scenario: Global Group behavior when deleting member/subsystems
    Given Authentication header is set to SECURITY_OFFICER
    And member class 'E2E' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new global group 'test-group' with description 'group description' is added
    And new member 'CS:E2E:m-1' is added
    And new subsystem 'CS:E2E:m-1:Subsystem-0' is added
    And new subsystem 'CS:E2E:m-1:Subsystem-1' is added
    And new subsystem 'CS:E2E:m-1:Subsystem-2' is added
    And members are added to group 'test-group'
      | $identifier | $isNew |
      | CS:E2E:m-1  | true   |
      | CS:E2E:m-1:Subsystem-0  | true   |
      | CS:E2E:m-1:Subsystem-1  | true   |
      | CS:E2E:m-1:Subsystem-2  | true   |
    And new global group 'test-group' with description 'group description' is added
    And global group 'test-group' has 4 members
    When subsystem 'CS:E2E:m-1:Subsystem-0' is deleted
    Then global group "test-group" members list with page size 10 is queried
    And global group "test-group" members list does not contain "CS:E2E:m-1:Subsystem-0" member
    And global group 'test-group' has 3 members
    When user deletes member 'CS:E2E:m-1'
    Then global group "test-group" members list with page size 10 is queried
    And global group "test-group" members list does not contain "CS:E2E:m-1" member
    And global group "test-group" members list does not contain "CS:E2E:m-1:Subsystem-1" member
    And global group "test-group" members list does not contain "CS:E2E:m-1:Subsystem-2" member
    And global group 'test-group' has 0 members

  Scenario: Add and delete members to global group fails due to wrong member
    Given Authentication header is set to SECURITY_OFFICER
    And member class 'E2E' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new global group 'test-group' with description 'group description' is added
    And new member 'CS:E2E:m-1' is added
    And new member 'CS:E2E:m-2' is added
    And new member 'CS:E2E:m-3' is added
    And members are added to group 'test-group'
      | $identifier | $isNew |
      | CS:E2E:m-1  | true   |
    And new global group 'test-group' with description 'group description' is added
    When global group "test-group" member "CS:E2E:m-missing" is deleted
    Then Response is of status code 404 and error code "member_not_found"

  Scenario: Delete global group member is forbidden for non privileged user
    Given Authentication header is set to SECURITY_OFFICER
    When global group "security-server-owners" member 'CS:E2E:m-2' is deleted
    Then Response is of status code 403
