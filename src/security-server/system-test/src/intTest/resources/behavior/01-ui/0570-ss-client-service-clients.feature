@SecurityServer
@Client
Feature: 0570 - SS: Client Service clients

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret
    And Clients tab is selected

  Scenario: Multiple Service clients are added
    Given Client "TestService" is opened
    And Service clients sub-tab is selected
    And Service clients add subject wizard is opened
    When Service clients wizard is filtered to "Test member" with 4 results and subject "DEV:COM:1234:test-consumer" is selected
    And Service clients wizard services step is filtered to "c2" with 2 results and service "s4c2" is selected
    Then Service clients list is as follows
      | $memberName | $id                            |
      | Test member | DEV:COM:1234:test-consumer     |
    When Service clients add subject wizard is opened
    And Service clients wizard is filtered to "Test" with 5 results and subject "DEV:COM:4321:TestClient" is selected
    And Service clients subject "DEV:COM:1234:test-consumer" is not selectable
    And Service clients wizard services step is filtered to "" with 5 results and service "s3c2" is selected

    And Service clients add subject wizard is opened
    And Service clients wizard is filtered to "own" with 1 results and subject "DEV:security-server-owners" is selected
    And Service clients wizard services step is filtered to "" with 5 results and service "testOp1" is selected
    Then Service clients list is as follows
      | $memberName            | $id                            |
      | Security Server owners | DEV:security-server-owners     |
      | Test client            | DEV:COM:4321:TestClient        |
      | Test member            | DEV:COM:1234:test-consumer     |

  Scenario: Service client list can be filtered and sorted
    Given Client "TestService" is opened
    And Service clients sub-tab is selected
    And Service clients list is as follows
      | $memberName            | $id                            |
      | Security Server owners | DEV:security-server-owners     |
      | Test client            | DEV:COM:4321:TestClient        |
      | Test member            | DEV:COM:1234:test-consumer     |
    When Service clients list is filtered with "mem"
    Then Service clients list is as follows
      | $memberName            | $id                            |
      | Test member            | DEV:COM:1234:test-consumer     |
    When Service clients list is filtered with ""
    And Service clients list sorted by col no 2 asc
    Then Service clients list is as follows
      | $memberName            | $id                            |
      | Test member            | DEV:COM:1234:test-consumer     |
      | Test client            | DEV:COM:4321:TestClient        |
      | Security Server owners | DEV:security-server-owners     |
    When Service clients list sorted by col no 1 desc
    Then Service clients list is as follows
      | $memberName            | $id                            |
      | Test member            | DEV:COM:1234:test-consumer     |
      | Test client            | DEV:COM:4321:TestClient        |
      | Security Server owners | DEV:security-server-owners     |

  Scenario: Service client can be edited with additional access rights
    Given Client "TestService" is opened
    And Service clients sub-tab is selected
    When Service client "DEV:COM:1234:test-consumer" is opened
    Then Service client view shows id "DEV:COM:1234:test-consumer" and member name "Test member"
    And Service client view access right list is as follows
      | $serviceCode |
      | s4c2         |
    When  Service client view has following services added
      | $serviceCode |
      | testOp1      |
      | testOpA      |
    Then Service client view access right list is as follows
      | $serviceCode |
      | s4c2         |
      | testOp1      |
      | testOpA      |

  Scenario: Service client can be can have all of its service codes removed and re-added
    Given Client "TestService" is opened
    And Service clients sub-tab is selected
    When Service client "DEV:COM:1234:test-consumer" is opened
    Then Service client view shows id "DEV:COM:1234:test-consumer" and member name "Test member"
    And Service client view access right list is as follows
      | $serviceCode |
      | s4c2         |
      | testOp1      |
      | testOpA      |
    When Service clients access rights are removed in full
    Then Service client view Access rights table is empty
    When  Service client view has following services added
      | $serviceCode |
      | testOp1      |
      | testOpA      |
    Then Service client view access right list is as follows
      | $serviceCode |
      | testOp1      |
      | testOpA      |

  Scenario: Service client can be can have single service code removed
    Given Client "TestService" is opened
    And Service clients sub-tab is selected
    When Service client "DEV:COM:1234:test-consumer" is opened
    Then Service client view shows id "DEV:COM:1234:test-consumer" and member name "Test member"
    And Service client view access right list is as follows
      | $serviceCode |
      | testOp1      |
      | testOpA      |
    When Service client view access right for service code "testOp1" is removed
    Then Service client view access right list is as follows
      | $serviceCode |
      | testOpA      |

  Scenario: Service client can have all of its service codes removed and member is not present anymore
    Given Client "TestService" is opened
    And Service clients sub-tab is selected
    When Service client "DEV:COM:4321:TestClient" is opened
    When Service clients access rights are removed in full
    Then Service client view Access rights table is empty
    And Service clients list entry with id "DEV:COM:4321:TestClient" is missing

  Scenario: Service client is removed if its service is deleted
    Given Client "TestService" is opened
    And Service clients sub-tab is selected
    And Service clients list entry with id "DEV:COM:1234:test-consumer" is present

    When Services sub-tab is selected
    And Service "WSDL (http://mock-server:1080/test-services/testservice1.wsdl)" is deleted
    And Service "WSDL (http://mock-server:1080/test-services/testservice1.wsdl)" is missing in the list
    Then Service clients sub-tab is selected
    And Service clients list entry with id "DEV:COM:1234:test-consumer" is missing
