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
    When Service clients wizard is filtered to "TestGov" with 3 results and subject "CS:GOV:0245437-2:test-consumer" is selected
    And Service clients wizard services step is filtered to "c2" with 2 results and service "s4c2" is selected
    Then Service clients list is as follows
      | $memberName | $id                            |
      | TestGov     | CS:GOV:0245437-2:test-consumer |
    When Service clients add subject wizard is opened
    And Service clients wizard is filtered to "Test" with 5 results and subject "CS:ORG:2908758-4:Management" is selected
    And Service clients subject "CS:GOV:0245437-2:test-consumer" is not selectable
    And Service clients wizard services step is filtered to "" with 5 results and service "s3c2" is selected

    And Service clients add subject wizard is opened
    And Service clients wizard is filtered to "own" with 1 results and subject "CS:security-server-owners" is selected
    And Service clients wizard services step is filtered to "" with 5 results and service "testOp1" is selected
    Then Service clients list is as follows
      | $memberName            | $id                            |
      | Security Server owners | CS:security-server-owners      |
      | TestGov                | CS:GOV:0245437-2:test-consumer |
      | TestOrg                | CS:ORG:2908758-4:Management    |

  Scenario: Service client list can be filtered and sorted
    Given Client "TestService" is opened
    And Service clients sub-tab is selected
    And Service clients list is as follows
      | $memberName            | $id                            |
      | Security Server owners | CS:security-server-owners      |
      | TestGov                | CS:GOV:0245437-2:test-consumer |
      | TestOrg                | CS:ORG:2908758-4:Management    |
    When Service clients list is filtered with "gov"
    Then Service clients list is as follows
      | $memberName            | $id                            |
      | TestGov                | CS:GOV:0245437-2:test-consumer |
    When Service clients list is filtered with ""
    And Service clients list sorted by col no 2 asc
    Then Service clients list is as follows
      | $memberName            | $id                            |
      | TestGov                | CS:GOV:0245437-2:test-consumer |
      | TestOrg                | CS:ORG:2908758-4:Management    |
      | Security Server owners | CS:security-server-owners      |
    When Service clients list sorted by col no 1 desc
    Then Service clients list is as follows
      | $memberName            | $id                            |
      | TestOrg                | CS:ORG:2908758-4:Management    |
      | TestGov                | CS:GOV:0245437-2:test-consumer |
      | Security Server owners | CS:security-server-owners      |

  Scenario: Service client can be edited with additional access rights
    Given Client "TestService" is opened
    And Service clients sub-tab is selected
    When Service client "CS:GOV:0245437-2:test-consumer" is opened
    Then Service client view shows id "CS:GOV:0245437-2:test-consumer" and member name "TestGov"
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
    When Service client "CS:GOV:0245437-2:test-consumer" is opened
    Then Service client view shows id "CS:GOV:0245437-2:test-consumer" and member name "TestGov"
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
    When Service client "CS:GOV:0245437-2:test-consumer" is opened
    Then Service client view shows id "CS:GOV:0245437-2:test-consumer" and member name "TestGov"
    And Service client view access right list is as follows
      | $serviceCode |
      | testOp1      |
      | testOpA      |
    When Service client view access right for service code "testOp1" is removed
    Then Service client view access right list is as follows
      | $serviceCode |
      | testOpA      |

  Scenario: Service client can be can have all of its service codes removed and member is not present anymore
    Given Client "TestService" is opened
    And Service clients sub-tab is selected
    When Service client "CS:ORG:2908758-4:Management" is opened
    When Service clients access rights are removed in full
    Then Service client view Access rights table is empty
    And Service clients list entry with id "CS:ORG:2908758-4:Management" is missing

  Scenario: Service client is removed if its service is deleted
    Given Client "TestService" is opened
    And Service clients sub-tab is selected
    And Service clients list entry with id "CS:GOV:0245437-2:test-consumer" is present

    When Services sub-tab is selected
    And Service "WSDL (http://mock-server:1080/test-services/testservice1.wsdl)" is deleted
    And Service "WSDL (http://mock-server:1080/test-services/testservice1.wsdl)" is missing in the list
    Then Service clients sub-tab is selected
    And Service clients list entry with id "CS:GOV:0245437-2:test-consumer" is missing
