@SecurityServer
@Client
Feature: 0590 - SS: Client list

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret
    And Clients tab is selected

  Scenario: Client List search
    When Client filter is set to "Test Service"
    Then Client table is ordered as follows:
      | $memberName  |
      | Test member  |
      | Test service |

  Scenario: Client List default sorting by name
    Then Client table is ordered as follows:
      | $memberName   | $id                        |
      | Test member   | DEV:COM:1234               |
      | Test consumer | DEV:COM:1234:test-consumer |
      | Test saved    | DEV:COM:1234:TestSaved     |
      | Test service  | DEV:COM:1234:TestService   |
      | undefined     | DEV:COM:1234:random-sub-1  |
      | Test client   | DEV:COM:4321               |
      | Test client   | DEV:COM:4321:TestClient    |

  Scenario: Client List sorting by ID desc
    When Client table sorting change to "ID" column desc
    Then Client table is ordered as follows:
      | $memberName   |
      | Test member   |
      | Test service  |
      | Test saved    |
      | Test consumer |
      | undefined     |
      | Test Client   |
      | Test client   |

  Scenario: Client List sorting by Status asc
    When Client table sorting change to "Status" column
    Then Client table is ordered as follows:
      | $memberName   |
      | Test member   |
      | Test saved    |
      | Test service  |
      | Test consumer |
      | undefined     |
      | Test Client   |
      | Test client   |
