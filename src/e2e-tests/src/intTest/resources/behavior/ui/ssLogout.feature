@SecurityServer
@Logout
Feature: It is possible to login to secure site via frontpage

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret

  Scenario: User is able to log out from security server
    When logout button is being clicked
    Then SecurityServer login page is open

  Scenario: Automatic logout happens when timeout passes
    When 120 seconds of inactivity is passed
    Then Error message about timeout appears
    When OK is clicked on timeout notification popup
    Then  SecurityServer login page is open

