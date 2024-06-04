@SecurityServer
@Client
Feature: 0590 - SS: Client list

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret
    And Clients tab is selected

  Scenario: Client List search
    When Client filter is set to "TestService"
    Then Client table is ordered as follows:
      | Test member |
      | TestService |

  Scenario: Client List default sorting by name
    Then Client table is ordered as follows:
      | Test member   |
      | random-sub-1  |
      | test-consumer |
      | TestSaved     |
      | TestService   |
      | Test Client   |
      | TestClient    |

  Scenario: Client List sorting by ID desc
    When Client table sorting change to "ID" column desc
    Then Client table is ordered as follows:
      | Test member   |
      | TestService   |
      | TestSaved     |
      | test-consumer |
      | random-sub-1  |
      | Test Client   |
      | TestClient    |

  Scenario: Client List sorting by Status asc
    When Client table sorting change to "Status" column
    Then Client table is ordered as follows:
      | Test member   |
      | TestSaved     |
      | TestService   |
      | random-sub-1  |
      | test-consumer |
      | Test Client   |
      | TestClient    |
