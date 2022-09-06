@SecurityServer
@Permissions
Feature: Security Server Security Officer permissions

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd-sec logs in to SecurityServer with password secret

  Scenario: Security officer cannot add new clients.
    When Clients tab is selected
    Then Add client button is not visible
    When Client "TestGov" is selected
    Then Client details tab is not visible

  Scenario: Security officer cannot see API keys.
    Given Keys and certificates tab is selected
    And SIGN and AUTH Keys tab is selected
    Then Token name is visible
    And Tab api keys is not visible
    When Security Server TLS Key tab is selected
    Then Generate key button is visible

  Scenario: Security officer cannot do backups
    Given SecurityServer Settings tab is selected
    When System parameters tab is selected
    Then Anchor download button is visible
    And Backup and restore tab is not visible

  Scenario: Security officer cannot see diagnostics tab
    Then Diagnostics tab is "invisible"
