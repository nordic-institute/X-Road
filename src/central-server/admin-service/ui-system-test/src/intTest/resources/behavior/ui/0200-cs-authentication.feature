@CentralServer
@Login
Feature: 0200 - CS: Authentication

  Background:
    Given CentralServer login page is open
    And Page is prepared to be tested

  Scenario: Correct password and username grant access
    Given Login form is visible
    When User xrd logs in to CentralServer with password secret
    Then Members tab is selected

  Scenario: Invalid password is rejected
    When User xrd logs in to CentralServer with password INVALID
    Then Error message for incorrect credentials is shown
    And Login form is visible

  Scenario: Invalid username is rejected
    When User INVALID logs in to CentralServer with password secret
    Then Error message for incorrect credentials is shown
    And Login form is visible
