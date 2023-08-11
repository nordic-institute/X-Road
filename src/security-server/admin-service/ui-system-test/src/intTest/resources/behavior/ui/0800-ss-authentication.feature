@SecurityServer
@Login
Feature: 0800 - SS: Authentication

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested

  Scenario: Invalid password is rejected
    When User xrd logs in to SecurityServer with password INVALID
    Then Error message for incorrect credentials is shown
    And Login form is visible

  Scenario: Invalid username is rejected
    When User INVALID logs in to SecurityServer with password secret
    Then Error message for incorrect credentials is shown
    And Login form is visible

  Scenario: User is able to log out from security server
    Given Login form is visible
    When User xrd logs in to SecurityServer with password secret
    And Clients Tab is present
    When logout button is being clicked
    Then SecurityServer login page is open

  Scenario: Automatic logout happens when timeout passes
    Given Login form is visible
    And User xrd logs in to SecurityServer with password secret
    When User becomes idle
    Then after 120 seconds, session timeout popup appears
    When OK is clicked on timeout notification popup
    Then  SecurityServer login page is open
