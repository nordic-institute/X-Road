@SecurityServer
@UI
@Client
Feature: 0570 - SS: Client Service clients

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret123!
    And Clients tab is selected

  Scenario: Service client list can be filtered and sorted
    Given Client "Test service" is opened
    And Service clients sub-tab is selected
    And Service clients list is as follows
      | $memberName            | $id                        |
      | Security Server owners | DEV:security-server-owners |
      | Test client            | DEV:COM:4321:TestClient    |
      | Test consumer          | DEV:COM:1234:test-consumer |
    When Service clients list is filtered with "consumer"
    Then Service clients list is as follows
      | $memberName   | $id                        |
      | Test consumer | DEV:COM:1234:test-consumer |
    When Service clients list is filtered with ""
    And Service clients list sorted by col no 2 asc
    Then Service clients list is as follows
      | $memberName            | $id                        |
      | Test consumer          | DEV:COM:1234:test-consumer |
      | Test client            | DEV:COM:4321:TestClient    |
      | Security Server owners | DEV:security-server-owners |
    When Service clients list sorted by col no 1 desc
    Then Service clients list is as follows
      | $memberName            | $id                        |
      | Test consumer          | DEV:COM:1234:test-consumer |
      | Test client            | DEV:COM:4321:TestClient    |
      | Security Server owners | DEV:security-server-owners |

