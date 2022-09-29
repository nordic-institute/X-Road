@SecurityServer
@Login

Feature: It is possible to login to secure site via frontpage

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested

  Scenario: Correct password and username grant access
    When User xrd logs in to SecurityServer with password secret
    Then Clients tab is selected

  Scenario: Invalid password is rejected
    When User xrd logs in to SecurityServer with password INVALID
    Then Error message for incorrect credentials is shown
    And SecurityServer login page is open

  Scenario: Invalid username is rejected
    When User INVALID logs in to SecurityServer with password secret
    Then Error message for incorrect credentials is shown
    And SecurityServer login page is open

