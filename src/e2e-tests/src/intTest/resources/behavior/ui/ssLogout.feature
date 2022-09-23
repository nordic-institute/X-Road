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
    When User becomes idle
    Then after 120 seconds, session timeout popup appears
    When OK is clicked on timeout notification popup
    Then  SecurityServer login page is open

