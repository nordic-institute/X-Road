@SecurityServer
Feature: Configuration backup configuration
  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd-obs logs in to SecurityServer with password secret

  Scenario: Backup encryption is enabled
    When Diagnostics tab is selected
    Then Backup encryption is enabled
    Then Backup encryption configuration has 3 keys
