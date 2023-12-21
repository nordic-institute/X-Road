@Clients
@Modifying
Feature: Clients API

  Background:
    Given Authentication header is set to SECURITY_OFFICER
    And member class 'TEST' is created
    And member class 'TEST2' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new member 'CS:TEST:member' is added with name 'name first'
    And new member 'CS:TEST:another' is added with name 'name second'
    And new member 'CS:TEST2:member' is added with name 'name third'
    And new subsystem "CS:TEST:member:sub1" is added
    And new subsystem "CS:TEST:member:sub2" is added
    And new subsystem "CS:TEST:member:sub3" is added
    And new global group "test-group" with description "test:group:desc" is added
    When members are added to group 'test-group'
      | $identifier         |
      | CS:TEST:member:sub3 |
    And Authentication header is set to MANAGEMENT_SERVICE
    And new security server 'CS:TEST:member:SS-1' authentication certificate registered with origin 'SECURITY_SERVER' and approved
    And new security server 'CS:TEST:member:SS-2' authentication certificate registered with origin 'SECURITY_SERVER' and approved
    And new security server 'CS:TEST2:member:SS-3' authentication certificate registered with origin 'SECURITY_SERVER' and approved
    And client 'CS:TEST:member:sub1' is registered as security server 'CS:TEST:member:SS-1' client from 'SECURITY_SERVER'
    And management request is approved

  Scenario Outline: Clients are listed (positive scenario)
    Given Authentication header is set to SECURITY_OFFICER
    When Clients are queried and validated with following parameters
      | $query   | $name   | $instance   | $memberClass   | $memberCode   | $subsystemCode   | $clientType   | $securityServer   | $globalGroup   | $sortBy   | $desc   | $limit   | $offset   |
      | <$query> | <$name> | <$instance> | <$memberClass> | <$memberCode> | <$subsystemCode> | <$clientType> | <$securityServer> | <$globalGroup> | <$sortBy> | <$desc> | <$limit> | <$offset> |
    Then Clients response is as follows
      | $totalItems   | $items   | $limit   | $offset   | $jsonPath1   |
      | <$totalItems> | <$items> | <$limit> | <$offset> | <$jsonPath1> |
    Examples:
      | $query      | $name | $instance | $memberClass | $memberCode | $subsystemCode | $clientType | $securityServer     | $globalGroup | $sortBy               | $desc | $limit | $offset | $totalItems | $items | $jsonPath1                                  |
      |             |       |           |              |             |                |             |                     |              |                       |       | 25     | 0       | 6           | 6      |                                             |
      | TEST2       |       |           |              |             |                |             |                     |              |                       |       | 25     | 0       | 1           | 1      | clients[0].clientId.memberClass == 'TEST2'  |
      | another     |       |           |              |             |                |             |                     |              |                       |       | 25     | 0       | 1           | 1      | clients[0].clientId.memberCode == 'another' |
      | name second |       |           |              |             |                |             |                     |              |                       |       | 25     | 0       | 1           | 1      | clients[0].memberName == 'name second'      |
      | sub2        |       |           |              |             |                |             |                     |              |                       |       | 25     | 0       | 1           | 1      | clients[0].clientId.subsystemCode == 'sub2' |
      | sub         |       |           |              |             |                |             |                     |              |                       |       | 25     | 0       | 3           | 3      | clients[0].clientId.subsystemCode == 'sub1' |
      |             | third |           |              |             |                |             |                     |              |                       |       | 25     | 0       | 1           | 1      | clients[0].memberName == 'name third'       |
      |             |       | CS        |              |             |                |             |                     |              |                       |       | 3      | 1       | 6           | 3      |                                             |
      |             |       | POTATO    |              |             |                |             |                     |              |                       |       | 25     | 0       | 0           | 0      |                                             |
      |             |       |           | TEST2        |             |                |             |                     |              |                       |       | 25     | 0       | 1           | 1      | clients[0].clientId.memberClass == 'TEST2'  |
      |             |       |           | TEST2        | member      |                |             |                     |              |                       |       | 25     | 0       | 1           | 1      | clients[0].clientId.memberClass == 'TEST2'  |
      |             |       |           | TEST2        | member      | sub2           |             |                     |              |                       |       | 25     | 0       | 0           | 0      |                                             |
      |             |       |           | TEST         | member      | sub2           |             |                     |              |                       |       | 25     | 0       | 1           | 1      | clients[0].clientId.subsystemCode == 'sub2' |
      |             |       |           |              |             |                | MEMBER      |                     |              | client_id.member_code | false | 2      | 0       | 3           | 2      | clients[0].clientId.memberCode == 'another' |
      |             |       |           |              |             |                | MEMBER      |                     |              | client_id.member_code | true  | 2      | 0       | 3           | 2      | clients[0].clientId.memberCode == 'member'  |
      |             |       |           |              |             |                | SUBSYSTEM   |                     |              |                       |       | 25     | 0       | 3           | 3      | clients[0].clientId.subsystemCode == 'sub1' |
      |             |       |           |              |             |                |             | CS:TEST:member:SS-1 |              |                       |       | 25     | 0       | 1           | 1      |                                             |
      |             |       |           |              |             |                |             |                     | test-group   |                       |       | 25     | 0       | 6           | 6      |                                             |
      |             |       |           |              |             | sub3           |             | CS:TEST:member:SS-1 | test-group   |                       |       | 25     | 0       | 0           | 0      |                                             |


  Scenario Outline: Clients are not listed (negative scenario)
    Given Authentication header is set to SECURITY_OFFICER
    When Clients are queried and validated with following parameters
      | $query   | $name   | $instance   | $memberClass   | $memberCode   | $subsystemCode   | $clientType   | $securityServer   | $sortBy   | $desc   | $limit   | $offset   |
      | <$query> | <$name> | <$instance> | <$memberClass> | <$memberCode> | <$subsystemCode> | <$clientType> | <$securityServer> | <$sortBy> | <$desc> | <$limit> | <$offset> |
    Then Response is of status code <$statusCode> and error code "<$errorCode>"
    Examples:
      | $query        | $name         | $instance     | $memberClass  | $memberCode   | $subsystemCode | $clientType | $securityServer | $sortBy | $desc | $limit | $offset | $statusCode | $errorCode                    |
      | $RND-STR-256$ |               |               |               |               |                |             |                 |         |       | 25     | 0       | 400         | validation_failure            |
      |               | $RND-STR-256$ |               |               |               |                |             |                 |         |       | 25     | 0       | 400         | validation_failure            |
      |               |               | $RND-STR-256$ |               |               |                |             |                 |         |       | 25     | 0       | 400         | validation_failure            |
      |               |               |               | $RND-STR-256$ |               |                |             |                 |         |       | 25     | 0       | 400         | validation_failure            |
      |               |               |               |               | $RND-STR-256$ |                |             |                 |         |       | 25     | 0       | 400         | validation_failure            |
      |               |               |               |               |               | $RND-STR-256$  |             |                 |         |       | 25     | 0       | 400         | validation_failure            |
      |               |               | CS            |               |               |                |             | MISSING:SERVER  |         |       | 25     | 0       | 400         | invalid_encoded_id            |
      |               |               | POTATO        |               |               |                |             |                 | NOTHING |       | 25     | 0       | 400         | invalid_sort_properties       |
      |               |               |               | TEST2         |               |                |             |                 |         |       | -1     | 0       | 400         | invalid_pagination_properties |
      |               |               |               | TEST2         | member        |                |             |                 |         |       | 25     | -1      | 400         | invalid_pagination_properties |
